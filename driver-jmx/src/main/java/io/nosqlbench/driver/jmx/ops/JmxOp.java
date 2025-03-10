package io.nosqlbench.driver.jmx.ops;

import io.nosqlbench.engine.api.activityimpl.uniform.flowtypes.Op;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;

/**
 * All JMX Operations should built on this base type.
 */
public abstract class JmxOp implements Op,Runnable {

    protected final static Logger logger = LogManager.getLogger(JmxOp.class);

    protected JMXConnector connector;
    protected ObjectName objectName;

    public JmxOp(JMXConnector connector, ObjectName objectName) {
        this.connector = connector;
        this.objectName = objectName;
    }

    public MBeanServerConnection getMBeanConnection() {
        MBeanServerConnection connection = null;
        try {
            connection = connector.getMBeanServerConnection();
            return connection;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    protected Object readObject(String attributeName) {
        try {
            Object value = getMBeanConnection().getAttribute(objectName, attributeName);
            logger.trace("read attribute '" + value + "': " + value);
            return value;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public abstract void execute();

    public void run() {
        execute();
    }
}
