package de.tum.cit.aet.artemis.core.config.cache;

import java.util.Set;

/**
 * A cache that can report the keys it currently holds.
 *
 * <p>
 * Spring's {@link org.springframework.cache.Cache} deliberately cannot enumerate keys, because not every store can.
 * Artemis needs it in exactly one place: the course notification caches are keyed by a composed string and have to be
 * invalidated by key prefix, since a single write invalidates every page of a user's notification list. Expressing that
 * as a capability interface keeps the caller independent of which store is behind the cache.
 */
public interface KeyEnumerableCache {

    /**
     * @return a snapshot of the keys currently held, safe to iterate while the cache is modified
     */
    Set<Object> cacheKeys();
}
