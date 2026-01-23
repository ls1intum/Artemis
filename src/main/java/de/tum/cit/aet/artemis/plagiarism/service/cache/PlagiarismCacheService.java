package de.tum.cit.aet.artemis.plagiarism.service.cache;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.plagiarism.config.PlagiarismEnabled;
import de.tum.cit.aet.artemis.programming.service.localci.DistributedDataAccessService;

@Conditional(PlagiarismEnabled.class)
@Lazy
@Service
public class PlagiarismCacheService {

    private final DistributedDataAccessService distributedDataAccessService;

    public PlagiarismCacheService(DistributedDataAccessService distributedDataAccessService) {
        this.distributedDataAccessService = distributedDataAccessService;
    }

    /**
     * Returns the status of the course.
     *
     * @param courseId courseId used to identify the table entry
     * @return true if there is an active plagiarism check
     */
    public boolean isActivePlagiarismCheck(Long courseId) {
        return distributedDataAccessService.getActivePlagiarismChecksPerCourse().contains(courseId);
    }

    /**
     * There is an active plagiarism check in this course. The course id is added.
     *
     * @param courseId current course
     */
    public void setActivePlagiarismCheck(Long courseId) {
        distributedDataAccessService.getDistributedActivePlagiarismChecksPerCourse().add(courseId);
    }

    /**
     * There is no active plagiarism check anymore. The course id is removed.
     *
     * @param courseId current course
     */
    public void setInactivePlagiarismCheck(Long courseId) {
        distributedDataAccessService.getDistributedActivePlagiarismChecksPerCourse().remove(courseId);
    }
}
