package de.tum.cit.aet.artemis.core.service.distributed.local;

import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.tum.cit.aet.artemis.core.service.distributed.api.map.DistributedMap;
import de.tum.cit.aet.artemis.core.service.distributed.api.map.listener.MapEntryAddedEvent;
import de.tum.cit.aet.artemis.core.service.distributed.api.map.listener.MapEntryListener;
import de.tum.cit.aet.artemis.core.service.distributed.api.map.listener.MapEntryRemovedEvent;
import de.tum.cit.aet.artemis.core.service.distributed.api.map.listener.MapEntryUpdatedEvent;
import de.tum.cit.aet.artemis.core.service.distributed.api.map.listener.MapListener;

public class LocalMap<K, V> implements DistributedMap<K, V> {

    private static final Logger log = LoggerFactory.getLogger(LocalMap.class);

    private final ConcurrentHashMap<K, V> map;

    private final LocalLockRegistry<K> locks = new LocalLockRegistry<>();

    /**
     * Keyed operations check their own deadline under the key lock. Bulk reads and the provider's periodic cleanup
     * also discard entries that nobody has accessed since they expired.
     */
    private final ConcurrentHashMap<K, Long> expiryDeadlines = new ConcurrentHashMap<>();

    // Listeners that require the specific changed entries on add/update/remove
    private final ConcurrentHashMap<UUID, MapEntryListener<K, V>> entryListeners = new ConcurrentHashMap<>();

    // Simplified listeners that do not require changed entry but just notification
    private final ConcurrentHashMap<UUID, MapListener> mapListeners = new ConcurrentHashMap<>();

    private final ExecutorService notificationExecutor;

    public LocalMap() {
        this(Executors.newCachedThreadPool(BasicThreadFactory.builder().namingPattern("local-map-listener-%d").daemon().build()));
    }

    public LocalMap(ExecutorService notificationExecutor) {
        this.notificationExecutor = notificationExecutor;
        this.map = new ConcurrentHashMap<>();
    }

    /** Releases expired values, including entries whose key is never accessed again. */
    void purgeExpiredEntries() {
        long now = System.nanoTime();
        expiryDeadlines.forEach((key, deadline) -> {
            if (deadline - now <= 0) {
                lock(key);
                try {
                    expireEntryIfDue(key);
                }
                finally {
                    unlock(key);
                }
            }
        });
    }

    /** Called with the key locked, so a concurrent renewal cannot lose its value. */
    private void expireEntryIfDue(K key) {
        Long deadline = expiryDeadlines.get(key);
        if (deadline != null && deadline - System.nanoTime() <= 0) {
            expiryDeadlines.remove(key);
            V expired = map.remove(key);
            if (expired != null) {
                notifyEntryRemoved(key, expired);
            }
        }
    }

    /**
     * Records or clears the lifetime of an entry. Always called while holding that key's lock, so the value and its
     * deadline move together: a reader or the purge never sees a value whose deadline belongs to a previous write.
     *
     * @param key      the entry the lifetime belongs to
     * @param deadline the nano-time the entry expires at, or null to store it without a lifetime
     */
    private void writeDeadline(K key, Long deadline) {
        if (deadline == null) {
            expiryDeadlines.remove(key);
        }
        else {
            expiryDeadlines.put(key, deadline);
        }
    }

    @Override
    public V get(K key) {
        lock(key);
        try {
            expireEntryIfDue(key);
            return map.get(key);
        }
        finally {
            unlock(key);
        }
    }

    @Override
    public Map<K, V> getAll(Set<K> keys) {
        Map<K, V> result = new HashMap<>();
        for (K key : keys) {
            V value = get(key);
            // Absent keys are left out rather than mapped to null, matching Redisson's
            // RMap.getAll. Mapping them to null would make result.size() and containsKey() disagree between providers.
            if (value != null) {
                result.put(key, value);
            }
        }
        return result;
    }

    @Override
    public void put(K key, V value, Duration timeToLive) {
        putInternal(key, value, System.nanoTime() + timeToLive.toNanos());
    }

