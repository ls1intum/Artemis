package de.tum.cit.aet.artemis.programming.service.localci.distributed.redisson;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class MapItemEvent<K, V> {

    public enum EventType {
        ADD, UPDATE, REMOVE
    }

    private final K key;

    private final V value;

    private final V oldValue;

    private final EventType eventType;

    @JsonCreator
    public MapItemEvent(@JsonProperty("eventType") EventType eventType, @JsonProperty("key") K key, @JsonProperty("value") V value, @JsonProperty("oldValue") V oldValue) {
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
