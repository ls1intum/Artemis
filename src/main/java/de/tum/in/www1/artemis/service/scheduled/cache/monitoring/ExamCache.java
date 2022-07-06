package de.tum.in.www1.artemis.service.scheduled.cache.monitoring;

import java.util.function.UnaryOperator;

import com.hazelcast.config.*;
import com.hazelcast.core.HazelcastInstance;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.service.scheduled.cache.Cache;
import de.tum.in.www1.artemis.service.scheduled.cache.CacheHandler;

/**
 * This class manages all {@link ExamMonitoringCache}s for all cached exams.
 * <p>
 * It encapsulates the Hazelcast objects to avoid unsafe actions on them and to prevent mistakes.
 * Hazelcast distributed objects are more database like, which means that modifications of the objects themselves
 * will not have any effect until they are send to all other instances, e.g. by replacing the value in the data structure.
 * <p>
 * To handle this better, we provide methods in the {@link CacheHandler super class} that make {@linkplain #getReadCacheFor(Long) read-operations} and
 * {@linkplain #getTransientWriteCacheFor(Long) write operations on transient properties} easier and less error-prone;
 * and that allow for {@linkplain #performCacheWrite(Long, UnaryOperator) atomic writes} (including an
 * {@linkplain #performCacheWriteIfPresent(Long, UnaryOperator) if-present variant}).
 */
final class ExamCache extends CacheHandler<Long> {

    public ExamCache(HazelcastInstance hazelcastInstance) {
        super(hazelcastInstance, Constants.HAZELCAST_MONITORING_CACHE);
    }

    /**
     * Configures Hazelcast for the ExamCache before the HazelcastInstance is created.
     *
     * @param config the {@link Config} the ExamCache-specific configuration should be added to
     */
    static void configureHazelcast(Config config) {
        ExamMonitoringCache.registerSerializers(config);
        // Important to avoid continuous serialization and de-serialization and the implications on transient fields
        // of ExamMonitoringCache
        // @formatter:off
        EvictionConfig evictionConfig = new EvictionConfig().setEvictionPolicy(EvictionPolicy.NONE);
        NearCacheConfig nearCacheConfig = new NearCacheConfig()
                .setName(Constants.HAZELCAST_MONITORING_CACHE + "-local")
                .setInMemoryFormat(InMemoryFormat.OBJECT).setSerializeKeys(true)
                .setInvalidateOnChange(true)
                .setTimeToLiveSeconds(0)
                .setMaxIdleSeconds(0)
                .setEvictionConfig(evictionConfig)
                .setCacheLocalEntries(true);
        config.getMapConfig(Constants.HAZELCAST_MONITORING_CACHE).setNearCacheConfig(nearCacheConfig);
        // @formatter:on
    }

    @Override
    protected Cache emptyCacheValue() {
        return ExamMonitoringCache.empty();
    }

    @Override
    protected Cache createDistributedCacheValue(Long examId) {
        var distributedCache = new ExamMonitoringDistributedCache(examId);
        distributedCache.setHazelcastInstance(hazelcastInstance);

        return distributedCache;
    }

    @Override
    protected void clear() {
        cache.values().forEach(Cache::clear);
        cache.clear();
    }
}
