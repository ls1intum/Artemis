package de.tum.in.www1.artemis.service.util;

/**
 * Immutable tuple object.
 * @param <X> first param.
 * @param <Y> second param.
 */
public record Tuple<X, Y> (X x, Y y) {
}
