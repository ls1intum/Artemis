package de.tum.cit.aet.artemis.core.service.distributed.api.map;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/**
 * Copies a map so that it serializes to the same bytes every time, on every node.
 *
 * <p>
 * Redis implements {@link DistributedMap#replace(Object, Object, Object)} and {@link DistributedMap#remove(Object, Object)}
 * by comparing the encoded bytes of the expected value with the stored bytes. A value that was read from the store and
 * is handed back as the expected value is encoded again, so it only matches if encoding is stable across a round trip.
 * {@link Map#copyOf(Map)}, {@link Map#of()} and the other immutable maps are not: their iteration order depends on a
 * random per-JVM salt and, for colliding keys, on the order the entries were inserted in, so a map with two or more
 * entries is written in a different order after it has been read back. The compare-and-set then fails for good, and a
 * caller that retries it spins forever.
 *
 * <p>
 * Use this for every {@code Map} component of a value that is a compare-and-set operand. The sorted order is a pure
 * function of the keys, so it is the same after any number of round trips and on every node.
 */
public final class CanonicalMaps {

    private CanonicalMaps() {
    }

    /**
     * @param source the entries to copy; keys must be non-null and mutually comparable, with natural ordering consistent with equality
     * @param <K>    the key type
     * @param <V>    the value type
     * @return an unmodifiable copy that iterates, and therefore serializes, in key order
     * @throws IllegalArgumentException if distinct keys compare as equal
     */
    public static <K extends Comparable<? super K>, V> Map<K, V> copyOf(Map<K, V> source) {
        var sorted = new TreeMap<>(source);
        if (sorted.size() != source.size()) {
            throw new IllegalArgumentException("Map keys must have a natural ordering consistent with equality");
        }
        return Collections.unmodifiableSortedMap(sorted);
    }
}
