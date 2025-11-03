package de.tum.cit.aet.artemis.programming.service.localci.distributed.api.queue.listener;

/**
 * Listener interface for receiving notifications about specific items being added to or removed from a distributed queue.
 * <p>
 * Implementations of this interface can be registered with a distributed queue to receive callbacks
 * whenever an item is added to or removed from the queue. The listener receives the affected item as a parameter.
 * </p>
 *
 * @param <T> the type of items in the queue
 */
public interface QueueItemListener<T> {

    /**
     * Called when an item is added to the queue.
     *
     * @param item the item that was added
     */
    void itemAdded(T item);

    /**
     * Called when an item is removed from the queue.
     *
     * @param item the item that was removed
     */
    void itemRemoved(T item);
}
