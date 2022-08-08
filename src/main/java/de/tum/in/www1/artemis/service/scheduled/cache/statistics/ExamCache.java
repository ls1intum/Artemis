package de.tum.in.www1.artemis.service.scheduled.cache.statistics;

import java.util.function.UnaryOperator;

import com.hazelcast.config.Config;
import com.hazelcast.core.HazelcastInstance;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.service.scheduled.cache.Cache;
import de.tum.in.www1.artemis.service.scheduled.cache.CacheHandler;

/**
 * This class manages all {@link ExamLiveStatisticsCache}s for all cached exams.
 * <p>
 * It encapsulates the Hazelcast objects to avoid unsafe actions on them and to prevent mistakes.
 * Hazelcast distributed objects are more database like, which means that modifications of the objects themselves
 * will not have any effect until they are send to all other instances, e.g. by replacing the value in the data structure.
 * <p>
 * To handle this better, we provide methods in the {@link CacheHandler super class} that make {@linkplain #getReadCacheFor(Long) read-operations} and
 * {@linkplain #getTransientWriteCacheFor(Long) write operations on transient properties} easier and less error-prone;
 * and that allow for {@linkplain #performCacheWrite(Long, UnaryOperator) atomic writes} (including an
 * {@linkplain #performCacheWriteIfPresent(Long, UnaryOperator) if-present variant}).
 * <p>
 * Additionally, we don't need any near cache configuration since reloading all actions from the cache is a very rare case.
 */
final class ExamCache extends CacheHandler<Long> {

    public ExamCache(HazelcastInstance hazelcastInstance) {
        super(hazelcastInstance, Constants.HAZELCAST_LIVE_STATISTICS_CACHE);
    }

    @Override
    protected Cache emptyCacheValue() {
        return ExamMonitoringCache.empty();
    }

    /**
     * Configures Hazelcast for the ExamCache before the HazelcastInstance is created.
     *
     * @param config the {@link Config} the ExamCache-specific configuration should be added to
     */
    static void configureHazelcast(Config config) {
        ExamLiveStatisticsCache.registerSerializers(config);
    }

    @Override
    protected Cache createDistributedCacheValue(Long examId) {
        var distributedCache = new ExamLiveStatisticsDistributedCache(examId);
        distributedCache.setHazelcastInstance(hazelcastInstance);

        return distributedCache;
    }

    @Override
    protected void clear() {
        cache.values().forEach(Cache::clear);
        cache.clear();
    }
}
