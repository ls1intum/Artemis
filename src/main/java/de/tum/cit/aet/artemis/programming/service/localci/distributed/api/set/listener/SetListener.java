package de.tum.cit.aet.artemis.programming.service.localci.distributed.api.set.listener;

/**
 * Simplified listener interface for receiving notifications when a distributed set changes.
 * This listener does not provide details about which items changed.
 */
public interface SetListener {

    /**
     * Called when the set has been modified (item added or removed).
     */
    void onSetChanged();
}
