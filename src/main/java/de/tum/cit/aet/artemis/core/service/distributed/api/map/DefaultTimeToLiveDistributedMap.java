package de.tum.cit.aet.artemis.core.service.distributed.api.map;

import java.time.Duration;

/**
 * Applies a map-wide default time-to-live to every {@link #put(Object, Object)}.
 *
 * <p>
 * Deliberately provider-agnostic: each backend only implements per-entry TTL
 * ({@link DistributedMap#put(Object, Object, Duration)}), and the map-level default is layered on here. That keeps the
 * three backends from each reinventing the default, and it sidesteps Hazelcast's constraint that a map-level TTL comes
 * from a {@code MapConfig} which must exist before the proxy is created and cannot be registered from a client at all.
 *
 * @param <K> key type
 * @param <V> value type
 */
public class DefaultTimeToLiveDistributedMap<K, V> extends DelegatingDistributedMap<K, V> {

    private final Duration defaultTimeToLive;

    public DefaultTimeToLiveDistributedMap(DistributedMap<K, V> delegate, Duration defaultTimeToLive) {
        super(delegate);
        // Validated here rather than on the first write: a null default fails with a confusing NullPointerException far
        // from the misconfiguration, and zero or negative durations mean "never expires" on the backends, silently
        // turning an expiring map into a permanent one.
        if (defaultTimeToLive == null || defaultTimeToLive.isZero() || defaultTimeToLive.isNegative()) {
            throw new IllegalArgumentException("The default time-to-live of an expiring map must be positive, but was " + defaultTimeToLive);
        }
        this.defaultTimeToLive = defaultTimeToLive;
    }

    @Override
    public void put(K key, V value) {
        delegate.put(key, value, defaultTimeToLive);
    }

    @Override
    public V putIfAbsent(K key, V value) {
        return delegate.putIfAbsent(key, value, defaultTimeToLive);
    }
}
