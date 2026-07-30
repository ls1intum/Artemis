package de.tum.cit.aet.artemis.plagiarism.service.cache;

import static de.tum.cit.aet.artemis.core.config.Constants.ACTIVE_PLAGIARISM_CHECKS_PER_COURSE_CACHE;

import jakarta.annotation.PostConstruct;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.core.service.distributed.api.set.DistributedSet;
import de.tum.cit.aet.artemis.plagiarism.config.PlagiarismEnabled;

@Conditional(PlagiarismEnabled.class)
@Lazy
@Service
public class PlagiarismCacheService {

    private final DistributedDataProvider distributedDataProvider;

    // Every course in this set is currently doing a plagiarism check
    private DistributedSet<Long> activePlagiarismChecksPerCourse;

    public PlagiarismCacheService(DistributedDataProvider distributedDataProvider) {
        this.distributedDataProvider = distributedDataProvider;
    }

    /**
     * Gets the active plagiarism cases per course from the distributed data provider on bean creation.
     * EventListener cannot be used here, as the bean is lazy
     * <a href="https://docs.spring.io/spring-framework/reference/core/beans/context-introduction.html#context-functionality-events-annotation">Spring Docs</a>
     */
    @PostConstruct
    public void init() {
        this.activePlagiarismChecksPerCourse = distributedDataProvider.getSet(ACTIVE_PLAGIARISM_CHECKS_PER_COURSE_CACHE);
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
