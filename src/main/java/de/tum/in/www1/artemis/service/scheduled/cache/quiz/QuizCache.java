package de.tum.in.www1.artemis.service.scheduled.cache.quiz;

import java.util.Objects;
import java.util.function.UnaryOperator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hazelcast.config.*;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.topic.ITopic;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.quiz.QuizExercise;
import de.tum.in.www1.artemis.service.scheduled.cache.Cache;
import de.tum.in.www1.artemis.service.scheduled.cache.CacheHandler;

/**
 * This class manages all {@link QuizExerciseCache}s for all cached quiz exercises.
 * <p>
 * It encapsulates the Hazelcast objects to avoid unsafe actions on them and to prevent mistakes.
 * Hazelcast distributed objects are more database like, which means that modifications of the objects themselves
 * will not have any effect until they are send to all other instances, e.g. by replacing the value in the data structure.
 * <p>
 * To handle this better, we provide methods in the {@linkplain #CacheHandler super class} that make {@linkplain #getReadCacheFor(Long) read-operations} and
 * {@linkplain #getTransientWriteCacheFor(Long) write operations on transient properties} easier and less error-prone;
 * and that allow for {@linkplain #performCacheWrite(Long, UnaryOperator) atomic writes} (including an
 * {@linkplain #performCacheWriteIfPresent(Long, UnaryOperator) if-present variant}).
 */
final class QuizCache extends CacheHandler<Long> {

    private final Logger logger = LoggerFactory.getLogger(QuizCache.class);

    private static final String HAZELCAST_CACHED_EXERCISE_UPDATE_TOPIC = Constants.HAZELCAST_QUIZ_PREFIX + "cached-exercise-invalidation";

    private final ITopic<QuizExercise> cachedQuizExerciseUpdates;

    public QuizCache(HazelcastInstance hazelcastInstance) {
        super(hazelcastInstance, Constants.HAZELCAST_EXERCISE_CACHE);
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
        // @formatter:off
        EvictionConfig evictionConfig = new EvictionConfig()
                .setEvictionPolicy(EvictionPolicy.NONE);
        NearCacheConfig nearCacheConfig = new NearCacheConfig()
                .setName(Constants.HAZELCAST_EXERCISE_CACHE + "-local")
                .setInMemoryFormat(InMemoryFormat.OBJECT)
                .setSerializeKeys(true)
                .setInvalidateOnChange(true)
                .setTimeToLiveSeconds(0)
                .setMaxIdleSeconds(0)
                .setEvictionConfig(evictionConfig)
                .setCacheLocalEntries(true);
        // @formatter:on
        config.getMapConfig(Constants.HAZELCAST_EXERCISE_CACHE).setNearCacheConfig(nearCacheConfig);
    }

    @Override
    protected Cache emptyCacheValue() {
        return QuizExerciseCache.empty();
    }

    @Override
    protected Cache createDistributedCacheValue(Long exerciseId) {
        var distributedCache = new QuizExerciseDistributedCache(exerciseId);
        distributedCache.setHazelcastInstance(hazelcastInstance);

        return distributedCache;
    }

    /**
     * Releases all cached resources, all cached objects will be lost.
     * <p>
     * <b>WARNING:</b> This should only be used for exceptional cases, such as deleting everything or for testing.
     * Due to the concurrent nature of the cache, it is not possible to determine if this causes data to be lost.
     * <p>
     * This action will not cancel any scheduled tasks, this needs to be done separately.
     */
    protected void clear() {
        cache.values().forEach(Cache::clear);
        cache.clear();
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
        logger.debug("Quiz exercise {} updated in quiz exercise map: {}", quizExercise.getId(), quizExercise);
        ((QuizExerciseCache) getTransientWriteCacheFor(quizExercise.getId())).setExercise(quizExercise);
    }
}
