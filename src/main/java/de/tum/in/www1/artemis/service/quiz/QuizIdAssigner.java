package de.tum.in.www1.artemis.service.quiz;

import java.util.Collection;

import de.tum.in.www1.artemis.domain.TempIdObject;

/**
 * Utility class for assigning IDs to a collection of objects that extend the TempIdObject class.
 * <p>
 * This class provides a static method to assign unique IDs to objects within a collection.
 * If an object already has an ID, it remains unchanged. If an object does not have an ID,
 * it will be assigned the next available ID starting from the maximum ID currently present in the collection.
 */
public class QuizIdAssigner {

    /**
     * Assigns unique IDs to a collection of objects that extend TempIdObject.
     * <p>
     * This method iterates over the collection, determines the highest existing ID,
     * and assigns new IDs incrementally to objects without an ID.
     * </p>
     *
     * @param items the collection of objects to which IDs should be assigned
     * @param <T>   the type of objects in the collection, which must extend TempIdObject
     */
    public static <T extends TempIdObject> void assignIds(Collection<T> items) {
        if (items == null) {
            throw new RuntimeException("Items should not be null");
        }

        Long currentId = items.stream().filter(item -> item.getId() != null).mapToLong(TempIdObject::getId).max().orElse(0L);

        for (TempIdObject item : items) {
            if (item.getId() == null) {
                currentId = currentId + 1;
                item.setId(currentId);
            }
        }
    }
}
