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
     * Claims the course for a plagiarism check. Claiming and checking are one atomic operation, so that two checks
     * started at the same moment - on the same node or on different ones - cannot both believe they are the only one.
     *
     * @param courseId current course
     * @return true if the caller claimed the course and therefore has to release it again, false if a check is already running
     */
    public boolean tryStartPlagiarismCheck(Long courseId) {
        return activePlagiarismChecksPerCourse.add(courseId);
    }

    /**
     * Releases the course again. Only the caller whose {@link #tryStartPlagiarismCheck(Long)} returned true may call
     * this, otherwise it would release the check somebody else is still running.
     *
     * @param courseId current course
     */
    public void finishPlagiarismCheck(Long courseId) {
        activePlagiarismChecksPerCourse.remove(courseId);
    }
}
