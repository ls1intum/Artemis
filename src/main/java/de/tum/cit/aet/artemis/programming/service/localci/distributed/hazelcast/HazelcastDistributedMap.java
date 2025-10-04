package de.tum.cit.aet.artemis.programming.service.localci.distributed.hazelcast;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.hazelcast.core.EntryEvent;
import com.hazelcast.map.IMap;
import com.hazelcast.map.listener.EntryAddedListener;
import com.hazelcast.map.listener.EntryRemovedListener;
import com.hazelcast.map.listener.EntryUpdatedListener;

import de.tum.cit.aet.artemis.programming.service.localci.distributed.api.map.DistributedMap;
import de.tum.cit.aet.artemis.programming.service.localci.distributed.api.map.listener.MapEntryAddedEvent;
import de.tum.cit.aet.artemis.programming.service.localci.distributed.api.map.listener.MapEntryListener;
import de.tum.cit.aet.artemis.programming.service.localci.distributed.api.map.listener.MapEntryRemovedEvent;
import de.tum.cit.aet.artemis.programming.service.localci.distributed.api.map.listener.MapEntryUpdatedEvent;
import de.tum.cit.aet.artemis.programming.service.localci.distributed.api.map.listener.MapListener;

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
    public UUID addEntryListener(MapEntryListener<K, V> listener) {
        class HazelcastMapEntryListener implements EntryAddedListener<K, V>, EntryRemovedListener<K, V>, EntryUpdatedListener<K, V> {

            @Override
            public void entryAdded(EntryEvent<K, V> event) {
                listener.entryAdded(new MapEntryAddedEvent<>(event.getKey(), event.getValue()));
            }

            @Override
            public void entryRemoved(EntryEvent<K, V> event) {
                listener.entryRemoved(new MapEntryRemovedEvent<>(event.getKey(), event.getOldValue()));
            }

            @Override
            public void entryUpdated(EntryEvent<K, V> event) {
                listener.entryUpdated(new MapEntryUpdatedEvent<>(event.getKey(), event.getValue(), event.getOldValue()));
            }
        }
        return map.addEntryListener(new HazelcastMapEntryListener(), true);
    }

    @Override
    public UUID addListener(MapListener listener) {
        class HazelcastMapEntryListener implements EntryAddedListener<K, V>, EntryRemovedListener<K, V>, EntryUpdatedListener<K, V> {

            @Override
            public void entryAdded(EntryEvent<K, V> event) {
                listener.entryAdded();
            }

            @Override
            public void entryRemoved(EntryEvent<K, V> event) {
                listener.entryRemoved();
            }

            @Override
            public void entryUpdated(EntryEvent<K, V> event) {
                listener.entryUpdated();
            }
        }
        return map.addEntryListener(new HazelcastMapEntryListener(), false);
    }

    @Override
    public void removeListener(UUID registrationId) {
        map.removeEntryListener(registrationId);
    }
}
