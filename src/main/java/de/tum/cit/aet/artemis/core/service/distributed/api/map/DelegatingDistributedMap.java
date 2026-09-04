package de.tum.cit.aet.artemis.core.service.distributed.api.map;

import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import de.tum.cit.aet.artemis.core.service.distributed.api.map.listener.MapEntryListener;
import de.tum.cit.aet.artemis.core.service.distributed.api.map.listener.MapListener;

/**
 * Forwards every operation to another {@link DistributedMap}.
 *
 * <p>
 * Exists so that behaviour shared by all backends can be layered on in one provider-agnostic place instead of being
 * reimplemented three times. Subclasses override only what they change.
 *
 * @param <K> key type
 * @param <V> value type
 */
public abstract class DelegatingDistributedMap<K, V> implements DistributedMap<K, V> {

    protected final DistributedMap<K, V> delegate;

    protected DelegatingDistributedMap(DistributedMap<K, V> delegate) {
        this.delegate = delegate;
    }

    @Override
    public V get(K key) {
        return delegate.get(key);
    }

    @Override
    public Map<K, V> getAll(Set<K> keys) {
        return delegate.getAll(keys);
    }

    @Override
    public void put(K key, V value) {
        delegate.put(key, value);
    }

    @Override
    public void put(K key, V value, Duration timeToLive) {
        delegate.put(key, value, timeToLive);
    }

    @Override
    public V putIfAbsent(K key, V value) {
        return delegate.putIfAbsent(key, value);
    }

    @Override
    public V putIfAbsent(K key, V value, Duration timeToLive) {
        return delegate.putIfAbsent(key, value, timeToLive);
    }

    @Override
    public boolean remove(K key, V value) {
        return delegate.remove(key, value);
    }

    @Override
    public boolean replace(K key, V expectedValue, V replacementValue) {
        return delegate.replace(key, expectedValue, replacementValue);
    }

    @Override
    public boolean refreshTimeToLive(K key, Duration timeToLive) {
        return delegate.refreshTimeToLive(key, timeToLive);
    }

    @Override
    public V remove(K key) {
        return delegate.remove(key);
    }

    @Override
    public Collection<V> values() {
        return delegate.values();
    }

    @Override
    public Set<K> keySet() {
        return delegate.keySet();
    }

    @Override
    public Set<Map.Entry<K, V>> entrySet() {
        return delegate.entrySet();
    }

    @Override
    public Map<K, V> getMapCopy() {
        return delegate.getMapCopy();
    }

    @Override
    public int size() {
        return delegate.size();
    }

    @Override
    public void clear() {
        delegate.clear();
    }

    @Override
    public void lock(K key) {
        delegate.lock(key);
    }

    @Override
    public void lock(K key, Duration lease) {
        delegate.lock(key, lease);
    }

    @Override
    public void unlock(K key) {
        delegate.unlock(key);
    }

    @Override
    public UUID addEntryListener(MapEntryListener<K, V> listener) {
        return delegate.addEntryListener(listener);
    }

    @Override
    public UUID addListener(MapListener listener) {
        return delegate.addListener(listener);
    }

    @Override
    public void removeListener(UUID registrationId) {
        delegate.removeListener(registrationId);
    }
}
