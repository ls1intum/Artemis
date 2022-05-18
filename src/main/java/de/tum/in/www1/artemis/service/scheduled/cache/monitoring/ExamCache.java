package de.tum.in.www1.artemis.service.scheduled.cache.monitoring;

import com.hazelcast.config.*;
import com.hazelcast.core.HazelcastInstance;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.service.scheduled.cache.Cache;
import de.tum.in.www1.artemis.service.scheduled.cache.CacheHandler;

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
