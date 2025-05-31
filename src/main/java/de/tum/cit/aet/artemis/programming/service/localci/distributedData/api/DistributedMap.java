package de.tum.cit.aet.artemis.programming.service.localci.distributedData.api;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.hazelcast.map.listener.MapListener;

public interface DistributedMap<K, V> {

    V get(K key);

    Map<K, V> getAll(Set<K> keys);

    void put(K key, V value);

    V remove(K key);

    Collection<V> values();

    Set<K> keySet();

    Set<Map.Entry<K, V>> entrySet();

    HashMap<K, V> getMapCopy();

    int size();

    void clear();

    void lock(K key);

    void unlock(K key);

    UUID addEntryListener(MapListener listener, boolean includeValue);
}
