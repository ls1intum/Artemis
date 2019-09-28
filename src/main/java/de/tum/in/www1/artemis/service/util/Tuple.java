package de.tum.in.www1.artemis.service.util;

/**
 * Immutable tuple object.
 * @param <X> first param.
 * @param <Y> second param.
 */
public class Tuple<X, Y> {

    public final X x;

    public final Y y;

    public Tuple(X x, Y y) {
        this.x = x;
        this.y = y;
    }
}
