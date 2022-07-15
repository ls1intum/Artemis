package de.tum.in.www1.artemis.service.scheduled.cache;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.function.UnaryOperator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;

public abstract class CacheHandler<K> {

    private final Logger logger = LoggerFactory.getLogger(CacheHandler.class);

    protected final HazelcastInstance hazelcastInstance;

    protected final IMap<K, Cache> cache;

    protected CacheHandler(HazelcastInstance hazelcastInstance, String name) {
        this.hazelcastInstance = hazelcastInstance;
        this.cache = hazelcastInstance.getMap(name);
    }

    /**
     * Returns all the {@link Cache} that are currently in the cache.
     *
     * @return a snapshot of all {@link Cache}s in this cache, cannot be modified (apart from transient properties)
     * @implNote This is the {@linkplain Map#values() value collection} of the map of this cache.
     */
    public Collection<Cache> getAllCaches() {
        // We do that here to avoid the distributed query of IMap.values() and its deserialization and benefit from the near cache.
        // due to concurrency, we need the filter here
        return cache.keySet().stream().map(this::getCacheFor).filter(Objects::nonNull).toList();
    }

    /**
     * Returns a distributed cache or null.
     *
     * @param key the id of the cache
     * @return a {@link Cache} object, can be null
     * @implNote This is just a {@linkplain Map#get(Object) get} operation on the map of the cache.
     */
    public Cache getCacheFor(K key) {
        return cache.get(key);
    }

    /**
     * Returns an empty cache.
     * @return empty {@link Cache}
     */
    protected abstract Cache emptyCacheValue();

    /**
     * Only for reading from cache
     *
     * @param key the id of the value, must not be null
     * @return a {@link Cache} object, never null but potentially empty
     */
    public Cache getReadCacheFor(K key) {
        return cache.getOrDefault(key, emptyCacheValue());
    }

    /**
     * Creates a distributed cache.
     * @param key identifier of the cache
     * @return created {@link Cache}
     */
    protected abstract Cache createDistributedCacheValue(K key);

    /**
     * Only for the modification of transient properties, e.g. the exercise and the maps.
     * <p>
     * Creates new CacheValue if required.
     *
     * @param key the id of the value, must not be null
     * @return a {@link Cache} object, never null and never empty
     */
    // TODO: rename method
    public Cache getTransientWriteCacheFor(K key) {
        // Look for a cache
        var cached = cache.get(key);
        // If it exists, just return it
        if (cached != null) {
            return cached;
        }
        // Otherwise, lock the cache for this specific key
        cache.lock(key);
        try {
            // Check again, if no existing cache can be found
            cached = cache.get(key);
            // If it is now not null anymore, a concurrent process created a new one in the meantime before the lock. Return that one.
            if (cached != null) {
                return cached;
            }
            // Create a new QuizExerciseDistributedCache object and initialize it.
            var newCached = createDistributedCacheValue(key);
            // Place the new cache object in the distributed map (this will apparently *not* place it in the near OBJECT cache)
            cache.set(key, newCached);
            // Return the new deserialized, new cached object returned by get()
            // (this is not the newCachedQuiz object anymore, although we use near caching in OBJECT in-memory format, because Hazelcast.)
            return cache.get(key);
        }
        finally {
            cache.unlock(key);
        }
    }

    /**
     * To perform the given action on QuizExerciseCache non-transient fields.
     * <p>
     * Creates new QuizExerciseCache if required.
     *
     * @param key the id of the cache, must not be null
     * @param writeOperation gets non-null and has  to return non-null.
     * @implNote This operation locks the cache for the given <code>quizExerciseId</code> while the operation is executed. This prevents simultaneous writes.
     */
    public void performCacheWrite(K key, UnaryOperator<Cache> writeOperation) {
        cache.lock(key);
        try {
            logger.info("Write cache {}", key);
            cache.set(key, writeOperation.apply(getTransientWriteCacheFor(key)));
            // We do this get here to deserialize and load the newly written instance into the near cache directly after the writing operation
            cache.get(key);
        }
        finally {
            cache.unlock(key);
        }
    }

    /**
     * To perform the given action on QuizExerciseCache non-transient fields.
     * <p>
     * Will not execute the <code>writeOperation</code> if no QuizExerciseCache exists for the given id.
     *
     * @param key the id of the cache, must not be null
     * @param writeOperation gets non-null and has  to return non-null.
     * @implNote This operation locks the cache for the given <code>quizExerciseId</code> while the operation is executed. This prevents simultaneous writes.
     */
    public void performCacheWriteIfPresent(K key, UnaryOperator<Cache> writeOperation) {
        cache.lock(key);
        try {
            Cache cached = cache.get(key);
            if (cached != null) {
                logger.info("Write cache {}", key);
                cache.set(key, writeOperation.apply(cached));
                // We do this get here to deserialize and load the newly written instance into the near cache directly after the write
                cache.get(key);
            }
        }
        finally {
            cache.unlock(key);
        }
    }

    /**
     * This removes the cache of given id from the cache, if possible.
     * <p>
     * This action will not cancel any scheduled tasks, this needs to be done separately.
     * Cached data like cached submissions and results will be preserved, if present.
     *
     * @param key the id of the cache to remove
     * @return the cache entry that was removed in case further processing is necessary
     * @implNote This just removes the {@link Cache} from the cache map
     */
    public Cache remove(K key) {
        return cache.remove(key);
    }

    /**
     * This removes the cache of given id from the cache, if possible, and clears it.
     * <p>
     * <b>WARNING:</b> The clear operation will clear all cached data like submissions and results.
     * Due to the concurrent nature of the cache, it is not possible to determine if this causes data to be lost.
     * When in doubt, use only {@link #remove(K)} instead.
     * <p>
     * This action will not cancel any scheduled tasks, this needs to be done separately.
     *
     * @param key the id of the cache to remove and clear
     * @see #remove(K)
     */
    public void removeAndClear(K key) {
        var cached = cache.remove(key);
        if (cached != null) {
            cached.clear();
        }
    }

    /**
     * Releases all cached resources, all cached objects will be lost.
     * <p>
     * <b>WARNING:</b> This should only be used for exceptional cases, such as deleting everything or for testing.
     * Due to the concurrent nature of the cache, it is not possible to determine if this causes data to be lost.
     * <p>
     * This action will not cancel any scheduled tasks, this needs to be done separately.
     */
    protected abstract void clear();
}
