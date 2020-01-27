package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.forbidden;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.service.*;
import de.tum.in.www1.artemis.web.rest.errors.*;

public abstract class AssessmentResource {

    private final Logger log = LoggerFactory.getLogger(AssessmentResource.class);

    protected final AuthorizationCheckService authCheckService;

    protected final UserService userService;

    protected final ExerciseService exerciseService;

    protected final SubmissionService submissionService;

    protected final AssessmentService assessmentService;

    protected final ResultRepository resultRepository;

    public AssessmentResource(AuthorizationCheckService authCheckService, UserService userService, ExerciseService exerciseService, SubmissionService submissionService,
            AssessmentService assessmentService, ResultRepository resultRepository) {
        this.authCheckService = authCheckService;
        this.userService = userService;
        this.exerciseService = exerciseService;
        this.submissionService = submissionService;
        this.assessmentService = assessmentService;
        this.resultRepository = resultRepository;
    }

    abstract String getEntityName();

    /**
     * checks that the given user has at least tutor rights for the given exercise
     *
     * @param exercise the exercise for which the authorization should be checked
     * @throws AccessForbiddenException if current user is not at least teaching assistant in the given exercise
     * @throws BadRequestAlertException if no course is associated to the given exercise
     */
    void checkAuthorization(Exercise exercise, User user) throws AccessForbiddenException, BadRequestAlertException {
        validateExercise(exercise);
        if (!authCheckService.isAtLeastTeachingAssistantForExercise(exercise, user)) {
            throw new AccessForbiddenException("Insufficient permission for course: " + exercise.getCourse().getTitle());
        }
    }

    void validateExercise(Exercise exercise) throws BadRequestAlertException {
        Course course = exercise.getCourse();
        if (course == null) {
            throw new BadRequestAlertException("The course belonging to this exercise does not exist", getEntityName(), "courseNotFound");
        }
    }

    /**
     * checks if the user can override an already submitted result. This is only possible if the same tutor overrides before the assessment due date
     * or if an instructor overrides it.
     *
     * If the result does not yet exist or is not yet submitted, this method returns true
     *
     * @param submission the submission that might include an existing result which would include information about the assessor
     * @param exercise the exercise to which the submission and result belong and which potentially includes an assessment due date
     * @param user the user who initiates a request
     * @param isAtLeastInstructor whether the given user is an instructor for the given exercise
     * @return true of the the given user can override a potentially existing result
     */
    protected boolean isAllowedToCreateOrOverrideResult(Submission submission, Exercise exercise, User user, boolean isAtLeastInstructor) {
        final var existingResult = submission.getResult();
        if (existingResult == null) {
            // if there is no result yet, we can always save, submit and potentially "override"
            return true;
        }
        return assessmentService.isAllowedToOverrideExistingResult(existingResult, exercise, user, isAtLeastInstructor);
    }

    /**
     * checks if the user can override an already submitted result. This is only possible if the same tutor overrides before the assessment due date
     * or if an instructor overrides it.
     *
     * If the result does not yet exist or is not yet submitted, this method returns true
     *
     * @param resultId the id of a potentially existing result in case the result is updated (submitted or overridden)
     * @param exercise the exercise to which the submission and result belong and which potentially includes an assessment due date
     * @param user the user who initiates a request
     * @param isAtLeastInstructor whether the given user is an instructor for the given exercise
     * @return true of the the given user can override a potentially existing result
     */
    protected boolean isAllowedToOverrideExistingResult(long resultId, Exercise exercise, User user, boolean isAtLeastInstructor) {
        final var existingResult = resultRepository.findByIdWithEagerFeedbacksAndAssessor(resultId);
        if (existingResult.isEmpty()) {
            // if there is no result yet, we can always save, submit and potentially "override"
            return true;
        }
        return assessmentService.isAllowedToOverrideExistingResult(existingResult.get(), exercise, user, isAtLeastInstructor);
    }

    protected ResponseEntity<Void> cancelAssessment(long submissionId) {
        log.debug("REST request to cancel assessment of submission: {}", submissionId);
        Submission submission = submissionService.findOneWithEagerResult(submissionId);
        if (submission.getResult() == null) {
            // if there is no result everything is fine
            return ResponseEntity.ok().build();
        }
        User user = userService.getUserWithGroupsAndAuthorities();
        StudentParticipation studentParticipation = (StudentParticipation) submission.getParticipation();
        long exerciseId = studentParticipation.getExercise().getId();
        Exercise exercise = exerciseService.findOne(exerciseId);
        checkAuthorization(exercise, user);
        boolean isAtLeastInstructor = authCheckService.isAtLeastInstructorForExercise(exercise, user);
        if (!(isAtLeastInstructor || userService.getUser().getId().equals(submission.getResult().getAssessor().getId()))) {
            // tutors cannot cancel the assessment of other tutors (only instructors can)
            return forbidden();
        }
        assessmentService.cancelAssessmentOfSubmission(submission);
        return ResponseEntity.ok().build();
    }
}
