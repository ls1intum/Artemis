package de.tum.cit.aet.artemis.programming.service.localci.distributed.api.queue;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import de.tum.cit.aet.artemis.programming.service.localci.distributed.api.queue.listener.QueueItemListener;
import de.tum.cit.aet.artemis.programming.service.localci.distributed.api.queue.listener.QueueListener;

public interface DistributedQueue<T> {

    /**
     * Adds an item to the queue.
     *
     * @param item the item to add
     * @return {@code true} if the item was added successfully, {@code false} otherwise
     */
    boolean add(T item);

    /**
     * Retrieves and removes the head of the queue.
     *
     * @return the head of the queue, or {@code null} if the queue is empty
     */
    T poll();

    /**
     * Retrieves, but does not remove, the head of the queue.
     *
     * @return the head of the queue, or {@code null} if the queue is empty
     */
    T peek();

    /**
     * Clears the queue, removing all items.
     */
    void clear();

    /**
     * Adds all items from the specified collection to the queue.
     *
     * @param items the collection of items to add
     * @return {@code true} if the queue was modified as a result of the call, {@code false} otherwise
     */
    boolean addAll(Collection<T> items);

    /**
     * Removes all specified items from the queue.
     *
     * @param items the collection of items to remove
     */
    void removeAll(Collection<T> items);

    /**
     * Retrieves all items currently in the queue.
     *
     * @return a list of all items in the queue
     */
    List<T> getAll();

    /**
     * Returns the name of the queue.
     *
     * @return queue name
     */
    String getName();

    /**
     * Checks if the queue is empty.
     *
     * @return {@code true} if the queue contains no items, {@code false} otherwise
     */
    boolean isEmpty();

    /**
     * Returns the number of items in the queue.
     *
     * @return the size of the queue
     */
    int size();

    /**
     * Adds a listener that will be notified when items are added or removed from the queue.
     * The listener methods get the added or removed items passed as parameter.
     *
     * @param listener the listener to add
     * @return a unique identifier for the registration, which can be used to remove the listener later
     */
    UUID addItemListener(QueueItemListener<T> listener);

    /**
     * Adds a listener that will be notified when items are added or removed from the queue.
     * It is simplified version of listener that does not get specific items passed as parameter.
     *
     * @param listener the listener to add
     * @return a unique identifier for the registration, which can be used to remove the listener later
     */
    UUID addListener(QueueListener listener);

    /**
     * Removes a previously registered listener using its unique identifier.
     *
     * @param registrationId the unique identifier of the listener to remove
     */
    void removeListener(UUID registrationId);
}
