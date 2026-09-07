package de.tum.cit.aet.artemis.core.service.distributed.api.set;

import java.util.Set;

/**
 * A set shared across all nodes of the cluster.
 *
 * <p>
 * Kept deliberately narrow: only the operations that callers actually need, so that every backend can support the whole
 * interface. Iteration is exposed as {@link #getAll()} returning a snapshot rather than as {@link Iterable}, because
 * streaming a live distributed collection is unreliable while it is being mutated concurrently.
 *
 * @param <T> element type
 */
public interface DistributedSet<T> {

    /**
     * @param item the element to add
     * @return true if the element was not already present
     */
    boolean add(T item);

    /**
     * @param item the element to remove
     * @return true if the element was present
     */
    boolean remove(T item);

    /**
     * @param item the element to look for
     * @return true if the element is present
     */
    boolean contains(T item);

    /**
     * @return a snapshot of all elements
     */
    Set<T> getAll();

    /**
     * @return the number of elements
     */
    int size();

    /**
     * @return true if the set holds no elements
     */
    boolean isEmpty();

    /**
     * Removes all elements.
     */
    void clear();
}
