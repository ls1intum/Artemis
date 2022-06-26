package de.tum.in.www1.artemis.service.plagiarism.cache;

import org.springframework.stereotype.Service;

import com.hazelcast.core.HazelcastInstance;

@Service
public class PlagiarismCacheService {

    private final PlagiarismCache plagiarismCache;

    public PlagiarismCacheService(HazelcastInstance hazelcastInstance) {
        plagiarismCache = new PlagiarismCache(hazelcastInstance);
    }

    /**
     * Returns the status of the course.
     * @param courseId current course
     * @return true if there is an active plagiarism check
     */
    public boolean isActivePlagiarismCheck(Long courseId) {
        return plagiarismCache.isActivePlagiarismCheck(courseId);
    }

    /**
     * There is an active plagiarism check in this course. The entry is set to true.
     * @param courseId current course
     */
    public void setActivePlagiarismCheck(Long courseId) {
        plagiarismCache.setActivePlagiarismCheck(courseId);
    }

    /**
     * There is no active plagiarism check anymore. The entry is set to false.
     * @param courseId current course
     */
    public void setInactivePlagiarismCheck(Long courseId) {
        plagiarismCache.setInactivePlagiarismCheck(courseId);
    }
}
