package io.nosqlbench.driver.pulsar;

import java.util.concurrent.ConcurrentHashMap;

/**
 * To enable flexibility in testing methods, each object graph which is used within
 * the pulsar API is kept within a single umbrella called the PulsarSpace.
 * This allows for clients, producers, and consumers to remain connected and
 * cached in a useful way.
 */
public class PulsarSpaceCache {

    // TODO: Implement cache limits
    // TODO: Implement variant cache eviction behaviors (halt, warn, LRU)

    private final PulsarActivity activity;
    private final ConcurrentHashMap<String, PulsarSpace> clientScopes = new ConcurrentHashMap<>();

    public PulsarSpaceCache(PulsarActivity pulsarActivity) {
        this.activity = pulsarActivity;
    }

    public Iterable<PulsarSpace> getAssociatedPulsarSpace() {
        return clientScopes.values();
    }

    public PulsarActivity getAssociatedPulsarActivity() {
        return activity;
    }

    public PulsarSpace getPulsarSpace(String name) {
        return clientScopes.computeIfAbsent(name, spaceName -> new PulsarSpace(spaceName, activity));
    }

    public PulsarActivity getActivity() { return activity; }
}
