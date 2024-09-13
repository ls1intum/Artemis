package de.tum.cit.aet.artemis.plagiarism.service.cache;

import static de.tum.cit.aet.artemis.core.config.Constants.HAZELCAST_ACTIVE_PLAGIARISM_CHECKS_PER_COURSE_CACHE;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Set;

import jakarta.annotation.PostConstruct;

import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Profile(PROFILE_CORE)
@Service
public class PlagiarismCacheService {

    private final RedissonClient redissonClient;

    // Every course in this set is currently doing a plagiarism check
    private Set<Long> activePlagiarismChecksPerCourse;

    public PlagiarismCacheService(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    @PostConstruct
    public void init() {
        this.activePlagiarismChecksPerCourse = redissonClient.getSet(HAZELCAST_ACTIVE_PLAGIARISM_CHECKS_PER_COURSE_CACHE);
    }

    /**
     * Returns the status of the course.
     *
     * @param courseId courseId used to identify the table entry
     * @return true if there is an active plagiarism check
     */
    public boolean isActivePlagiarismCheck(Long courseId) {
        return activePlagiarismChecksPerCourse.contains(courseId);
    }

    /**
     * There is an active plagiarism check in this course. The course id is added.
     *
     * @param courseId current course
     */
    public void setActivePlagiarismCheck(Long courseId) {
        activePlagiarismChecksPerCourse.add(courseId);
    }

    /**
     * There is no active plagiarism check anymore. The course id is removed.
     *
     * @param courseId current course
     */
    public void setInactivePlagiarismCheck(Long courseId) {
        activePlagiarismChecksPerCourse.remove(courseId);
    }
}
