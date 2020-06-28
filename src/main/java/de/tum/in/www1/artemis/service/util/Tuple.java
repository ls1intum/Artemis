package de.tum.in.www1.artemis.service.util;

import java.util.Objects;

/**
 * Immutable tuple object.
 * @param <X> first param.
 * @param <Y> second param.
 */
public final class Tuple<X, Y> {

    public final X x;

    public final Y y;

    public Tuple(X x, Y y) {
        this.x = x;
        this.y = y;
    }

    /**
     * Two Tuple objects are considered equal if x1 and x2, y1 and y2 are equal.
     *
     * @param o Tuple
     * @return true if the Tuple objects are equal.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        Tuple<?, ?> tuple = (Tuple<?, ?>) o;
        return Objects.equals(x, tuple.x) && Objects.equals(y, tuple.y);
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y);
    }

    public X getX() {
        return x;
    }

    public Y getY() {
        return y;
    }
}
