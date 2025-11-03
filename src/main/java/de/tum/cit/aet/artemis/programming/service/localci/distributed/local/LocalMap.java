package de.tum.cit.aet.artemis.programming.service.localci.distributed.local;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

import de.tum.cit.aet.artemis.programming.service.localci.distributed.api.map.DistributedMap;
import de.tum.cit.aet.artemis.programming.service.localci.distributed.api.map.listener.MapEntryAddedEvent;
import de.tum.cit.aet.artemis.programming.service.localci.distributed.api.map.listener.MapEntryListener;
import de.tum.cit.aet.artemis.programming.service.localci.distributed.api.map.listener.MapEntryRemovedEvent;
import de.tum.cit.aet.artemis.programming.service.localci.distributed.api.map.listener.MapEntryUpdatedEvent;
import de.tum.cit.aet.artemis.programming.service.localci.distributed.api.map.listener.MapListener;

public class LocalMap<K, V> implements DistributedMap<K, V> {

    private static final Logger log = LoggerFactory.getLogger(LocalMap.class);

    private final ConcurrentHashMap<K, V> map;

    private final ConcurrentHashMap<K, ReentrantLock> locks = new ConcurrentHashMap<>();

    // Listeners that require the specific changed entries on add/update/remove
    private final ConcurrentHashMap<UUID, MapEntryListener<K, V>> entryListeners = new ConcurrentHashMap<>();

    // Simplified listeners that do not require changed entry but just notification
    private final ConcurrentHashMap<UUID, MapListener> mapListeners = new ConcurrentHashMap<>();

    private final ExecutorService notificationExecutor;

    public LocalMap() {
        this(Executors.newCachedThreadPool(new ThreadFactoryBuilder().setNameFormat("local-map-listener-%d").setDaemon(true).build()));
    }

    public LocalMap(ExecutorService notificationExecutor) {
        this.notificationExecutor = notificationExecutor;
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
        V oldValue;
        boolean isUpdate;

        lock.lock();
        try {
            oldValue = map.put(key, value);
            isUpdate = oldValue != null;
        }
        finally {
            lock.unlock();
        }

        if (isUpdate) {
            notifyEntryUpdated(key, value, oldValue);
        }
        else {
            notifyEntryAdded(key, value);
        }
    }

    @Override
    public V remove(K key) {
        ReentrantLock lock = getLock(key);
        V oldValue;

        lock.lock();
        try {
            oldValue = map.remove(key);
        }
        finally {
            lock.unlock();
        }

        if (oldValue != null) {
            notifyEntryRemoved(key, oldValue);
        }

        return oldValue;
    }

    @Override
    public Collection<V> values() {
        // Return a snapshot
        return new java.util.ArrayList<>(map.values());
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
        Set<K> keysCopy = Set.copyOf(map.keySet());
        Map<K, V> entriesCopy = new HashMap<>(map);
        try {
            for (K key : keysCopy) {
                getLock(key).lock();
            }
            map.clear();
        }
        finally {
            for (K key : keysCopy) {
                getLock(key).unlock();
            }
        }

        for (Map.Entry<K, V> entry : entriesCopy.entrySet()) {
            final K key = entry.getKey();
            final V value = entry.getValue();
            notifyEntryRemoved(key, value);
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

    @Override
    public void removeListener(UUID registrationId) {
        entryListeners.remove(registrationId);
        mapListeners.remove(registrationId);
    }

    private void notifyEntryAdded(K key, V value) {
        notificationExecutor.execute(() -> {
            try {
                for (MapEntryListener<K, V> listener : entryListeners.values()) {
                    try {
                        listener.entryAdded(new MapEntryAddedEvent<>(key, value));
                    }
                    catch (Exception e) {
                        log.error("Error in map entry listener", e);
                    }
                }
                for (MapListener listener : mapListeners.values()) {
                    try {
                        listener.entryAdded();
                    }
                    catch (Exception e) {
                        log.error("Error in map listener", e);
                    }
                }
            }
            catch (Exception e) {
                log.error("Error notifying listeners", e);
            }
        });
    }

    private void notifyEntryUpdated(K key, V newValue, V oldValue) {
        notificationExecutor.execute(() -> {
            try {
                for (MapEntryListener<K, V> listener : entryListeners.values()) {
                    try {
                        listener.entryUpdated(new MapEntryUpdatedEvent<>(key, newValue, oldValue));
                    }
                    catch (Exception e) {
                        log.error("Error in map entry listener", e);
                    }
                }
                for (MapListener listener : mapListeners.values()) {
                    try {
                        listener.entryUpdated();
                    }
                    catch (Exception e) {
                        log.error("Error in map listener", e);
                    }
                }
            }
            catch (Exception e) {
                log.error("Error notifying listeners", e);
            }
        });
    }

    private void notifyEntryRemoved(K key, V oldValue) {
        notificationExecutor.execute(() -> {
            try {
                for (MapEntryListener<K, V> listener : entryListeners.values()) {
                    try {
                        listener.entryRemoved(new MapEntryRemovedEvent<>(key, oldValue));
                    }
                    catch (Exception e) {
                        log.error("Error in map entry listener", e);
                    }
                }
                for (MapListener listener : mapListeners.values()) {
                    try {
                        listener.entryRemoved();
                    }
                    catch (Exception e) {
                        log.error("Error in map listener", e);
                    }
                }
            }
            catch (Exception e) {
                log.error("Error notifying listeners", e);
            }
        });
    }
}
