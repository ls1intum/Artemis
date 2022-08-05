package de.tum.in.www1.artemis.web.rest;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.AssessmentService;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.WebsocketMessagingService;
import de.tum.in.www1.artemis.service.exam.ExamService;
import de.tum.in.www1.artemis.service.notifications.SingleUserNotificationService;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

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

    protected final SingleUserNotificationService singleUserNotificationService;

    public AssessmentResource(AuthorizationCheckService authCheckService, UserRepository userRepository, ExerciseRepository exerciseRepository, AssessmentService assessmentService,
            ResultRepository resultRepository, ExamService examService, WebsocketMessagingService messagingService, ExampleSubmissionRepository exampleSubmissionRepository,
            SubmissionRepository submissionRepository, SingleUserNotificationService singleUserNotificationService) {
        this.authCheckService = authCheckService;
        this.userRepository = userRepository;
        this.exerciseRepository = exerciseRepository;
        this.assessmentService = assessmentService;
        this.resultRepository = resultRepository;
        this.examService = examService;
        this.messagingService = messagingService;
        this.exampleSubmissionRepository = exampleSubmissionRepository;
        this.submissionRepository = submissionRepository;
        this.singleUserNotificationService = singleUserNotificationService;
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
            throw new EntityNotFoundException("Result with submission", submissionId);
        }

        if (!authCheckService.isUserAllowedToGetResult(exercise, participation, result)) {
            throw new AccessForbiddenException();
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
            throw new AccessForbiddenException("The user is not allowed to override the assessment");
        }

        Result result = assessmentService.saveManualAssessment(submission, feedbackList, resultId);
        if (submit) {
            result = assessmentService.submitManualAssessment(result.getId(), exercise, submission.getSubmissionDate());
            Optional<User> optionalStudent = ((StudentParticipation) submission.getParticipation()).getStudent();
            if (optionalStudent.isPresent()) {
                singleUserNotificationService.checkNotificationForAssessmentExerciseSubmission(exercise, optionalStudent.get(), result);
            }
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
            result = assessmentService.saveManualAssessment(submission, feedbacks, null);
        }
        else {
            result = assessmentService.saveManualAssessment(submission, feedbacks, submission.getLatestResult().getId());
        }
        result = resultRepository.submitResult(result, exercise, Optional.empty());
        return ResponseEntity.ok(result);
    }

    /**
     * Retrieve the result for an example submission, only if the user is an instructor or if the example submission is not used for tutorial purposes.
     *
     * @param exerciseId   the id of the exercise
     * @param submissionId the id of the example submission
     * @return the result linked to the example submission
     */
    protected ResponseEntity<Result> getExampleAssessment(long exerciseId, long submissionId) {
        Exercise exercise = exerciseRepository.findByIdElseThrow(exerciseId);
        final var exampleSubmission = exampleSubmissionRepository.findBySubmissionIdWithResultsElseThrow(submissionId);

        var user = userRepository.getUserWithGroupsAndAuthorities();
        var isAtLeastEditor = authCheckService.isAtLeastEditorForExercise(exercise, user);
        var isAtLeastTutor = authCheckService.isAtLeastTeachingAssistantForExercise(exercise, user);
        // It is allowed to get the example assessment, if the user is an instructor or
        // if the user is a tutor and the submission is not used for tutorial in the assessment dashboard
        // The reason is that example submissions with isTutorial = false should be shown immediately (with the assessment) to the tutor and
        // for example submission with isTutorial = true, the assessment should not be shown to the tutor. Instead, the tutor should try to assess it him/herself
        if (!(isAtLeastEditor || (isAtLeastTutor && !exampleSubmission.isUsedForTutorial()))) {
            throw new AccessForbiddenException();
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
            throw new AccessForbiddenException();
        }
        assessmentService.cancelAssessmentOfSubmission(submission);
        return ResponseEntity.ok().build();
    }

    protected ResponseEntity<Void> deleteAssessment(Long participationId, Long submissionId, Long resultId) {
        log.info("REST request by user: {} to delete result {}", userRepository.getUser().getLogin(), resultId);
        // check authentication
        Submission submission = submissionRepository.findByIdWithResultsElseThrow(submissionId);
        Result result = resultRepository.findByIdWithEagerFeedbacksElseThrow(resultId);
        Participation participation = submission.getParticipation();
        if (!participation.getId().equals(participationId)) {
            throw new BadRequestAlertException("participationId in path does not match the id of the participation to submission " + submissionId + "!", "Participation", "400");
        }
        Exercise exercise = exerciseRepository.findByIdElseThrow(participation.getExercise().getId());
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.INSTRUCTOR, exercise, null);

        if (!submission.getResults().contains(result)) {
            throw new BadRequestAlertException("The specified result does not belong to the submission.", "Result", "invalidResultId");
        }
        // delete assessment
        assessmentService.deleteAssessment(submission, result);

        return ResponseEntity.ok().build();
    }
}
