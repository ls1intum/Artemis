package de.tum.cit.aet.artemis.core.util;

import java.util.Collection;

public class CollectionUtil {

    /**
     * takes a Collection and return null if the Collection is empty.
     *
     * @param collection the collection to be checked
     * @param <T>        The class of the objects saved in the collection
     * @param <C>        The class of the collection
     * @return return null if the collection is null or `isEmpty()`, otherwise returns the collection
     */
    public static <T, C extends Collection<T>> C nullIfEmpty(C collection) {
        if (collection == null || collection.isEmpty()) {
            return null;
        }
        else {
            return collection;
        }
    }
}
