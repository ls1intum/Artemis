package de.tum.in.www1.artemis.service.scheduled.quiz;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.function.UnaryOperator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hazelcast.config.*;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import com.hazelcast.topic.ITopic;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.quiz.QuizExercise;

/**
 * This class manages all {@link QuizExerciseCache}s for all cached quiz exercises.
 * <p>
 * It encapsulates the Hazelcast objects to avoid unsafe actions on them and to prevent mistakes.
 * Hazelcast distributed objects are more database like, which means that modifications of the objects themselves
 * will not have any effect until they are send to all other instances, e.g. by replacing the value in the data structure.
 * <p>
 * To handle this better, we provide methods in this class that make {@linkplain #getReadCacheFor(Long) read-operations} and
 * {@linkplain #getTransientWriteCacheFor(Long) write operations on transient properties} easier and less error prone;
 * and that allow for {@linkplain #performCacheWrite(Long, UnaryOperator) atomic writes} (including an
 * {@linkplain #performCacheWriteIfPresent(Long, UnaryOperator) if-present variant}).
 */
final class QuizCache {

    private static final Logger log = LoggerFactory.getLogger(QuizCache.class);

    private static final String HAZELCAST_CACHED_EXERCISE_UPDATE_TOPIC = Constants.HAZELCAST_QUIZ_PREFIX + "cached-exercise-invalidation";

    private final ITopic<QuizExercise> cachedQuizExerciseUpdates;

    private final IMap<Long, QuizExerciseCache> cachedQuizExercises;

    private final HazelcastInstance hazelcastInstance;

    public QuizCache(HazelcastInstance hazelcastInstance) {
        this.hazelcastInstance = hazelcastInstance;
        this.cachedQuizExercises = hazelcastInstance.getMap(Constants.HAZELCAST_EXERCISE_CACHE);
        this.cachedQuizExerciseUpdates = hazelcastInstance.getTopic(HAZELCAST_CACHED_EXERCISE_UPDATE_TOPIC);
        this.cachedQuizExerciseUpdates.addMessageListener(newQuizExerciseMessage -> updateQuizExerciseLocally(newQuizExerciseMessage.getMessageObject()));
    }

    /**
     * Configures Hazelcast for the QuizCache before the HazelcastInstance is created.
     *
     * @param config the {@link Config} the QuizCache-specific configuration should be added to
     */
    static void configureHazelcast(Config config) {
        QuizExerciseCache.registerSerializers(config);
        // Important to avoid continuous serialization and de-serialization and the implications on transient fields of QuizExerciseCache
        EvictionConfig evictionConfig = new EvictionConfig() //
                .setEvictionPolicy(EvictionPolicy.NONE);
        NearCacheConfig nearCacheConfig = new NearCacheConfig() //
                .setName(Constants.HAZELCAST_EXERCISE_CACHE + "-local") //
                .setInMemoryFormat(InMemoryFormat.OBJECT) //
                .setSerializeKeys(true) //
                .setInvalidateOnChange(true) //
                .setTimeToLiveSeconds(0) //
                .setMaxIdleSeconds(0) //
                .setEvictionConfig(evictionConfig) //
                .setCacheLocalEntries(true);
        config.getMapConfig(Constants.HAZELCAST_EXERCISE_CACHE).setNearCacheConfig(nearCacheConfig);
    }

    /**
     * Returns all the {@link QuizExerciseCache} that are currently in the cache.
     *
     * @return a snapshot of all {@link QuizExerciseCache}s in this cache, cannot be modified (apart from transient properties)
     * @implNote This is the {@linkplain Map#values() value collection} of the map of this cache.
     */
    Collection<QuizExerciseCache> getAllQuizExerciseCaches() {
        // We do that here to avoid the distributed query of IMap.values() and its deserialization and benefit from the near cache.
        // due to concurrency, we need the filter here
        return cachedQuizExercises.keySet().stream().map(this::getCacheFor).filter(Objects::nonNull).toList();
    }

    /**
     * Returns a distributed quiz exercise cache or null.
     *
     * @return a {@link QuizExerciseCache} object, can be null
     * @implNote This is just a {@linkplain Map#get(Object) get} operation on the map of the cache.
     */
    QuizExerciseCache getCacheFor(Long quizExerciseId) {
        return cachedQuizExercises.get(quizExerciseId);
    }

    /**
     * Only for reading from QuizExerciseCache
     *
     * @param quizExerciseId the id of the quiz exercise, must not be null
     * @return a {@link QuizExerciseCache} object, never null but potentially {@linkplain EmptyQuizExerciseCache empty};
     */
    QuizExerciseCache getReadCacheFor(Long quizExerciseId) {
        return cachedQuizExercises.getOrDefault(quizExerciseId, QuizExerciseCache.empty());
    }

