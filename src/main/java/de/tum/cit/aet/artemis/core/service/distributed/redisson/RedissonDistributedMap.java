package de.tum.cit.aet.artemis.core.service.distributed.redisson;

import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.jspecify.annotations.Nullable;
import org.redisson.api.RMap;
import org.redisson.api.RMapCache;
import org.redisson.api.RTopic;
import org.redisson.client.RedisConnectionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.tum.cit.aet.artemis.core.service.distributed.api.map.DistributedMap;
import de.tum.cit.aet.artemis.core.service.distributed.api.map.listener.MapEntryAddedEvent;
import de.tum.cit.aet.artemis.core.service.distributed.api.map.listener.MapEntryListener;
import de.tum.cit.aet.artemis.core.service.distributed.api.map.listener.MapEntryRemovedEvent;
import de.tum.cit.aet.artemis.core.service.distributed.api.map.listener.MapEntryUpdatedEvent;
import de.tum.cit.aet.artemis.core.service.distributed.api.map.listener.MapListener;

public class RedissonDistributedMap<K, V> implements DistributedMap<K, V> {

    private static final Logger log = LoggerFactory.getLogger(RedissonDistributedMap.class);

    private final RMap<K, V> map;

    /**
     * Non-null only when this map was created as an expiring map. Plain {@link RMap} has no notion of entry expiry, so
     * per-entry TTL requires an {@link RMapCache} backing.
     */
    @Nullable
    private final RMapCache<K, V> mapCache;

    private final RTopic notificationTopic;

    private final Map<UUID, Integer> listenerRegistrations = new ConcurrentHashMap<>();

    public RedissonDistributedMap(RMap<K, V> map, RTopic notificationTopic) {
        this.map = map;
        this.mapCache = null;
        this.notificationTopic = notificationTopic;
    }

    public RedissonDistributedMap(RMapCache<K, V> mapCache, RTopic notificationTopic) {
        this.map = mapCache;
        this.mapCache = mapCache;
        this.notificationTopic = notificationTopic;
    }

    private void publishSafely(Object event) {
        try {
            notificationTopic.publish(event);
        }
        catch (Exception ex) {
            log.warn("Failed to publish map notification. Event: {} for map {}", event, map.getName(), ex);
        }
    }

    @Override
    public V get(K key) {
        return map.get(key);
    }

    @Override
    public Map<K, V> getAll(Set<K> keys) {
        return map.getAll(keys);
    }

    @Override
    public void put(K key, V value) {
        V oldValue = map.put(key, value);
        if (oldValue != null) {
            publishSafely(MapItemEvent.updated(key, value, oldValue));
        }
        else {
            publishSafely(MapItemEvent.added(key, value));
        }
    }

    @Override
    public void put(K key, V value, Duration timeToLive) {
        if (mapCache == null) {
            throw new UnsupportedOperationException(
                    "Map '" + map.getName() + "' does not support entry expiry. Obtain it via DistributedDataProvider.getExpiringMap(name, ttl) instead of getMap(name).");
        }
        V oldValue = mapCache.put(key, value, timeToLive.toMillis(), TimeUnit.MILLISECONDS);
        if (oldValue != null) {
            publishSafely(MapItemEvent.updated(key, value, oldValue));
        }
        else {
            publishSafely(MapItemEvent.added(key, value));
        }
    }

    @Override
    public V putIfAbsent(K key, V value) {
        V existing = map.putIfAbsent(key, value);
        if (existing == null) {
            publishSafely(MapItemEvent.added(key, value));
        }
        return existing;
    }

    @Override
    public V putIfAbsent(K key, V value, Duration timeToLive) {
        if (mapCache == null) {
            throw new UnsupportedOperationException(
                    "Map '" + map.getName() + "' does not support entry expiry. Obtain it via DistributedDataProvider.getExpiringMap(name, ttl) instead of getMap(name).");
        }
        V existing = mapCache.putIfAbsent(key, value, timeToLive.toMillis(), TimeUnit.MILLISECONDS);
        if (existing == null) {
            publishSafely(MapItemEvent.added(key, value));
        }
        return existing;
    }

    @Override
    public boolean remove(K key, V value) {
        boolean removed = map.remove(key, value);
        if (removed) {
            publishSafely(MapItemEvent.removed(key, value));
        }
        return removed;
    }

    @Override
    public boolean replace(K key, V expectedValue, V replacementValue) {
        boolean replaced = map.replace(key, expectedValue, replacementValue);
        if (replaced) {
            publishSafely(MapItemEvent.updated(key, replacementValue, expectedValue));
        }
        return replaced;
    }

    @Override
    public boolean refreshTimeToLive(K key, Duration timeToLive) {
        if (mapCache == null) {
            throw new UnsupportedOperationException(
                    "Map '" + map.getName() + "' does not support entry expiry. Obtain it via DistributedDataProvider.getExpiringMap(name, ttl) instead of getMap(name).");
        }
        return mapCache.expireEntry(key, timeToLive, Duration.ZERO);
    }

    @Override
    public V remove(K key) {
        V oldValue = map.remove(key);
        if (oldValue != null) {
            publishSafely(MapItemEvent.removed(key, oldValue));
        }
        return oldValue;
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
        map.getLock(key).lock();

    }

    @Override
    public void lock(K key, Duration lease) {
        map.getLock(key).lock(lease.toMillis(), TimeUnit.MILLISECONDS);
    }

    @Override
    public void unlock(K key) {
        map.getLock(key).unlock();
    }

    @Override
    public UUID addEntryListener(MapEntryListener<K, V> listener) {
        int registrationId = notificationTopic.addListener(MapItemEvent.class, (_, event) -> {
            MapItemEvent<K, V> mapItemEvent = (MapItemEvent<K, V>) event;
            if (event.getType() == MapItemEvent.EventType.ADD) {
                listener.entryAdded(new MapEntryAddedEvent<>(mapItemEvent.getKey(), mapItemEvent.getValue()));
            }
            else if (event.getType() == MapItemEvent.EventType.UPDATE) {
                listener.entryUpdated(new MapEntryUpdatedEvent<>(mapItemEvent.getKey(), mapItemEvent.getValue(), mapItemEvent.getOldValue()));
            }
            else if (event.getType() == MapItemEvent.EventType.REMOVE) {
                listener.entryRemoved(new MapEntryRemovedEvent<>(mapItemEvent.getKey(), mapItemEvent.getOldValue()));
            }
        });
        UUID uuid = UUID.randomUUID();
        listenerRegistrations.put(uuid, registrationId);
        return uuid;
    }

    @Override
    public UUID addListener(MapListener listener) {
        int registrationId = notificationTopic.addListener(MapItemEvent.class, (_, event) -> {
            if (event.getType() == MapItemEvent.EventType.ADD) {
                listener.entryAdded();
            }
            else if (event.getType() == MapItemEvent.EventType.UPDATE) {
                listener.entryUpdated();
            }
            else if (event.getType() == MapItemEvent.EventType.REMOVE) {
                listener.entryRemoved();
            }
        });
        UUID uuid = UUID.randomUUID();
        listenerRegistrations.put(uuid, registrationId);
        return uuid;
    }

    @Override
    public void removeListener(UUID uuid) {
        try {
            Integer listenerId = listenerRegistrations.get(uuid);
            if (listenerId == null) {
                log.warn("No listener found for UUID: {}", uuid);
                return;
            }
            notificationTopic.removeListener(listenerId);
            listenerRegistrations.remove(uuid);
        }
        catch (RedisConnectionException e) {
            log.error("Could not remove listener due to Redis connection exception.", e);
        }
    }
}
