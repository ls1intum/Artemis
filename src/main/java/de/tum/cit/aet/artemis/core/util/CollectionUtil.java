package de.tum.cit.aet.artemis.core.util;

import java.util.Collection;

public class CollectionUtil {

    public static <T, C extends Collection<T>> C nullIfEmpty(C collection) {
        if (collection == null || collection.isEmpty()) {
            return null;
        }
        else {
            return collection;
        }
    }
}
