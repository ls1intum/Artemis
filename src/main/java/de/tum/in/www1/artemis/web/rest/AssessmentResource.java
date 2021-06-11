package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.*;

import java.time.ZonedDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.FeedbackType;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.AssessmentService;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.WebsocketMessagingService;
import de.tum.in.www1.artemis.service.exam.ExamService;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;

public abstract class AssessmentResource {

    private final Logger log = LoggerFactory.getLogger(AssessmentResource.class);

    protected final AuthorizationCheckService authCheckService;

    protected final UserRepository userRepository;

    protected final ExerciseRepository exerciseRepository;

    protected final AssessmentService assessmentService;

    protected final ResultRepository resultRepository;

    protected final ExamService examService;

    protected final WebsocketMessagingService messagingService;

    protected final ExampleSubmissionRepository exampleSubmissionRepository;

    protected final SubmissionRepository submissionRepository;


    public AssessmentResource(AuthorizationCheckService authCheckService, UserRepository userRepository, ExerciseRepository exerciseRepository, AssessmentService assessmentService,
            ResultRepository resultRepository, ExamService examService, WebsocketMessagingService messagingService, ExampleSubmissionRepository exampleSubmissionRepository,
            SubmissionRepository submissionRepository) {
        this.authCheckService = authCheckService;
        this.userRepository = userRepository;
        this.exerciseRepository = exerciseRepository;
        this.assessmentService = assessmentService;
        this.resultRepository = resultRepository;
        this.examService = examService;
        this.messagingService = messagingService;
        this.exampleSubmissionRepository = exampleSubmissionRepository;
        this.submissionRepository = submissionRepository;
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
        Submission submission = submissionRepository.findOneWithEagerResultAndFeedback(submissionId);
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
     * @param resultId resultId of the result we save the feedbackList to, null of no results exists yet
     * @return result after saving/submitting modeling assessment
     */
    ResponseEntity<Result> saveAssessment(Submission submission, boolean submit, List<Feedback> feedbackList, Long resultId) {
        User user = userRepository.getUserWithGroupsAndAuthorities();
        StudentParticipation studentParticipation = (StudentParticipation) submission.getParticipation();
        long exerciseId = studentParticipation.getExercise().getId();
        Exercise exercise = exerciseRepository.findByIdElseThrow(exerciseId);
        checkAuthorization(exercise, user);

        final var isAtLeastInstructor = authCheckService.isAtLeastInstructorForExercise(exercise, user);
        if (!assessmentService.isAllowedToCreateOrOverrideResult(submission.getLatestResult(), exercise, studentParticipation, user, isAtLeastInstructor)) {
            log.debug("The user {} is not allowed to override the assessment for the submission {}", user.getLogin(), submission.getId());
            return forbidden("assessment", "assessmentSaveNotAllowed", "The user is not allowed to override the assessment");
        }

        if (exercise instanceof ProgrammingExercise programmingExercise) {
            if (!programmingExercise.areManualResultsAllowed()) {
                return forbidden("assessment", "assessmentSaveNotAllowed", "Creating manual results is disabled for this exercise!");
            }
            // All not automatically generated result must have a detail text
            // TODO: put this check above
            else if (!feedbackList.isEmpty()
                    && feedbackList.stream().anyMatch(feedback -> feedback.getType() == FeedbackType.MANUAL_UNREFERENCED && feedback.getDetailText() == null)) {
                throw new BadRequestAlertException("In case tutor feedback is present, a feedback detail text is mandatory.", "programmingAssessment", "feedbackDetailTextNull");
            }
            else if (!feedbackList.isEmpty() && feedbackList.stream().anyMatch(feedback -> feedback.getCredits() == null)) {
                throw new BadRequestAlertException("In case feedback is present, a feedback must contain points.", "programmingAssessment", "feedbackCreditsNull");
            }
        }

        Result result = assessmentService.saveManualAssessment(submission, feedbackList, resultId, exercise);
        if (submit) {
            result = assessmentService.submitManualAssessment(result.getId());
        }
        var participation = result.getParticipation();
        // remove information about the student for tutors to ensure double-blind assessment
        if (!isAtLeastInstructor) {
            participation.filterSensitiveInformation();
        }

        if (submit && (participation.getExercise().getAssessmentDueDate() == null || participation.getExercise().getAssessmentDueDate().isBefore(ZonedDateTime.now()))) {
            messagingService.broadcastNewResult(result.getParticipation(), result);
        }
        return ResponseEntity.ok(result);
    }

    /**
     * @param exampleSubmissionId id of the example submission
     * @param feedbacks list of feedbacks
     * @return result after saving example assessment
     */
    protected ResponseEntity<Result> saveExampleAssessment(long exampleSubmissionId, List<Feedback> feedbacks) {
        User user = userRepository.getUserWithGroupsAndAuthorities();
        final var exampleSubmission = exampleSubmissionRepository.findByIdWithEagerResultAndFeedbackElseThrow(exampleSubmissionId);
        Submission submission = exampleSubmission.getSubmission();
        Exercise exercise = exampleSubmission.getExercise();
        checkAuthorization(exercise, user);
        // as parameter resultId is not set, we use the latest Result, if no latest Result exists, we use null
        Result result;
        if (submission.getLatestResult() == null) {
            result = assessmentService.saveManualAssessment(submission, feedbacks, null, exercise);
        }
        else {
            result = assessmentService.saveManualAssessment(submission, feedbacks, submission.getLatestResult().getId(), exercise);
        }
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
        Exercise exercise = exerciseRepository.findByIdElseThrow(exerciseId);
        final var exampleSubmission = exampleSubmissionRepository.findBySubmissionIdWithResultsElseThrow(submissionId);

        // It is allowed to get the example assessment, if the user is an instructor or
        // if the user is a tutor and the submission is not used for tutorial in the assessment dashboard
        boolean isAllowed = authCheckService.isAtLeastInstructorForExercise(exercise)
                || authCheckService.isAtLeastTeachingAssistantForExercise(exercise) && !Boolean.TRUE.equals(exampleSubmission.isUsedForTutorial());
        if (!isAllowed) {
            forbidden();
        }

        return ResponseEntity.ok(assessmentService.getExampleAssessment(submissionId));
    }

    /**
     * checks that the given user has at least tutor rights for the given exercise
     *
     * @param exercise the exercise for which the authorization should be checked
     * @throws BadRequestAlertException if no course is associated to the given exercise
     */
    void checkAuthorization(Exercise exercise, User user) throws BadRequestAlertException {
        validateExercise(exercise);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.TEACHING_ASSISTANT, exercise, user);
    }

