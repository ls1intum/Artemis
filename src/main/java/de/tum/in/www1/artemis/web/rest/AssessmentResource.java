package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.forbidden;
import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.notFound;

import java.time.ZonedDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.service.*;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;

public abstract class AssessmentResource {

    private final Logger log = LoggerFactory.getLogger(AssessmentResource.class);

    protected final AuthorizationCheckService authCheckService;

    protected final UserService userService;

    protected final ExerciseService exerciseService;

    protected final SubmissionService submissionService;

    protected final AssessmentService assessmentService;

    protected final ResultRepository resultRepository;

    protected final ExamService examService;

    protected final WebsocketMessagingService messagingService;

    protected final ExampleSubmissionService exampleSubmissionService;

    public AssessmentResource(AuthorizationCheckService authCheckService, UserService userService, ExerciseService exerciseService, SubmissionService submissionService,
            AssessmentService assessmentService, ResultRepository resultRepository, ExamService examService, WebsocketMessagingService messagingService,
            ExampleSubmissionService exampleSubmissionService) {
        this.authCheckService = authCheckService;
        this.userService = userService;
        this.exerciseService = exerciseService;
        this.submissionService = submissionService;
        this.assessmentService = assessmentService;
        this.resultRepository = resultRepository;
        this.examService = examService;
        this.messagingService = messagingService;
        this.exampleSubmissionService = exampleSubmissionService;
    }

    abstract String getEntityName();

    /**
     * Get the result of the submission with the given id. Returns a 403 Forbidden response if the user is not allowed to retrieve the assessment. The user is not allowed
     * to retrieve the assessment if he is not a student of the corresponding course, the submission is not his submission, the result is not finished or the assessment due date of
     * the corresponding exercise is in the future (or not set).
     *
     * @param submissionId the id of the submission that should be sent to the client
     * @return the assessment of the given id
     */
    ResponseEntity<Result> getAssessmentBySubmissionId(Long submissionId) {
        log.debug("REST request to get assessment for submission with id {}", submissionId);
        Submission submission = submissionService.findOneWithEagerResultAndFeedback(submissionId);
        StudentParticipation participation = (StudentParticipation) submission.getParticipation();
        Exercise exercise = participation.getExercise();

        Result result = submission.getLatestResult();
        if (result == null) {
            return notFound();
        }

        if (!authCheckService.isUserAllowedToGetResult(exercise, participation, result)) {
            return forbidden();
        }

        // remove sensitive information for students
        if (!authCheckService.isAtLeastTeachingAssistantForExercise(exercise)) {
            exercise.filterSensitiveInformation();
            result.setAssessor(null);
        }

        return ResponseEntity.ok(result);
    }

    /**
     * Save or submit manual assessment depending on the submit flag.
     *
     * @param submission the submission containing the assessment
     * @param feedbackList list of feedbacks
     * @param submit if true the assessment is submitted, else only saved
     * @param resultId resultId of the result we save the feedbackList to
     * @return result after saving/submitting modeling assessment
     */
    ResponseEntity<Result> saveAssessment(Submission submission, boolean submit, List<Feedback> feedbackList, Long resultId) {
        User user = userService.getUserWithGroupsAndAuthorities();
        StudentParticipation studentParticipation = (StudentParticipation) submission.getParticipation();
        long exerciseId = studentParticipation.getExercise().getId();
        Exercise exercise = exerciseService.findOne(exerciseId);
        checkAuthorization(exercise, user);

        final var isAtLeastInstructor = authCheckService.isAtLeastInstructorForExercise(exercise, user);
        if (!assessmentService.isAllowedToCreateOrOverrideResult(submission.getLatestResult(), exercise, studentParticipation, user, isAtLeastInstructor)) {
            log.debug("The user " + user.getLogin() + " is not allowed to override the assessment for the submission " + submission.getId());
            return forbidden("assessment", "assessmentSaveNotAllowed", "The user is not allowed to override the assessment");
        }

        Result result = assessmentService.saveManualAssessment(submission, feedbackList, resultId);
        if (submit) {
            result = assessmentService.submitManualAssessment(result.getId(), exercise, submission.getSubmissionDate());
        }
        // remove information about the student for tutors to ensure double-blind assessment
        if (!isAtLeastInstructor) {
            ((StudentParticipation) result.getParticipation()).filterSensitiveInformation();
        }
        if (submit && ((result.getParticipation()).getExercise().getAssessmentDueDate() == null
                || (result.getParticipation()).getExercise().getAssessmentDueDate().isBefore(ZonedDateTime.now()))) {
            messagingService.broadcastNewResult(result.getParticipation(), result);
        }
        return ResponseEntity.ok(result);
    }

