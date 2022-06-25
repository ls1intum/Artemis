package de.tum.in.www1.artemis.service.plagiarism.cache;

import static de.tum.in.www1.artemis.config.Constants.HAZELCAST_ACTIVE_PLAGIARISM_CHECKS_PER_COURSE_CACHE;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hazelcast.config.*;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;

public class PlagiarismCache {

    private final Logger logger = LoggerFactory.getLogger(PlagiarismCache.class);

    private final IMap<Long, Boolean> activePlagiarismChecksPerCourse;

    public PlagiarismCache(HazelcastInstance hazelcastInstance) {
        this.activePlagiarismChecksPerCourse = hazelcastInstance.getMap(HAZELCAST_ACTIVE_PLAGIARISM_CHECKS_PER_COURSE_CACHE);
    }

    /**
     * Configures Hazelcast for the ExamCache before the HazelcastInstance is created.
     *
     * @param config the {@link Config} the ExamCache-specific configuration should be added to
     */
    static void configureHazelcast(Config config) {
        // Important to avoid continuous serialization and de-serialization and the implications on transient fields
        // of PlagiarismCache
        // @formatter:off
        EvictionConfig evictionConfig = new EvictionConfig().setEvictionPolicy(EvictionPolicy.NONE);
        NearCacheConfig nearCacheConfig = new NearCacheConfig()
            .setName(HAZELCAST_ACTIVE_PLAGIARISM_CHECKS_PER_COURSE_CACHE + "-local")
            .setInMemoryFormat(InMemoryFormat.OBJECT).setSerializeKeys(true)
            .setInvalidateOnChange(true)
            .setTimeToLiveSeconds(0)
            .setMaxIdleSeconds(0)
            .setEvictionConfig(evictionConfig)
            .setCacheLocalEntries(true);
        config.getMapConfig(HAZELCAST_ACTIVE_PLAGIARISM_CHECKS_PER_COURSE_CACHE).setNearCacheConfig(nearCacheConfig);
        // @formatter:on
    }

    public boolean isActivePlagiarismCheck(Long courseId) {
        return Optional.ofNullable(activePlagiarismChecksPerCourse.get(courseId)).orElse(false);
    }

    public void setActivePlagiarismCheck(Long courseId, boolean active) {
        activePlagiarismChecksPerCourse.lock(courseId);
        try {
            logger.info("Write cache {}", courseId);
            activePlagiarismChecksPerCourse.set(courseId, active);
            // We do this get here to deserialize and load the newly written instance into the near cache directly after the writing operation
            activePlagiarismChecksPerCourse.get(courseId);
        }
        finally {
            activePlagiarismChecksPerCourse.unlock(courseId);
        }
    }
}
