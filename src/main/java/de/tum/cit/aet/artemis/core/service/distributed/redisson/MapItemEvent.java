package de.tum.cit.aet.artemis.core.service.distributed.redisson;

import java.io.Serial;
import java.io.Serializable;

/**
 * The map mutation broadcast on a map's notification topic, so that listeners on other nodes see it.
 *
 * <p>
 * Serializable because it travels over the wire like every other value in the distributed store. It carries the entry's
 * key and values, so those have to be serializable too - which they already are, since Hazelcast requires it of
 * everything stored in a distributed map.
 */
public class MapItemEvent<K, V> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public enum EventType {
        ADD, UPDATE, REMOVE
    }

    private final K key;

    private final V value;

    private final V oldValue;

    private final EventType eventType;

    private MapItemEvent(EventType eventType, K key, V value, V oldValue) {
        this.key = key;
        this.value = value;
        this.eventType = eventType;
        this.oldValue = oldValue;
    }

    public static <K, V> MapItemEvent<K, V> added(K key, V value) {
        return new MapItemEvent<>(EventType.ADD, key, value, null);
    }

    public static <K, V> MapItemEvent<K, V> updated(K key, V value, V oldValue) {
        return new MapItemEvent<>(EventType.UPDATE, key, value, oldValue);
    }

    public static <K, V> MapItemEvent<K, V> removed(K key, V oldValue) {
        return new MapItemEvent<>(EventType.REMOVE, key, null, oldValue);
    }

    public EventType getType() {
        return eventType;
    }

    public K getKey() {
        return key;
    }

    public V getValue() {
        return value;
    }

    public V getOldValue() {
        return oldValue;
    }
}
