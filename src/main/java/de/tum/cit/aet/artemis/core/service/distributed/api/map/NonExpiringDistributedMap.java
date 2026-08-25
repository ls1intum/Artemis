package de.tum.cit.aet.artemis.core.service.distributed.api.map;

import java.time.Duration;

/**
 * Rejects per-entry time-to-live on a map that was not requested as an expiring map.
 *
 * <p>
 * Backends differ in what they could technically do here: Hazelcast and the local provider accept a per-entry TTL on any
 * map, while Redisson's plain {@code RMap} has no notion of expiry at all. Allowing the call wherever a backend happens
 * to support it would mean code that works on Hazelcast silently stops expiring entries once the same deployment runs on
 * Redis. Failing uniformly instead forces callers to state the requirement by asking for
 * {@link de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider#getExpiringMap(String, Duration)}.
 *
 * @param <K> key type
 * @param <V> value type
 */
public class NonExpiringDistributedMap<K, V> extends DelegatingDistributedMap<K, V> {

    private final String name;

    public NonExpiringDistributedMap(DistributedMap<K, V> delegate, String name) {
        super(delegate);
        this.name = name;
    }

    @Override
    public V putIfAbsent(K key, V value, Duration timeToLive) {
        throw rejectExpiry();
    }

    @Override
    public void put(K key, V value, Duration timeToLive) {
        throw rejectExpiry();
    }

    private UnsupportedOperationException rejectExpiry() {
        return new UnsupportedOperationException(
                "Map '" + name + "' was not created as an expiring map, so entries would never expire. Obtain it via DistributedDataProvider.getExpiringMap(name, ttl).");
    }
}
