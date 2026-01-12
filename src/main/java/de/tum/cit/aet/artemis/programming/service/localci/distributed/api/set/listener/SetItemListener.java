package de.tum.cit.aet.artemis.programming.service.localci.distributed.api.set.listener;

/**
 * Listener interface for receiving notifications about item-level changes in a distributed set.
 *
 * @param <E> the type of elements in the set
 */
public interface SetItemListener<E> {

    /**
     * Called when an item is added to the set.
     *
     * @param event the event containing the added item
     */
    void itemAdded(SetItemAddedEvent<E> event);

    /**
     * Called when an item is removed from the set.
     *
     * @param event the event containing the removed item
     */
    void itemRemoved(SetItemRemovedEvent<E> event);
}
