package de.tum.cit.aet.artemis.programming.service.localci.distributed.api.map.listener;

public interface MapEntryListener<K, V> {

    /**
     * Called when an entry is added to the map.
     *
     * @param event the event containing the key and value of the added entry
     */
    void entryAdded(MapEntryAddedEvent<K, V> event);

    /**
     * Called when an entry is removed from the map.
     *
     * @param event the event containing the key and the old value of the removed entry
     */
    void entryRemoved(MapEntryRemovedEvent<K, V> event);

    /**
     * Called when an entry in the map is updated.
     *
     * @param event the event containing the key, new value, and old value of the updated entry
     */
    void entryUpdated(MapEntryUpdatedEvent<K, V> event);
}