    @Override
    public void put(K key, V value) {
        // A plain put replaces any previously configured lifetime for this key.
        putInternal(key, value, null);
    }

    /**
     * @param key      the entry to write
     * @param value    the value to store
     * @param deadline the nano-time the entry expires at, or null to store it without a lifetime
     */
    private void putInternal(K key, V value, Long deadline) {
        V oldValue;
        boolean isUpdate;

        lock(key);
        try {
            expireEntryIfDue(key);
            oldValue = map.put(key, value);
            isUpdate = oldValue != null;
            writeDeadline(key, deadline);
        }
        finally {
            unlock(key);
        }

        if (isUpdate) {
            notifyEntryUpdated(key, value, oldValue);
        }
        else {
            notifyEntryAdded(key, value);
        }
    }

    @Override
    public V putIfAbsent(K key, V value) {
        return putIfAbsentInternal(key, value, null);
    }

    @Override
    public V putIfAbsent(K key, V value, Duration timeToLive) {
        return putIfAbsentInternal(key, value, System.nanoTime() + timeToLive.toNanos());
    }

    /**
     * @param key      the entry to write if it is absent
     * @param value    the value to store
     * @param deadline the nano-time the entry expires at, or null to store it without a lifetime
     * @return the value that was already there, or null if this call stored the value
     */
    private V putIfAbsentInternal(K key, V value, Long deadline) {
        V existing;
        lock(key);
        try {
            expireEntryIfDue(key);
            existing = map.putIfAbsent(key, value);
            if (existing == null) {
                // Only the caller that actually stored the value may set or drop the lifetime.
                writeDeadline(key, deadline);
            }
        }
        finally {
            unlock(key);
        }
        if (existing == null) {
            notifyEntryAdded(key, value);
        }
        return existing;
    }

    @Override
    public boolean remove(K key, V value) {
        boolean removed;
        lock(key);
        try {
            expireEntryIfDue(key);
            removed = map.remove(key, value);
            if (removed) {
                expiryDeadlines.remove(key);
            }
        }
        finally {
            unlock(key);
        }
        if (removed) {
            notifyEntryRemoved(key, value);
        }
        return removed;
    }

    @Override
    public boolean replace(K key, V expectedValue, V replacementValue) {
        boolean replaced;
        lock(key);
        try {
            expireEntryIfDue(key);
            replaced = map.replace(key, expectedValue, replacementValue);
        }
        finally {
            unlock(key);
        }
        if (replaced) {
            notifyEntryUpdated(key, replacementValue, expectedValue);
        }
        return replaced;
    }

    @Override
    public boolean refreshTimeToLive(K key, Duration timeToLive) {
        lock(key);
        try {
            expireEntryIfDue(key);
            if (!map.containsKey(key)) {
                return false;
            }
            writeDeadline(key, System.nanoTime() + timeToLive.toNanos());
            return true;
        }
        finally {
            unlock(key);
        }
    }

    @Override
    public V remove(K key) {
        V oldValue;

        lock(key);
        try {
            expireEntryIfDue(key);
            oldValue = map.remove(key);
            expiryDeadlines.remove(key);
        }
        finally {
            unlock(key);
        }

        if (oldValue != null) {
            notifyEntryRemoved(key, oldValue);
        }

        return oldValue;
    }

    @Override
    public Collection<V> values() {
        purgeExpiredEntries();
        // Return a snapshot
        return new java.util.ArrayList<>(map.values());
    }

    @Override
    public Set<K> keySet() {
        purgeExpiredEntries();
        return map.keySet();
    }

    @Override
    public Set<Map.Entry<K, V>> entrySet() {
        purgeExpiredEntries();
        return map.entrySet();
    }

    @Override
    public HashMap<K, V> getMapCopy() {
        purgeExpiredEntries();
        return new HashMap<>(map);
    }

    @Override
    public int size() {
        purgeExpiredEntries();
        return map.size();
    }

    @Override
    public void clear() {
        for (K key : Set.copyOf(map.keySet())) {
            remove(key);
        }
    }

    @Override
    public void lock(K key) {
        locks.lock(key);
    }

    @Override
    public void unlock(K key) {
        locks.unlock(key);
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
