package de.tum.cit.aet.artemis.core.util;

import java.util.Collection;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.hibernate.Hibernate;
import org.jspecify.annotations.Nullable;

/**
 * Utility methods to reduce boilerplate when applying DTO field values to entities.
 */
public final class DTOHelper {

    private DTOHelper() {
        // utility class
    }

    /**
     * Applies a value to a setter only if the value is not null.
     * Replaces the common pattern: {@code if (value != null) { setter.accept(value); }}
     *
     * @param <T>    the type of the value
     * @param value  the nullable value from the DTO
     * @param setter the setter method reference on the target entity
     */
    public static <T> void setIfPresent(@Nullable T value, Consumer<T> setter) {
        if (value != null) {
            setter.accept(value);
        }
    }

    /**
     * Maps a Hibernate-managed collection to a set of DTOs, returning null if the collection
     * is null or not initialized.
     * Replaces the common pattern:
     * {@code if (collection != null && Hibernate.isInitialized(collection)) { return collection.stream().map(mapper).collect(toSet()); } }
     *
     * @param <S>        the source element type
     * @param <D>        the target DTO type
     * @param collection the nullable Hibernate-managed collection
     * @param mapper     the mapping function from entity to DTO
     * @return a set of mapped DTOs, or null if the collection is not available
     */
    @Nullable
    public static <S, D> Set<D> mapInitializedSet(@Nullable Collection<S> collection, Function<S, D> mapper) {
        if (collection == null || !Hibernate.isInitialized(collection)) {
            return null;
        }
        return collection.stream().map(mapper).collect(Collectors.toSet());
    }

    /**
     * Clears a Hibernate-managed set if it is non-null and initialized.
     * Replaces the common pattern: {@code if (set != null && Hibernate.isInitialized(set)) { set.clear(); }}
     *
     * @param <T> the element type
     * @param set the nullable Hibernate-managed set
     */
    public static <T> void clearIfInitialized(@Nullable Set<T> set) {
        if (set != null && Hibernate.isInitialized(set)) {
            set.clear();
        }
    }
}
