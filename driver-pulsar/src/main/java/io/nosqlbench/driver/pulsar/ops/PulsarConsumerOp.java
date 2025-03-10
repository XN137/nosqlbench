package io.nosqlbench.driver.pulsar.ops;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.function.Supplier;

import org.apache.commons.lang3.StringUtils;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Timer;
import io.nosqlbench.driver.pulsar.PulsarActivity;
import io.nosqlbench.driver.pulsar.exception.PulsarDriverUnexpectedException;
import io.nosqlbench.driver.pulsar.util.AvroUtil;
import io.nosqlbench.driver.pulsar.util.PulsarActivityUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.pulsar.client.api.Consumer;
import org.apache.pulsar.client.api.Message;
import org.apache.pulsar.client.api.Schema;
import org.apache.pulsar.client.api.transaction.Transaction;
import org.apache.pulsar.common.schema.SchemaType;

public class PulsarConsumerOp implements PulsarOp {

    private final static Logger logger = LogManager.getLogger(PulsarConsumerOp.class);

    private final PulsarActivity pulsarActivity;

    private final boolean asyncPulsarOp;
    private final boolean useTransaction;
    private final boolean seqTracking;
    private final Supplier<Transaction> transactionSupplier;

    private final Consumer<?> consumer;
    private final Schema<?> pulsarSchema;
    private final int timeoutSeconds;
    private final boolean e2eMsgProc;

    private final Counter bytesCounter;
    private final Histogram messageSizeHistogram;
    private final Timer transactionCommitTimer;

    // keep track of end-to-end message latency
    private final Histogram e2eMsgProcLatencyHistogram;

    private final Function<String, ReceivedMessageSequenceTracker> receivedMessageSequenceTrackerForTopic;
    private final Histogram payloadRttHistogram;
    private final String payloadRttTrackingField;

    public PulsarConsumerOp(
        PulsarActivity pulsarActivity,
        boolean asyncPulsarOp,
        boolean useTransaction,
        boolean seqTracking,
        Supplier<Transaction> transactionSupplier,
        Consumer<?> consumer,
        Schema<?> schema,
        int timeoutSeconds,
        boolean e2eMsgProc,
        Function<String, ReceivedMessageSequenceTracker> receivedMessageSequenceTrackerForTopic,
        String payloadRttTrackingField)
    {
        this.pulsarActivity = pulsarActivity;

        this.asyncPulsarOp = asyncPulsarOp;
        this.useTransaction = useTransaction;
        this.seqTracking = seqTracking;
        this.transactionSupplier = transactionSupplier;

        this.consumer = consumer;
        this.pulsarSchema = schema;
        this.timeoutSeconds = timeoutSeconds;
        this.e2eMsgProc = e2eMsgProc;

        this.bytesCounter = pulsarActivity.getBytesCounter();
        this.messageSizeHistogram = pulsarActivity.getMessageSizeHistogram();
        this.transactionCommitTimer = pulsarActivity.getCommitTransactionTimer();

        this.e2eMsgProcLatencyHistogram = pulsarActivity.getE2eMsgProcLatencyHistogram();
        this.payloadRttHistogram = pulsarActivity.getPayloadRttHistogram();
        this.receivedMessageSequenceTrackerForTopic = receivedMessageSequenceTrackerForTopic;
        this.payloadRttTrackingField = payloadRttTrackingField;
    }

    private void checkAndUpdateMessageErrorCounter(Message message) {
        String msgSeqIdStr = message.getProperty(PulsarActivityUtil.MSG_SEQUENCE_NUMBER);

        if ( !StringUtils.isBlank(msgSeqIdStr) ) {
            long sequenceNumber = Long.parseLong(msgSeqIdStr);
            ReceivedMessageSequenceTracker receivedMessageSequenceTracker = receivedMessageSequenceTrackerForTopic.apply(message.getTopicName());
            receivedMessageSequenceTracker.sequenceNumberReceived(sequenceNumber);
        }
    }

