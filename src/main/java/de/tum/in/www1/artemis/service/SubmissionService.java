package de.tum.in.www1.artemis.service;

import static de.tum.in.www1.artemis.config.Constants.MAX_NUMBER_OF_LOCKED_SUBMISSIONS_PER_TUTOR;

import de.tum.in.www1.artemis.repository.SubmissionRepository;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;

abstract class SubmissionService {

    private SubmissionRepository submissionRepository;

    private UserService userService;

    public SubmissionService(SubmissionRepository submissionRepository, UserService userService) {
        this.submissionRepository = submissionRepository;
        this.userService = userService;
    }

    /**
     * Check if the limit of simultaneously locked submissions (i.e. unfinished assessments) has been reached for the current user in the given course. Throws a
     * BadRequestAlertException if the limit has been reached.
     *
     * @param courseId the id of the course
     */
    public void checkSubmissionLockLimit(long courseId) {
        long numberOfLockedSubmissions = submissionRepository.countLockedSubmissionsByUserIdAndCourseId(userService.getUserWithGroupsAndAuthorities().getId(), courseId);
        if (numberOfLockedSubmissions >= MAX_NUMBER_OF_LOCKED_SUBMISSIONS_PER_TUTOR) {
            throw new BadRequestAlertException("The limit of locked submissions has been reached", "submission", "lockedSubmissionsLimitReached");
        }
    }
}
