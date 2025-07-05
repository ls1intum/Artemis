package de.tum.cit.aet.artemis.programming.service.localci.distributed.api.queue.listener;

import de.tum.cit.aet.artemis.programming.service.localci.distributed.api.queue.DistributedQueue;

/**
 * Listener interface for queue events.
 * <p>
 * Implementations of this interface can be registered with a {@link DistributedQueue}
 * to receive notifications when items are added to or removed from the queue.
 * Unlike {@link QueueItemListener}, this listener doesn't provide information about
 * the specific items that were added or removed.
 * </p>
 */
public interface QueueListener {

    /**
     * Called when an item is added to the queue.
     */
    void itemAdded();

    /**
     * Called when an item is removed from the queue.
     */
    void itemRemoved();
}
