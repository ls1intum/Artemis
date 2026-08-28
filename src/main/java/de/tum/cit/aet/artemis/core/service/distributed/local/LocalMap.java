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
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

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

    private final ConcurrentHashMap<K, ReentrantLock> locks = new ConcurrentHashMap<>();

    /**
     * Nano-time deadlines for entries stored with a time-to-live. The local provider has no background eviction, so
     * expiry is enforced by purging on every read; that keeps reads consistent with the Hazelcast and Redis backends,
     * which also never return an entry whose time-to-live has elapsed.
     */
    private final ConcurrentHashMap<K, Long> expiryDeadlines = new ConcurrentHashMap<>();

    // Listeners that require the specific changed entries on add/update/remove
    private final ConcurrentHashMap<UUID, MapEntryListener<K, V>> entryListeners = new ConcurrentHashMap<>();

    // Simplified listeners that do not require changed entry but just notification
    private final ConcurrentHashMap<UUID, MapListener> mapListeners = new ConcurrentHashMap<>();

    private final ExecutorService notificationExecutor;

    /** Shared across every local map; entries with an expiry are rare and the task is a single guarded removal. */
    private static final ScheduledExecutorService EXPIRY_SCHEDULER = Executors
            .newSingleThreadScheduledExecutor(BasicThreadFactory.builder().namingPattern("local-map-expiry-%d").daemon().build());

    public LocalMap() {
        this(Executors.newCachedThreadPool(BasicThreadFactory.builder().namingPattern("local-map-listener-%d").daemon().build()));
    }

    public LocalMap(ExecutorService notificationExecutor) {
        this.notificationExecutor = notificationExecutor;
        this.map = new ConcurrentHashMap<>();
    }

    /**
     * Removes every entry whose time-to-live has elapsed, firing the regular removal notifications so listeners see
     * expiry the same way they see an explicit removal.
     */
    private void purgeExpiredEntries() {
        if (expiryDeadlines.isEmpty()) {
            return;
        }
        long now = System.nanoTime();
        for (var deadline : expiryDeadlines.entrySet()) {
            K key = deadline.getKey();
            // Subtraction rather than comparison, so the check stays correct across nano-time wraparound.
            if (deadline.getValue() - now > 0) {
                continue;
            }
            // Re-read the deadline under the key lock rather than acting on what the scan saw. Between the two, a
            // writer can replace the value and drop or renew its deadline; deciding outside the lock let purge delete
            // that fresh value.
            ReentrantLock lock = getLock(key);
            V expiredValue = null;
            lock.lock();
            try {
                Long current = expiryDeadlines.get(key);
                if (current != null && current - now <= 0) {
                    expiryDeadlines.remove(key);
                    expiredValue = map.remove(key);
                }
            }
            finally {
                lock.unlock();
            }
            if (expiredValue != null) {
                notifyEntryRemoved(key, expiredValue);
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

    private ReentrantLock getLock(K key) {
        return locks.computeIfAbsent(key, _ -> new ReentrantLock());
    }

    @Override
    public V get(K key) {
        purgeExpiredEntries();
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
        purgeExpiredEntries();
        Map<K, V> result = new HashMap<>();
        for (K key : keys) {
            V value = get(key);
            // Absent keys are left out rather than mapped to null, matching Hazelcast's IMap.getAll and Redisson's
            // RMap.getAll. Mapping them to null would make result.size() and containsKey() disagree between backends.
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
        ReentrantLock lock = getLock(key);
        V oldValue;
        boolean isUpdate;

        lock.lock();
        try {
            oldValue = map.put(key, value);
            isUpdate = oldValue != null;
            writeDeadline(key, deadline);
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
    public void put(K key, V value, Duration timeToLive) {
        put(key, value);
        // The single-JVM backend has no eviction machinery, so schedule the expiry. Value-guarded so a re-put under the same key is never evicted by the previous entry's timer.
        EXPIRY_SCHEDULER.schedule(() -> {
            ReentrantLock lock = getLock(key);
            lock.lock();
            boolean expired;
            try {
                expired = map.remove(key, value);
            }
            finally {
                lock.unlock();
            }
            if (expired) {
                notifyEntryRemoved(key, value);
            }
        }, Math.max(1L, timeToLive.toMillis()), TimeUnit.MILLISECONDS);
    }

    @Override
    public V remove(K key) {
        ReentrantLock lock = getLock(key);
        V oldValue;

        lock.lock();
        try {
            oldValue = map.remove(key);
            expiryDeadlines.remove(key);
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
        Set<K> keysCopy = Set.copyOf(map.keySet());
        Map<K, V> entriesCopy = new HashMap<>(map);
        try {
            for (K key : keysCopy) {
                getLock(key).lock();
            }
            // Both wipes happen while the locks are held, for the same reason the per-key writes keep them together:
            // dropping the deadlines first would briefly leave live values without the lifetime they were stored with.
            map.clear();
            expiryDeadlines.clear();
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
