package de.tum.in.www1.artemis.service.plagiarism.cache;

import org.springframework.stereotype.Service;

import com.hazelcast.config.Config;
import com.hazelcast.core.HazelcastInstance;

@Service
public class PlagiarismCacheService {

    private final PlagiarismCache plagiarismCache;

    public PlagiarismCacheService(HazelcastInstance hazelcastInstance) {
        plagiarismCache = new PlagiarismCache(hazelcastInstance);
    }

    public static void configureHazelcast(Config config) {
        PlagiarismCache.configureHazelcast(config);
    }

    public boolean isActivePlagiarismCheck(Long courseId) {
        return plagiarismCache.isActivePlagiarismCheck(courseId);
    }

    public void enablePlagiarismCheck(Long courseId) {
        plagiarismCache.setActivePlagiarismCheck(courseId, true);
    }

    public void disablePlagiarismCheck(Long courseId) {
        plagiarismCache.setActivePlagiarismCheck(courseId, false);
    }
}
