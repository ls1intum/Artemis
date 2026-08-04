package de.tum.cit.aet.artemis.assessment.web;

import java.util.List;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.assessment.domain.Feedback;
import de.tum.cit.aet.artemis.assessment.domain.FeedbackType;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.repository.ExampleSubmissionRepository;
import de.tum.cit.aet.artemis.assessment.repository.ResultRepository;
import de.tum.cit.aet.artemis.assessment.service.AssessmentService;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participation;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.exercise.repository.SubmissionRepository;

public abstract class AssessmentResource {

    private static final Logger log = LoggerFactory.getLogger(AssessmentResource.class);

    protected final AuthorizationCheckService authCheckService;

    protected final UserRepository userRepository;

    protected final ExerciseRepository exerciseRepository;

    protected final AssessmentService assessmentService;

    protected final ResultRepository resultRepository;

    protected final ExampleSubmissionRepository exampleSubmissionRepository;

    protected final SubmissionRepository submissionRepository;

    public AssessmentResource(AuthorizationCheckService authCheckService, UserRepository userRepository, ExerciseRepository exerciseRepository, AssessmentService assessmentService,
            ResultRepository resultRepository, ExampleSubmissionRepository exampleSubmissionRepository, SubmissionRepository submissionRepository) {
        this.authCheckService = authCheckService;
        this.userRepository = userRepository;
        this.exerciseRepository = exerciseRepository;
        this.assessmentService = assessmentService;
        this.resultRepository = resultRepository;
        this.exampleSubmissionRepository = exampleSubmissionRepository;
        this.submissionRepository = submissionRepository;
    }

    protected abstract String getEntityName();

    /**
     * Get the result of the submission with the given id. Returns a 403 Forbidden response if the user is not allowed to retrieve the assessment. The user is not allowed
     * to retrieve the assessment if they are not a student of the corresponding course, the submission is not their submission, the result is not finished or the assessment due
     * date of the corresponding exercise is in the future (or not set).
     *
     * @param submissionId the id of the submission that should be sent to the client
     * @return the assessment of the given id
     */
    public ResponseEntity<Result> getAssessmentBySubmissionId(Long submissionId) {
        log.debug("REST request to get assessment for submission with id {}", submissionId);
        Submission submission = submissionRepository.findOneWithEagerResultAndFeedbackAndAssessmentNoteAndTeamStudents(submissionId);
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
            result.filterSensitiveInformation();
        }

