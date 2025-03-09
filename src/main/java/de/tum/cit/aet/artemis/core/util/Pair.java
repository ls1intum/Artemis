package de.tum.cit.aet.artemis.core.util;

import java.io.Serializable;

/**
 * Immutable tuple object.
 *
 * @param <F> first param.
 * @param <S> second param.
 */
public record Pair<F, S>(F first, S second) implements Serializable {
}
