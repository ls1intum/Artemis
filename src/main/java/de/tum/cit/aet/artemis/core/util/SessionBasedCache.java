package de.tum.cit.aet.artemis.core.util;

import java.util.List;

import org.jspecify.annotations.Nullable;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

/**
 * A generic, type-safe wrapper for session-based caching operations.
 * Simplifies common cache operations (get, put, evict) for session-keyed data.
 * <p>
 * This wrapper is designed for use cases where data needs to be cached per session,
 * such as pending operations in multi-step workflows.
 *
 * @param <T> the type of elements stored in the cached lists
 */
public class SessionBasedCache<T> {

    private final CacheManager cacheManager;

    private final String cacheName;

    /**
     * Creates a new session-based cache wrapper.
     *
     * @param cacheManager the Spring CacheManager to use for cache operations
     * @param cacheName    the name of the cache to operate on (must be configured in CacheConfiguration)
     */
    public SessionBasedCache(CacheManager cacheManager, String cacheName) {
        this.cacheManager = cacheManager;
        this.cacheName = cacheName;
    }

    /**
     * Retrieves the cached list for the given session.
     *
     * @param sessionId the session identifier
     * @return the cached list, or null if no data exists for this session
     */
    @Nullable
    public List<T> get(String sessionId) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache == null) {
            return null;
        }
        return (List<T>) cache.get(sessionId, List.class);
    }

    /**
     * Stores a list in the cache for the given session.
     *
     * @param sessionId the session identifier
     * @param data      the list to cache
     */
    public void put(String sessionId, List<T> data) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.put(sessionId, data);
        }
    }

    /**
     * Removes the cached data for the given session.
     *
     * @param sessionId the session identifier
     */
    public void evict(String sessionId) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.evict(sessionId);
        }
    }
}
