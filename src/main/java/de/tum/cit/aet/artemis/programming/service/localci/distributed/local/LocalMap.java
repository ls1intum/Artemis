package de.tum.cit.aet.artemis.programming.service.localci.distributed.local;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

import de.tum.cit.aet.artemis.programming.service.localci.distributed.api.map.DistributedMap;
import de.tum.cit.aet.artemis.programming.service.localci.distributed.api.map.listener.MapEntryAddedEvent;
import de.tum.cit.aet.artemis.programming.service.localci.distributed.api.map.listener.MapEntryListener;
import de.tum.cit.aet.artemis.programming.service.localci.distributed.api.map.listener.MapEntryRemovedEvent;
import de.tum.cit.aet.artemis.programming.service.localci.distributed.api.map.listener.MapEntryUpdatedEvent;
import de.tum.cit.aet.artemis.programming.service.localci.distributed.api.map.listener.MapListener;

public class LocalMap<K, V> implements DistributedMap<K, V> {

    private final ConcurrentHashMap<K, V> map;

    private final ConcurrentHashMap<K, ReentrantLock> locks = new ConcurrentHashMap<>();

    // Listeners that require the specific changed entries on add/update/remove
    private final ConcurrentHashMap<UUID, MapEntryListener<K, V>> entryListeners = new ConcurrentHashMap<>();

    // Simplified listeners that do not require changed entry but just notification
    private final ConcurrentHashMap<UUID, MapListener> mapListeners = new ConcurrentHashMap<>();

    public LocalMap() {
        this.map = new ConcurrentHashMap<K, V>();
    }

    private ReentrantLock getLock(K key) {
        return locks.computeIfAbsent(key, k -> new ReentrantLock());
    }

    @Override
    public V get(K key) {
        ReentrantLock lock = getLock(key);
        lock.lock();
        try {
            return map.get(key);
        }
        finally {
            lock.unlock();
        }
    }

    @Override
    public Map<K, V> getAll(Set<K> keys) {
        Map<K, V> result = new HashMap<>();
        for (K key : keys) {
            result.put(key, get(key));
        }
        return result;
    }

    @Override
    public void put(K key, V value) {
        ReentrantLock lock = getLock(key);
        lock.lock();
        try {
            V oldValue = map.put(key, value);
            if (oldValue != null) {
                notifyEntryUpdated(key, value, oldValue);
            }
            else {
                notifyEntryAdded(key, value);
            }
        }
        finally {
            lock.unlock();
        }
    }

    @Override
    public V remove(K key) {
        ReentrantLock lock = getLock(key);
        lock.lock();
        try {
            V oldValue = map.remove(key);
            if (oldValue != null) {
                notifyEntryRemoved(key, oldValue);
            }
            return oldValue;
        }
        finally {
            lock.unlock();
        }
    }

    @Override
    public Collection<V> values() {
        // Not locking all keys for values() to avoid deadlocks; this is a snapshot
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
        for (K key : map.keySet()) {
            getLock(key).lock();
        }
        try {
            map.clear();
        }
        finally {
            for (K key : map.keySet()) {
                getLock(key).unlock();
            }
        }
    }

    @Override
    public void lock(K key) {
        getLock(key).lock();
    }

    @Override
    public void unlock(K key) {
        ReentrantLock lock = locks.get(key);
        if (lock != null) {
            lock.unlock();
            if (!lock.isLocked()) {
                locks.remove(key, lock);
            }
        }
    }

    @Override
    public UUID addEntryListener(MapEntryListener<K, V> listener) {
        UUID id = UUID.randomUUID();
        entryListeners.put(id, listener);
        return id;
    }

    @Override
    public UUID addListener(MapListener listener) {
        UUID id = UUID.randomUUID();
        mapListeners.put(id, listener);
        return id;
    }

    private void notifyEntryAdded(K key, V value) {
        for (MapEntryListener<K, V> listener : entryListeners.values()) {
            listener.entryAdded(new MapEntryAddedEvent<>(key, value));
        }
        for (MapListener listener : mapListeners.values()) {
            listener.entryAdded();
        }
    }

    private void notifyEntryUpdated(K key, V newValue, V oldValue) {
        for (MapEntryListener<K, V> listener : entryListeners.values()) {
            listener.entryUpdated(new MapEntryUpdatedEvent<>(key, newValue, oldValue));
        }
        for (MapListener listener : mapListeners.values()) {
            listener.entryUpdated();
        }
    }

    private void notifyEntryRemoved(K key, V oldValue) {
        for (MapEntryListener<K, V> listener : entryListeners.values()) {
            listener.entryRemoved(new MapEntryRemovedEvent<>(key, oldValue));
        }
        for (MapListener listener : mapListeners.values()) {
            listener.entryRemoved();
        }
    }
}
