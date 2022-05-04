package de.tum.in.www1.artemis.service.exam.monitoring;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.function.UnaryOperator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hazelcast.config.*;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;

import de.tum.in.www1.artemis.config.Constants;

final class ExamCache {

    private static final Logger log = LoggerFactory.getLogger(ExamCache.class);

    private final IMap<Long, ExamMonitoringCache> cachedExamMonitoring;

    private final HazelcastInstance hazelcastInstance;

    public ExamCache(HazelcastInstance hazelcastInstance) {
        this.hazelcastInstance = hazelcastInstance;
        this.cachedExamMonitoring = hazelcastInstance.getMap(Constants.HAZELCAST_EXAM_MONITORING_CACHE);
    }

    /**
     * Configures Hazelcast for the QuizCache before the HazelcastInstance is created.
     *
     * @param config the {@link Config} the QuizCache-specific configuration should be added to
     */
    static void configureHazelcast(Config config) {
        ExamMonitoringCache.registerSerializers(config);
        // Important to avoid continuous serialization and de-serialization and the implications on transient fields
        // of QuizExerciseCache
        EvictionConfig evictionConfig = new EvictionConfig().setEvictionPolicy(EvictionPolicy.NONE);
        NearCacheConfig nearCacheConfig = new NearCacheConfig().setName(Constants.HAZELCAST_EXAM_MONITORING_CACHE + "-local").setInMemoryFormat(InMemoryFormat.OBJECT)
                .setSerializeKeys(true).setInvalidateOnChange(true).setTimeToLiveSeconds(0).setMaxIdleSeconds(0).setEvictionConfig(evictionConfig).setCacheLocalEntries(true);
        config.getMapConfig(Constants.HAZELCAST_EXAM_MONITORING_CACHE).setNearCacheConfig(nearCacheConfig);
    }

    /**
     * Returns all the {@link ExamMonitoringCache} that are currently in the cache.
     *
     * @return a snapshot of all {@link ExamMonitoringCache}s in this cache, cannot be modified (apart from transient
     * properties)
     * @implNote This is the {@linkplain Map#values() value collection} of the map of this cache.
     */
    Collection<ExamMonitoringCache> getAllExamMonitoringCaches() {
        // We do that here to avoid the distributed query of IMap.values() and its deserialization and benefit from
        // the near cache.
        // due to concurrency, we need the filter here
        return cachedExamMonitoring.keySet().stream().map(this::getCacheFor).filter(Objects::nonNull).toList();
    }

    /**
     * Returns a distributed exam monitoring cache or null.
     *
     * @return a {@link ExamMonitoringCache} object, can be null
     * @implNote This is just a {@linkplain Map#get(Object) get} operation on the map of the cache.
     */
    ExamMonitoringCache getCacheFor(Long quizExerciseId) {
        return cachedExamMonitoring.get(quizExerciseId);
    }

    /**
     * Only for reading from ExamMonitoringCache
     *
     * @param examId the id of the exam, must not be null
     * @return a {@link ExamMonitoringCache} object, never null but potentially {@linkplain EmptyExamMonitoringCache
     * empty};
     */
    ExamMonitoringCache getReadCacheFor(Long examId) {
        return cachedExamMonitoring.getOrDefault(examId, ExamMonitoringCache.empty());
    }

    /**
     * Only for the modification of transient properties.
     * <p>
     * Creates new ExamMonitoringCache if required.
     *
     * @param examId the id of the exam, must not be null
     * @return a {@link ExamMonitoringCache} object, never null and never {@linkplain EmptyExamMonitoringCache empty}
     */
    ExamMonitoringCache getTransientWriteCacheFor(Long examId) {
        // Look for an existing exam cache
        var cachedExam = cachedExamMonitoring.get(examId);
        // If it exists, just return it
        if (cachedExam != null) {
            return cachedExam;
        }
        // Otherwise, lock the cache for this specific examId
        cachedExamMonitoring.lock(examId);
        try {
            // Check again, if no existing exam cache can be found
            cachedExam = cachedExamMonitoring.get(examId);
            // If it is now not null anymore, a concurrent process created a new one in the meantime before the lock.
            // Return that one.
            if (cachedExam != null) {
                return cachedExam;
            }
            // Create a new ExamMonitoringDistributedCache object and initialize it.
            var newCachedExam = new ExamMonitoringDistributedCache(examId);
            newCachedExam.setHazelcastInstance(hazelcastInstance);
            // Place the new exam cache object in the distributed map (this will apparently *not* place it in the
            // near OBJECT cache)
            cachedExamMonitoring.set(examId, newCachedExam);
            // Return the new deserialized, new cached object returned by get()
            // (this is not the newCachedQuiz object anymore, although we use near caching in OBJECT in-memory
            // format, because Hazelcast.)
            return cachedExamMonitoring.get(examId);
        }
        finally {
            cachedExamMonitoring.unlock(examId);
        }
    }

