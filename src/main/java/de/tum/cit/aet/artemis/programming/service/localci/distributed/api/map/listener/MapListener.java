package de.tum.cit.aet.artemis.programming.service.localci.distributed.api.map.listener;

public interface MapListener {

    /**
     * Called when an entry is added to the map.
     */
    void entryAdded();

    /**
     * Called when an entry is removed from the map.
     */
    void entryRemoved();

    /**
     * Called when an entry in the map is updated.
     */
    void entryUpdated();
}
