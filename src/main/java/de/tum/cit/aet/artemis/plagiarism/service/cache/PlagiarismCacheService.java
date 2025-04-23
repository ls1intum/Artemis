package de.tum.cit.aet.artemis.plagiarism.service.cache;

import static de.tum.cit.aet.artemis.core.config.Constants.HAZELCAST_ACTIVE_PLAGIARISM_CHECKS_PER_COURSE_CACHE;

import jakarta.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Service;

import com.hazelcast.collection.ISet;
import com.hazelcast.core.HazelcastInstance;

import de.tum.cit.aet.artemis.plagiarism.config.PlagiarismEnabled;

@Conditional(PlagiarismEnabled.class)
@Service
public class PlagiarismCacheService {

    private final HazelcastInstance hazelcastInstance;

    // Every course in this set is currently doing a plagiarism check
    private ISet<Long> activePlagiarismChecksPerCourse;

    public PlagiarismCacheService(@Qualifier("hazelcastInstance") HazelcastInstance hazelcastInstance) {
        this.hazelcastInstance = hazelcastInstance;
    }

    @PostConstruct
    public void init() {
        this.activePlagiarismChecksPerCourse = hazelcastInstance.getSet(HAZELCAST_ACTIVE_PLAGIARISM_CHECKS_PER_COURSE_CACHE);
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
