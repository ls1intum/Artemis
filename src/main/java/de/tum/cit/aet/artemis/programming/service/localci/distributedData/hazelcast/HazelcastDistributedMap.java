package de.tum.cit.aet.artemis.programming.service.localci.distributedData.hazelcast;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.hazelcast.map.IMap;
import com.hazelcast.map.listener.MapListener;

import de.tum.cit.aet.artemis.programming.service.localci.distributedData.api.DistributedMap;

public class HazelcastDistributedMap<K, V> implements DistributedMap<K, V> {

    private final IMap<K, V> map;

    public HazelcastDistributedMap(IMap<K, V> map) {
        this.map = map;
    }

    public V get(K key) {
        return map.get(key);
    }

    @Override
    public Map<K, V> getAll(Set<K> keys) {
        return map.getAll(keys);
    }

    @Override
    public void put(K key, V value) {
        map.put(key, value);
    }

    @Override
    public V remove(K key) {
        return map.remove(key);
    }

    @Override
    public Collection<V> values() {
        return map.values();
    }

    @Override
    public Set<K> keySet() {
        return map.keySet();
    }

    @Override
    public Set<Map.Entry<K, V>> entrySet() {
        return map.entrySet();
    }

    @Override
    public HashMap<K, V> getMapCopy() {
        return new HashMap<>(map);
    }

    @Override
    public int size() {
        return map.size();
    }

    @Override
    public void clear() {
        map.clear();
    }

    @Override
    public void lock(K key) {
        map.lock(key);
    }

    @Override
    public void unlock(K key) {
        map.unlock(key);
    }

    @Override
    public UUID addEntryListener(MapListener listener, boolean includeValue) {
        return map.addEntryListener(listener, includeValue);
    }
}
