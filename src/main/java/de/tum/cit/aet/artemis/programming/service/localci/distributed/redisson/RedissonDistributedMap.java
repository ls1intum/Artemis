package de.tum.cit.aet.artemis.programming.service.localci.distributed.redisson;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.redisson.api.RMap;
import org.redisson.api.RTopic;
import org.redisson.client.RedisConnectionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.tum.cit.aet.artemis.programming.service.localci.distributed.api.map.DistributedMap;
import de.tum.cit.aet.artemis.programming.service.localci.distributed.api.map.listener.MapEntryAddedEvent;
import de.tum.cit.aet.artemis.programming.service.localci.distributed.api.map.listener.MapEntryListener;
import de.tum.cit.aet.artemis.programming.service.localci.distributed.api.map.listener.MapEntryRemovedEvent;
import de.tum.cit.aet.artemis.programming.service.localci.distributed.api.map.listener.MapEntryUpdatedEvent;
import de.tum.cit.aet.artemis.programming.service.localci.distributed.api.map.listener.MapListener;

public class RedissonDistributedMap<K, V> implements DistributedMap<K, V> {

    private static final Logger log = LoggerFactory.getLogger(RedissonDistributedMap.class);

    private final RMap<K, V> map;

    private final RTopic notificationTopic;

    private final Map<UUID, Integer> listenerRegistrations = new ConcurrentHashMap<>();

    public RedissonDistributedMap(RMap<K, V> map, RTopic notificationTopic) {
        this.map = map;
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
    public void unlock(K key) {
        map.getLock(key).unlock();
    }

    @Override
    public UUID addEntryListener(MapEntryListener<K, V> listener) {
        int registrationId = notificationTopic.addListener(MapItemEvent.class, (channel, event) -> {
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
        int registrationId = notificationTopic.addListener(MapItemEvent.class, (channel, event) -> {
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
