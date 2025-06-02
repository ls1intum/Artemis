package de.tum.cit.aet.artemis.programming.service.localci.distributedData.api.map.listener;

public class MapEntryEvent<K, V> {

    private final K key;

    private final V value;

    private final V oldValue;

    public MapEntryEvent(K key, V value, V oldValue) {
        this.key = key;
        this.value = value;
        this.oldValue = oldValue;
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