    void validateExercise(Exercise exercise) throws BadRequestAlertException {
        Course course = exercise.getCourseViaExerciseGroupOrCourseMember();
        if (course == null) {
            throw new BadRequestAlertException("The course belonging to this exercise or its exercise group and exam does not exist", getEntityName(), "courseNotFound");
        }
    }

    protected ResponseEntity<Void> cancelAssessment(long submissionId) { // TODO: Add correction round !
        log.debug("REST request to cancel assessment of submission: {}", submissionId);
        Submission submission = submissionRepository.findByIdWithResultsElseThrow(submissionId);
        if (submission.getLatestResult() == null) {
            // if there is no result everything is fine
            return ResponseEntity.ok().build();
        }
        User user = userRepository.getUserWithGroupsAndAuthorities();
        StudentParticipation studentParticipation = (StudentParticipation) submission.getParticipation();
        long exerciseId = studentParticipation.getExercise().getId();
        Exercise exercise = exerciseRepository.findByIdElseThrow(exerciseId);
        checkAuthorization(exercise, user);
        boolean isAtLeastInstructor = authCheckService.isAtLeastInstructorForExercise(exercise, user);
        if (!(isAtLeastInstructor || userRepository.getUser().getId().equals(submission.getLatestResult().getAssessor().getId()))) {
            // tutors cannot cancel the assessment of other tutors (only instructors can)
            return forbidden();
        }
        assessmentService.cancelAssessmentOfSubmission(submission);
        return ResponseEntity.ok().build();
    }

    protected ResponseEntity<Void> deleteAssessment(Long submissionId, Long resultId) {
        log.info("REST request by user: {} to delete result {}", userRepository.getUser().getLogin(), resultId);
        // check authentication
        Submission submission = submissionRepository.findByIdWithResultsElseThrow(submissionId);
        Result result = resultRepository.findByIdWithEagerFeedbacksElseThrow(resultId);
        StudentParticipation studentParticipation = (StudentParticipation) submission.getParticipation();
        Exercise exercise = exerciseRepository.findByIdElseThrow(studentParticipation.getExercise().getId());
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.INSTRUCTOR, exercise, null);

        if (!submission.getResults().contains(result)) {
            throw new BadRequestAlertException("The specified result does not belong to the submission.", "Result", "invalidResultId");
        }
        // delete assessment
        assessmentService.deleteAssessment(submission, result);

        return ok();
    }
}
