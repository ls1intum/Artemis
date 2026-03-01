package de.tum.cit.aet.artemis.programming.service.localci.distributed.api.set;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

import de.tum.cit.aet.artemis.programming.service.localci.distributed.api.set.listener.SetItemListener;
import de.tum.cit.aet.artemis.programming.service.localci.distributed.api.set.listener.SetListener;

/**
 * A distributed set interface that provides operations for a set
 * that can be shared across multiple nodes in a cluster.
 *
 * @param <E> the type of elements in this set
 */
public interface DistributedSet<E> {

    /**
     * Adds the specified element to this set if it is not already present.
     *
     * @param element element to be added to this set
     * @return {@code true} if this set did not already contain the specified element
     */
    boolean add(E element);

    /**
     * Adds all of the elements in the specified collection to this set.
     *
     * @param elements collection containing elements to be added to this set
     * @return {@code true} if this set changed as a result of the call
     */
    boolean addAll(Collection<? extends E> elements);

    /**
     * Removes the specified element from this set if it is present.
     *
     * @param element element to be removed from this set, if present
     * @return {@code true} if this set contained the specified element
     */
    boolean remove(E element);

    /**
     * Removes all elements from this set that are contained in the specified collection.
     *
     * @param elements collection containing elements to be removed from this set
     * @return {@code true} if this set changed as a result of the call
     */
    boolean removeAll(Collection<?> elements);

    /**
     * Returns {@code true} if this set contains the specified element.
     *
     * @param element element whose presence in this set is to be tested
     * @return {@code true} if this set contains the specified element
     */
    boolean contains(E element);

    /**
     * Returns {@code true} if this set contains all of the elements in the specified collection.
     *
     * @param elements collection to be checked for containment in this set
     * @return {@code true} if this set contains all of the elements in the specified collection
     */
    boolean containsAll(Collection<?> elements);

    /**
     * Returns the number of elements in this set.
     *
     * @return the number of elements in this set
     */
    int size();

    /**
     * Returns {@code true} if this set contains no elements.
     *
     * @return {@code true} if this set contains no elements
     */
    boolean isEmpty();

    /**
     * Removes all of the elements from this set.
     */
    void clear();

    /**
     * Returns a copy of this set as a regular Java Set.
     *
     * @return a Set containing all elements in this distributed set
     */
    Set<E> getSetCopy();

    /**
     * Registers a listener that receives notifications for item-level events (add/remove).
     *
     * @param listener the listener to register
     * @return a UUID that can be used to remove the listener
     */
    UUID addItemListener(SetItemListener<E> listener);

    /**
     * Registers a simplified listener that receives notifications when the set changes.
     *
     * @param listener the listener to register
     * @return a UUID that can be used to remove the listener
     */
    UUID addListener(SetListener listener);

    /**
     * Removes a previously registered listener.
     *
     * @param listenerId the UUID of the listener to remove
     */
    void removeListener(UUID listenerId);
}