    /**
     * Only for the modification of transient properties, e.g. the exercise and the maps.
     * <p>
     * Creates new QuizExerciseCache if required.
     *
     * @param quizExerciseId the id of the quiz exercise, must not be null
     * @return a {@link QuizExerciseCache} object, never null and never {@linkplain EmptyQuizExerciseCache empty}
     */
    // TODO: rename method
    QuizExerciseCache getTransientWriteCacheFor(Long quizExerciseId) {
        // Look for an existing quiz cache
        var cachedQuiz = cachedQuizExercises.get(quizExerciseId);
        // If it exists, just return it
        if (cachedQuiz != null) {
            return cachedQuiz;
        }
        // Otherwise, lock the cache for this specific quizExerciseId
        cachedQuizExercises.lock(quizExerciseId);
        try {
            // Check again, if no existing quiz cache can be found
            cachedQuiz = cachedQuizExercises.get(quizExerciseId);
            // If it is now not null anymore, a concurrent process created a new one in the meantime before the lock. Return that one.
            if (cachedQuiz != null) {
                return cachedQuiz;
            }
            // Create a new QuizExerciseDistributedCache object and initialize it.
            var newCachedQuiz = new QuizExerciseDistributedCache(quizExerciseId);
            newCachedQuiz.setHazelcastInstance(hazelcastInstance);
            // Place the new quiz cache object in the distributed map (this will apparently *not* place it in the near OBJECT cache)
            cachedQuizExercises.set(quizExerciseId, newCachedQuiz);
            // Return the new deserialized, new cached object returned by get()
            // (this is not the newCachedQuiz object anymore although we use near caching in OBJECT in-memory format, because Hazelcast.)
            return cachedQuizExercises.get(quizExerciseId);
        }
        finally {
            cachedQuizExercises.unlock(quizExerciseId);
        }
    }

    /**
     * To perform the given action on QuizExerciseCache non-transient fields.
     * <p>
     * Creates new QuizExerciseCache if required.
     *
     * @param quizExerciseId the id of the quiz exercise, must not be null
     * @param writeOperation gets non-null and has  to return non-null.
     * @implNote This operation locks the quiz cache for the given <code>quizExerciseId</code> while the operation is executed. This prevents simultaneous writes.
     */
    void performCacheWrite(Long quizExerciseId, UnaryOperator<QuizExerciseCache> writeOperation) {
        cachedQuizExercises.lock(quizExerciseId);
        try {
            log.info("Write quiz cache {}", quizExerciseId);
            cachedQuizExercises.set(quizExerciseId, writeOperation.apply(getTransientWriteCacheFor(quizExerciseId)));
            // We do this get here to deserialize and load the newly written instance into the near cache directly after the write
            cachedQuizExercises.get(quizExerciseId);
        }
        finally {
            cachedQuizExercises.unlock(quizExerciseId);
        }
    }

    /**
     * To perform the given action on QuizExerciseCache non-transient fields.
     * <p>
     * Will not execute the <code>writeOperation</code> if no QuizExerciseCache exists for the given id.
     *
     * @param quizExerciseId the id of the quiz exercise, must not be null
     * @param writeOperation gets non-null and has  to return non-null.
     * @implNote This operation locks the quiz cache for the given <code>quizExerciseId</code> while the operation is executed. This prevents simultaneous writes.
     */
    void performCacheWriteIfPresent(Long quizExerciseId, UnaryOperator<QuizExerciseCache> writeOperation) {
        cachedQuizExercises.lock(quizExerciseId);
        try {
            QuizExerciseCache cachedQuiz = cachedQuizExercises.get(quizExerciseId);
            if (cachedQuiz != null) {
                log.info("Write quiz cache {}", quizExerciseId);
                cachedQuizExercises.set(quizExerciseId, writeOperation.apply(cachedQuiz));
                // We do this get here to deserialize and load the newly written instance into the near cache directly after the write
                cachedQuizExercises.get(quizExerciseId);
            }
        }
        finally {
            cachedQuizExercises.unlock(quizExerciseId);
        }
    }

    /**
     * This removes the quiz of given id from the cache, if possible.
     * <p>
     * This action will not cancel any scheduled tasks, this needs to be done separately.
     * Cached data like cached submissions and results will be preserved, if present.
     *
     * @param quizExerciseId the id of the quiz exercise cache to remove
     * @return the cache entry that was removed in case further processing is necessary
     * @implNote This just removes the {@link QuizExerciseCache} from the cache map
     */
    QuizExerciseCache remove(Long quizExerciseId) {
        return cachedQuizExercises.remove(quizExerciseId);
    }

    /**
     * This removes the quiz of given id from the cache, if possible, and clears it.
     * <p>
     * <b>WARNING:</b> The clear operation will clear all cached data like submissions and results.
     * Due to the concurrent nature of the cache, it is not possible to determine if this causes data to be lost.
     * When in doubt, use only {@link #remove(Long)} instead.
     * <p>
     * This action will not cancel any scheduled tasks, this needs to be done separately.
     *
     * @param quizExerciseId the id of the quiz exercise cache to remove and clear
     * @see #remove(Long)
     */
    void removeAndClear(Long quizExerciseId) {
        var cachedQuiz = cachedQuizExercises.remove(quizExerciseId);
        if (cachedQuiz != null) {
            cachedQuiz.clear();
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
    void clear() {
        cachedQuizExercises.values().forEach(QuizExerciseCache::clear);
        cachedQuizExercises.clear();
    }

    /**
     * Updates the cached {@link QuizExercise} object, mainly to prevent load on the DB.
     *
     * @param quizExercise the new quiz exercise object
     */
    void updateQuizExercise(QuizExercise quizExercise) {
        Objects.requireNonNull(quizExercise, "quizExercise must not be null");
        // Send every instance (including itself) a message to update the quizExercise of the corresponding QuizExerciseCache locally
        cachedQuizExerciseUpdates.publish(quizExercise);
    }

    /**
     * Set the transient <code>exercise</code> property of the quiz exercise cache for this instance only.
     *
     * @param quizExercise the new quiz exercise object
     */
    private void updateQuizExerciseLocally(QuizExercise quizExercise) {
        log.debug("Quiz exercise {} updated in quiz exercise map: {}", quizExercise.getId(), quizExercise);
        getTransientWriteCacheFor(quizExercise.getId()).setExercise(quizExercise);
    }
}