        return ResponseEntity.ok(result);
    }

    /**
     * Save or submit manual assessment depending on the submit flag.
     *
     * @param submission     the submission containing the assessment
     * @param feedbackList   list of feedbacks
     * @param submit         if true the assessment is submitted, else only saved
     * @param resultId       resultId of the result we save the feedbackList to, null of no results exists yet
     * @param assessmentNote the assessment note of the result
     * @return result after saving/submitting modeling assessment
     */
    public ResponseEntity<Result> saveAssessment(Submission submission, boolean submit, List<Feedback> feedbackList, Long resultId, String assessmentNote) {
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

        Result result = assessmentService.saveAndSubmitManualAssessment(exercise, submission, feedbackList, resultId, assessmentNote, submit);

        var participation = result.getSubmission().getParticipation();
        // remove information about the student for tutors to ensure double-blind assessment
        if (!isAtLeastInstructor) {
            participation.filterSensitiveInformation();
        }
        return ResponseEntity.ok(result);
    }

    /**
     * @param exampleSubmissionId id of the example submission
     * @param feedbacks           list of feedbacks
     * @return result after saving example assessment
     */
    protected Result saveExampleAssessment(long exampleSubmissionId, List<Feedback> feedbacks) {
        User user = userRepository.getUserWithGroupsAndAuthorities();
        final var exampleSubmission = exampleSubmissionRepository.findByIdWithEagerResultAndFeedbackElseThrow(exampleSubmissionId);
        Submission submission = exampleSubmission.getSubmission();
        Exercise exercise = exampleSubmission.getExercise();
        checkAuthorization(exercise, user);
        // as parameter resultId is not set, we use the latest Result, if no latest Result exists, we use null
        Result result;
        if (submission.getLatestResult() == null) {
            result = assessmentService.saveManualAssessment(submission, feedbacks, null, null, exercise.getId());
        }
        else {
            result = assessmentService.saveManualAssessment(submission, feedbacks, submission.getLatestResult().getId(), null, exercise.getId());
        }
        return resultRepository.submitResult(result, exercise);
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
        // It is allowed to get the example assessment, if the user is at least an editor or
        // if the user is a tutor and the submission is not used for tutorial in the assessment dashboard
        // The reason is that example submissions with isTutorial = false should be shown immediately (with the assessment) to the tutor and
        // for example submission with isTutorial = true, the assessment should not be shown to the tutor. Instead, the tutor should try to assess it themselves
        // Therefore we send a result with only the references included, which is needed to tell the tutor which elements they missed to assess
        Result result = assessmentService.getExampleAssessment(submissionId);

        if (result != null && !(isAtLeastEditor || (isAtLeastTutor && !exampleSubmission.isUsedForTutorial()))) {
            Result freshResult = new Result();
            freshResult.setId(result.getId());
            if (result.getFeedbacks() != null) {
                result.getFeedbacks().stream().filter(feedback -> !FeedbackType.MANUAL_UNREFERENCED.equals(feedback.getType()) && StringUtils.hasText(feedback.getReference()))
                        .forEach(feedback -> {
                            Feedback freshFeedback = new Feedback();
                            freshFeedback.setId(feedback.getId());
                            freshResult.addFeedback(freshFeedback.reference(feedback.getReference()).type(feedback.getType()));
                        });
            }
            result = freshResult;
        }

        return ResponseEntity.ok(result);
    }

    /**
     * checks that the given user has at least tutor rights for the given exercise
     *
     * @param exercise the exercise for which the authorization should be checked
     * @throws BadRequestAlertException if no course is associated to the given exercise
     */
    protected void checkAuthorization(Exercise exercise, User user) throws BadRequestAlertException {
        validateExercise(exercise);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.TEACHING_ASSISTANT, exercise, user);
    }

    void validateExercise(Exercise exercise) throws BadRequestAlertException {
        Course course = exercise.getCourseViaExerciseGroupOrCourseMember();
        if (course == null) {
            throw new BadRequestAlertException("The course belonging to this exercise or its exercise group and exam does not exist", getEntityName(), "courseNotFound");
        }
    }

    protected ResponseEntity<Void> cancelAssessment(long submissionId, @Nullable Long resultId) {
        log.debug("REST request to cancel assessment of submission {} and result {}", submissionId, resultId);
        Submission submission = submissionRepository.findByIdWithResultsElseThrow(submissionId);
        // Release the result the caller named. A submission can hold one result per correction round, and the caller is
        // the only one that knows which round its button belongs to: resolving it here always released the newest round,
        // so "cancel assessment of correction round 1" released round 2 instead and round 1 stayed locked (#13396).
        Result resultToCancel;
        if (resultId != null) {
            resultToCancel = submission.getManualResultsById(resultId);
            if (resultToCancel == null) {
                throw new BadRequestAlertException("The result does not belong to this submission or is not a manual assessment", "result", "resultNotFound");
            }
            // Cancelling releases a draft assessment, so a finished correction round is not a valid target here.
            //
            // The check is deliberately scoped to this branch. Its job is to make sure the new parameter cannot reach a
            // result that was unreachable before: without a result id only the newest manual result is ever released,
            // so naming an id is the only way to reach an older, already submitted correction round. Releasing the
            // newest manual result even when it carries a completion date is long-standing behaviour that
            // cancelOwnAssessmentAsTutor and cancelAssessmentOfOtherTutorAsInstructor both assert, and changing it is a
            // product decision that does not belong in a hotfix.
            if (resultToCancel.getCompletionDate() != null) {
                throw new BadRequestAlertException("The assessment of this correction round is already submitted and cannot be cancelled", "result", "resultAlreadySubmitted");
            }
        }
        else {
            // Without a result id the newest correction round is released, which is what every caller got before the
            // parameter existed.
            //
            // Deliberately the newest *manual* result rather than the highest id. In a normal lifecycle those are the
            // same, because an Athena result is only created by a student's preliminary feedback request and never by
            // the tutor-facing feedback suggestions, so it cannot follow a manual assessment. It can differ in an
            // exercise that allows preliminary feedback requests with no due date, where a student may request AI
            // feedback after a tutor has already assessed. Resolving by highest id would then pick a result with no
            // assessor and the authorization check below would dereference null.
            resultToCancel = submission.getLatestManualResult();
        }
        // The permission decision falls back to the latest result: a submission whose only result is automatic has nothing
        // to cancel, but another tutor still has to be rejected rather than silently told everything is fine.
        Result resultForAuthorization = resultToCancel != null ? resultToCancel : submission.getLatestResult();
        if (resultForAuthorization == null) {
            // if there is no result everything is fine
            return ResponseEntity.ok().build();
        }
        User user = userRepository.getUserWithGroupsAndAuthorities();
        StudentParticipation studentParticipation = (StudentParticipation) submission.getParticipation();
        long exerciseId = studentParticipation.getExercise().getId();
        Exercise exercise = exerciseRepository.findByIdElseThrow(exerciseId);
        checkAuthorization(exercise, user);
        boolean isAtLeastInstructor = authCheckService.isAtLeastInstructorForExercise(exercise, user);
        boolean isAssessorOfResult = resultForAuthorization.getAssessor() != null && user.getId().equals(resultForAuthorization.getAssessor().getId());
        if (!(isAtLeastInstructor || isAssessorOfResult)) {
            // tutors cannot cancel the assessment of other tutors (only instructors can)
            throw new AccessForbiddenException();
        }
        assessmentService.cancelAssessmentOfSubmission(submission, resultToCancel);
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