    /**
     * To perform the given action on ExamMonitoringCache non-transient fields.
     * <p>
     * Creates new ExamMonitoringCache if required.
     *
     * @param examId         the id of the exam, must not be null
     * @param writeOperation gets non-null and has  to return non-null.
     * @implNote This operation locks the exam cache for the given <code>examId</code> while the operation is
     * executed. This prevents simultaneous writes.
     */
    void performCacheWrite(Long examId, UnaryOperator<ExamMonitoringCache> writeOperation) {
        cachedExamMonitoring.lock(examId);
        try {
            log.info("Write exam cache {}", examId);
            cachedExamMonitoring.set(examId, writeOperation.apply(getTransientWriteCacheFor(examId)));
            // We do this get here to deserialize and load the newly written instance into the near cache directly
            // after the writing operation
            cachedExamMonitoring.get(examId);
        }
        finally {
            cachedExamMonitoring.unlock(examId);
        }
    }

    /**
     * To perform the given action on ExamMonitoringCache non-transient fields.
     * <p>
     * Will not execute the <code>writeOperation</code> if no ExamMonitoringCache exists for the given id.
     *
     * @param examId         the id of the exam, must not be null
     * @param writeOperation gets non-null and has  to return non-null.
     * @implNote This operation locks the exam cache for the given <code>examId</code> while the operation is
     * executed. This prevents simultaneous writes.
     */
    void performCacheWriteIfPresent(Long examId, UnaryOperator<ExamMonitoringCache> writeOperation) {
        cachedExamMonitoring.lock(examId);
        try {
            ExamMonitoringCache cachedQuiz = cachedExamMonitoring.get(examId);
            if (cachedQuiz != null) {
                log.info("Write exam cache {}", examId);
                cachedExamMonitoring.set(examId, writeOperation.apply(cachedQuiz));
                // We do this get here to deserialize and load the newly written instance into the near cache
                // directly after the writing operation
                cachedExamMonitoring.get(examId);
            }
        }
        finally {
            cachedExamMonitoring.unlock(examId);
        }
    }

    /**
     * This removes the exam of given id from the cache, if possible.
     * <p>
     * This action will not cancel any scheduled tasks, this needs to be done separately.
     * Cached data will be preserved, if present.
     *
     * @param examId the id of the exam, must not be null
     * @return the cache entry that was removed in case further processing is necessary
     * @implNote This just removes the {@link ExamMonitoringCache} from the cache map
     */
    ExamMonitoringCache remove(Long examId) {
        return cachedExamMonitoring.remove(examId);
    }

    /**
     * This removes the exam of given id from the cache, if possible, and clears it.
     * <p>
     * <b>WARNING:</b> The clear operation will clear all cached data.
     * Due to the concurrent nature of the cache, it is not possible to determine if this causes data to be lost.
     * When in doubt, use only {@link #remove(Long)} instead.
     * <p>
     * This action will not cancel any scheduled tasks, this needs to be done separately.
     *
     * @param examId the id of the quiz exercise cache to remove and clear
     * @see #remove(Long)
     */
    void removeAndClear(Long examId) {
        var cachedExam = cachedExamMonitoring.remove(examId);
        if (cachedExam != null) {
            cachedExam.clear();
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
        cachedExamMonitoring.values().forEach(ExamMonitoringCache::clear);
        cachedExamMonitoring.clear();
    }
}