    @Override
    public void run(Runnable timeTracker) {

        final Transaction transaction;
        if (useTransaction) {
            // if you are in a transaction you cannot set the schema per-message
            transaction = transactionSupplier.get();
        }
        else {
            transaction = null;
        }

        if (!asyncPulsarOp) {
            Message<?> message;

            try {
                if (timeoutSeconds <= 0) {
                    // wait forever
                    message = consumer.receive();
                }
                else {
                    message = consumer
                        .receive(timeoutSeconds, TimeUnit.SECONDS);
                    if (message == null) {
                        throw new TimeoutException("Did not receive a message within "+timeoutSeconds+" seconds");
                    }
                }

                if (logger.isDebugEnabled()) {
                    SchemaType schemaType = pulsarSchema.getSchemaInfo().getType();

                    if (PulsarActivityUtil.isAvroSchemaTypeStr(schemaType.name())) {
                        String avroDefStr = pulsarSchema.getSchemaInfo().getSchemaDefinition();
                        org.apache.avro.Schema avroSchema =
                            AvroUtil.GetSchema_ApacheAvro(avroDefStr);
                        org.apache.avro.generic.GenericRecord avroGenericRecord =
                            AvroUtil.GetGenericRecord_ApacheAvro(avroSchema, message.getData());

                        logger.debug("({}) Sync message received: msg-key={}; msg-properties={}; msg-payload={}",
                            consumer.getConsumerName(),
                            message.getKey(),
                            message.getProperties(),
                            avroGenericRecord.toString());
                    }
                    else {
                        logger.debug("({}) Sync message received: msg-key={}; msg-properties={}; msg-payload={}",
                            consumer.getConsumerName(),
                            message.getKey(),
                            message.getProperties(),
                            new String(message.getData()));
                    }
                }

                if (!payloadRttTrackingField.isEmpty()) {
                    String avroDefStr = pulsarSchema.getSchemaInfo().getSchemaDefinition();
                    org.apache.avro.Schema avroSchema =
                            AvroUtil.GetSchema_ApacheAvro(avroDefStr);
                    org.apache.avro.generic.GenericRecord avroGenericRecord =
                            AvroUtil.GetGenericRecord_ApacheAvro(avroSchema, message.getData());
                    if (avroGenericRecord.hasField(payloadRttTrackingField)) {
                        long extractedSendTime = (Long)avroGenericRecord.get(payloadRttTrackingField);
                        long delta = System.currentTimeMillis() - extractedSendTime;
                        payloadRttHistogram.update(delta);
                    }
                }

                // keep track end-to-end message processing latency
                if (e2eMsgProc) {
                    long e2eMsgLatency = System.currentTimeMillis() - message.getPublishTime();
                    e2eMsgProcLatencyHistogram.update(e2eMsgLatency);
                }

                // keep track of message errors and update error counters
                if (seqTracking) checkAndUpdateMessageErrorCounter(message);

                int messageSize = message.getData().length;
                bytesCounter.inc(messageSize);
                messageSizeHistogram.update(messageSize);

                if (!useTransaction) {
                    consumer.acknowledge(message.getMessageId());
                }
                else {
                    consumer.acknowledgeAsync(message.getMessageId(), transaction).get();

                    // little problem: here we are counting the "commit" time
                    // inside the overall time spent for the execution of the consume operation
                    // we should refactor this operation as for PulsarProducerOp, and use the passed callback
                    // to track with precision the time spent for the operation and for the commit
                    try (Timer.Context ctx = transactionCommitTimer.time()) {
                        transaction.commit().get();
                    }
                }

            }
            catch (Exception e) {
                logger.error(
                    "Sync message receiving failed - timeout value: {} seconds ", timeoutSeconds);
                e.printStackTrace();
                throw new PulsarDriverUnexpectedException("" +
                    "Sync message receiving failed - timeout value: " + timeoutSeconds + " seconds ");
            }
        }
        else {
            try {
                CompletableFuture<? extends Message<?>> msgRecvFuture = consumer.receiveAsync();
                if (useTransaction) {
                    // add commit step
                    msgRecvFuture = msgRecvFuture.thenCompose(msg -> {
                            Timer.Context ctx = transactionCommitTimer.time();
                            return transaction
                                .commit()
                                .whenComplete((m,e) -> ctx.close())
                                .thenApply(v-> msg);
                        }
                    );
                }

                msgRecvFuture.whenComplete((message, error) -> {
                    int messageSize = message.getData().length;
                    bytesCounter.inc(messageSize);
                    messageSizeHistogram.update(messageSize);

                    if (logger.isDebugEnabled()) {
                        SchemaType schemaType = pulsarSchema.getSchemaInfo().getType();

                        if (PulsarActivityUtil.isAvroSchemaTypeStr(schemaType.name())) {
                            String avroDefStr = pulsarSchema.getSchemaInfo().getSchemaDefinition();
                            org.apache.avro.Schema avroSchema =
                                AvroUtil.GetSchema_ApacheAvro(avroDefStr);
                            org.apache.avro.generic.GenericRecord avroGenericRecord =
                                AvroUtil.GetGenericRecord_ApacheAvro(avroSchema, message.getData());

                            logger.debug("({}) Async message received: msg-key={}; msg-properties={}; msg-payload={})",
                                consumer.getConsumerName(),
                                message.getKey(),
                                message.getProperties(),
                                avroGenericRecord.toString());
                        }
                        else {
                            logger.debug("({}) Async message received: msg-key={}; msg-properties={}; msg-payload={})",
                                consumer.getConsumerName(),
                                message.getKey(),
                                message.getProperties(),
                                new String(message.getData()));
                        }
                    }

                    if (e2eMsgProc) {
                        long e2eMsgLatency = System.currentTimeMillis() - message.getPublishTime();
                        e2eMsgProcLatencyHistogram.update(e2eMsgLatency);
                    }

                    // keep track of message errors and update error counters
                    if (seqTracking) checkAndUpdateMessageErrorCounter(message);

                    if (!useTransaction) {
                        consumer.acknowledgeAsync(message);
                    }
                    else {
                        consumer.acknowledgeAsync(message.getMessageId(), transaction);
                    }

                    timeTracker.run();
                }).exceptionally(ex -> {
                    pulsarActivity.asyncOperationFailed(ex);
                    return null;
                });
            }
            catch (Exception e) {
                throw new PulsarDriverUnexpectedException(e);
            }
        }
    }

}
