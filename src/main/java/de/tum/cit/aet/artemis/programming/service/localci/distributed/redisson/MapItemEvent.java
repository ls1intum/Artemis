package de.tum.cit.aet.artemis.programming.service.localci.distributed.redisson;

public class MapItemEvent<K, V> {

    public enum EventType {
        ADD, UPDATE, REMOVE
    }

    private final K key;

    private final V value;

    private final V oldValue;

    private final EventType eventType;

    public MapItemEvent(EventType eventType, K key, V value) {
        this.key = key;
        this.value = value;
        this.eventType = eventType;
        this.oldValue = null;
    }

    public MapItemEvent(EventType eventType, K key, V value, V oldValue) {
        this.key = key;
        this.value = value;
        this.eventType = eventType;
        this.oldValue = oldValue;
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
