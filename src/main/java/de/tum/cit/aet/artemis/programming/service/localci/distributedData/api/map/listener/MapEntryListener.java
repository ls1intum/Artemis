package de.tum.cit.aet.artemis.programming.service.localci.distributedData.api.map.listener;

public interface MapEntryListener<K, V> {

    void entryAdded(MapEntryEvent<K, V> event);

    void entryRemoved(MapEntryEvent<K, V> event);

    void entryUpdated(MapEntryEvent<K, V> event);
}