    /**
     * @param submissionId id of the submission
     * @param feedbacks list of feedbacks
     * @return result after saving example assessment
     */
    ResponseEntity<Result> saveExampleAssessment(long submissionId, List<Feedback> feedbacks) {
        User user = userService.getUserWithGroupsAndAuthorities();
        ExampleSubmission exampleSubmission = exampleSubmissionService.findOneWithEagerResult(submissionId);
        Submission submission = exampleSubmission.getSubmission();
        Exercise exercise = exampleSubmission.getExercise();
        checkAuthorization(exercise, user);
        Result result = assessmentService.saveManualAssessment(submission, feedbacks);
        return ResponseEntity.ok(result);
    }

    /**
     * Retrieve the result for an example submission, only if the user is an instructor or if the example submission is not used for tutorial purposes.
     *
     * @param exerciseId   the id of the exercise
     * @param submissionId the id of the example submission
     * @return the result linked to the example submission
     */
    ResponseEntity<Result> getExampleAssessment(long exerciseId, long submissionId) {
        Exercise exercise = exerciseService.findOne(exerciseId);
        ExampleSubmission exampleSubmission = exampleSubmissionService.findOneBySubmissionId(submissionId);

        // It is allowed to get the example assessment, if the user is an instructor or
        // if the user is a tutor and the submission is not used for tutorial in the assessment dashboard
        boolean isAllowed = authCheckService.isAtLeastInstructorForExercise(exercise)
                || authCheckService.isAtLeastTeachingAssistantForExercise(exercise) && !exampleSubmission.isUsedForTutorial();
        if (!isAllowed) {
            forbidden();
        }

        return ResponseEntity.ok(assessmentService.getExampleAssessment(submissionId));
    }

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
            log.debug("Insufficient permission for course: " + exercise.getCourseViaExerciseGroupOrCourseMember().getTitle());
            throw new AccessForbiddenException("Insufficient permission for course: " + exercise.getCourseViaExerciseGroupOrCourseMember().getTitle());
        }
    }

    void validateExercise(Exercise exercise) throws BadRequestAlertException {
        Course course = exercise.getCourseViaExerciseGroupOrCourseMember();
        if (course == null) {
            throw new BadRequestAlertException("The course belonging to this exercise or its exercise group and exam does not exist", getEntityName(), "courseNotFound");
        }
    }

    protected ResponseEntity<Void> cancelAssessment(long submissionId) {
        log.debug("REST request to cancel assessment of submission: {}", submissionId);
        Submission submission = submissionService.findOneWithEagerResults(submissionId);
        if (submission.getLatestResult() == null) {
            // if there is no result everything is fine
            return ResponseEntity.ok().build();
        }
        User user = userService.getUserWithGroupsAndAuthorities();
        StudentParticipation studentParticipation = (StudentParticipation) submission.getParticipation();
        long exerciseId = studentParticipation.getExercise().getId();
        Exercise exercise = exerciseService.findOne(exerciseId);
        checkAuthorization(exercise, user);
        boolean isAtLeastInstructor = authCheckService.isAtLeastInstructorForExercise(exercise, user);
        if (!(isAtLeastInstructor || userService.getUser().getId().equals(submission.getLatestResult().getAssessor().getId()))) {
            // tutors cannot cancel the assessment of other tutors (only instructors can)
            return forbidden();
        }
        assessmentService.cancelAssessmentOfSubmission(submission);
        return ResponseEntity.ok().build();
    }
}
