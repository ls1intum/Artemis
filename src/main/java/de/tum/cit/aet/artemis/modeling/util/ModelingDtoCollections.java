package de.tum.cit.aet.artemis.modeling.util;

import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.hibernate.Hibernate;

public final class ModelingDtoCollections {

    private ModelingDtoCollections() {
    }

    public static <E, D> List<D> listFromInitializedSet(Set<E> source, Function<? super E, D> mapper) {
        if (source == null || !Hibernate.isInitialized(source)) {
            return null;
        }
        return source.isEmpty() ? List.of() : source.stream().map(mapper).toList();
    }

    public static <E, D> Set<D> setFromInitializedSet(Set<E> source, Function<? super E, D> mapper) {
        if (source == null || !Hibernate.isInitialized(source)) {
            return null;
        }
        return source.isEmpty() ? Set.of() : source.stream().map(mapper).collect(Collectors.toSet());
    }

    public static <E> Set<E> copyInitializedSet(Set<E> source) {
        if (source == null || !Hibernate.isInitialized(source)) {
            return null;
        }
        return Set.copyOf(source);
    }
}
