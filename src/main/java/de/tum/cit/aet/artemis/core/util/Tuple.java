package de.tum.cit.aet.artemis.core.util;

/**
 * Immutable tuple object.
 *
 * @param <X> first param.
 * @param <Y> second param.
 */
public record Tuple<X, Y>(X x, Y y) {
}
