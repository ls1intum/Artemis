package de.tum.in.www1.artemis.web.rest;

import static java.util.stream.Collectors.toSet;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.security.jwt.AtheneTrackingTokenProvider;
import de.tum.in.www1.artemis.service.*;
import de.tum.in.www1.artemis.service.exam.ExamService;
import de.tum.in.www1.artemis.service.notifications.SingleUserNotificationService;
import de.tum.in.www1.artemis.web.rest.dto.TextAssessmentDTO;
import de.tum.in.www1.artemis.web.rest.dto.TextAssessmentUpdateDTO;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.errors.ErrorConstants;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

/**
 * REST controller for managing TextAssessment.
 */
@RestController
@RequestMapping("/api")
public class TextAssessmentResource extends AssessmentResource {

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private static final String ENTITY_NAME = "textAssessment";

    private final Logger log = LoggerFactory.getLogger(TextAssessmentResource.class);

    private final TextBlockService textBlockService;

    private final TextAssessmentService textAssessmentService;

    private final TextExerciseRepository textExerciseRepository;

    private final TextSubmissionService textSubmissionService;

    private final TextSubmissionRepository textSubmissionRepository;

    private final ExampleSubmissionRepository exampleSubmissionRepository;

    private final Optional<AtheneTrackingTokenProvider> atheneTrackingTokenProvider;

    private final Optional<AutomaticTextAssessmentConflictService> automaticTextAssessmentConflictService;

    private final GradingCriterionRepository gradingCriterionRepository;

    private final FeedbackRepository feedbackRepository;

    private final FeedbackConflictRepository feedbackConflictRepository;

    private final ResultService resultService;

    public TextAssessmentResource(AuthorizationCheckService authCheckService, TextAssessmentService textAssessmentService, TextBlockService textBlockService,
            TextExerciseRepository textExerciseRepository, TextSubmissionRepository textSubmissionRepository, UserRepository userRepository,
            TextSubmissionService textSubmissionService, WebsocketMessagingService messagingService, ExerciseRepository exerciseRepository, ResultRepository resultRepository,
            GradingCriterionRepository gradingCriterionRepository, Optional<AtheneTrackingTokenProvider> atheneTrackingTokenProvider, ExamService examService,
            Optional<AutomaticTextAssessmentConflictService> automaticTextAssessmentConflictService, FeedbackConflictRepository feedbackConflictRepository,
            ExampleSubmissionRepository exampleSubmissionRepository, SubmissionRepository submissionRepository, FeedbackRepository feedbackRepository,
            SingleUserNotificationService singleUserNotificationService, ResultService resultService) {
        super(authCheckService, userRepository, exerciseRepository, textAssessmentService, resultRepository, examService, messagingService, exampleSubmissionRepository,
                submissionRepository, singleUserNotificationService);

        this.textAssessmentService = textAssessmentService;
        this.textBlockService = textBlockService;
        this.textExerciseRepository = textExerciseRepository;
        this.textSubmissionRepository = textSubmissionRepository;
        this.textSubmissionService = textSubmissionService;
        this.gradingCriterionRepository = gradingCriterionRepository;
        this.atheneTrackingTokenProvider = atheneTrackingTokenProvider;
        this.automaticTextAssessmentConflictService = automaticTextAssessmentConflictService;
        this.feedbackConflictRepository = feedbackConflictRepository;
        this.feedbackRepository = feedbackRepository;
        this.exampleSubmissionRepository = exampleSubmissionRepository;
        this.resultService = resultService;
    }

    /**
     * PUT participations/:participationId/results/:resultId/text-assessment : Saves a given manual textAssessment
     *
     * @param participationId the participationId of the participation the result belongs to
     * @param resultId the resultId the assessment belongs to
     * @param textAssessment the assessments
     * @return 200 Ok if successful with the corresponding result as body, but sensitive information are filtered out
     */
    @PutMapping("participations/{participationId}/results/{resultId}/text-assessment")
    @PreAuthorize("hasRole('TA')")
    public ResponseEntity<Result> saveTextAssessment(@PathVariable Long participationId, @PathVariable Long resultId, @RequestBody TextAssessmentDTO textAssessment) {
        final boolean hasAssessmentWithTooLongReference = textAssessment.getFeedbacks() != null
                && textAssessment.getFeedbacks().stream().filter(Feedback::hasReference).anyMatch(feedback -> feedback.getReference().length() > Feedback.MAX_REFERENCE_LENGTH);
        if (hasAssessmentWithTooLongReference) {
            throw new BadRequestAlertException("Please select a text block shorter than " + Feedback.MAX_REFERENCE_LENGTH + " characters.", "feedbackList",
                    "feedbackReferenceTooLong");
        }
        Result result = resultRepository.findByIdElseThrow(resultId);
        if (!result.getParticipation().getId().equals(participationId)) {
            throw new BadRequestAlertException("participationId in Result of resultId " + resultId + " doesn't match the paths participationId!", "feedbackList",
                    "participationIdMismatch");
        }
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.TEACHING_ASSISTANT, result.getParticipation().getExercise(), null);
        final var textSubmission = textSubmissionRepository.getTextSubmissionWithResultAndTextBlocksAndFeedbackByResultIdElseThrow(resultId);
        ResponseEntity<Result> response = super.saveAssessment(textSubmission, false, textAssessment.getFeedbacks(), resultId);

        if (response.getStatusCode().is2xxSuccessful()) {
            saveTextBlocks(textAssessment.getTextBlocks(), textSubmission, (TextExercise) result.getParticipation().getExercise(), response.getBody().getFeedbacks());
        }

        return response;
    }

    /**
     * PUT exercises/:exerciseId/example-submissions/:exampleSubmissionId/example-text-assessment : save manual example text assessment
     *
     * @param exerciseId the id of the exercise the exampleSubmission belongs to
     * @param exampleSubmissionId id of the submission
     * @param textAssessment list of text assessments (consists of feedbacks and text blocks)
     * @return result after saving example text assessment
     */
    @ResponseStatus(HttpStatus.OK)
    @ApiResponses({ @ApiResponse(code = 403, message = ErrorConstants.REQ_403_REASON), @ApiResponse(code = 404, message = ErrorConstants.REQ_404_REASON) })
    @PutMapping("exercises/{exerciseId}/example-submissions/{exampleSubmissionId}/example-text-assessment")
    @PreAuthorize("hasRole('TA')")
    public ResponseEntity<Result> saveTextExampleAssessment(@PathVariable long exerciseId, @PathVariable long exampleSubmissionId, @RequestBody TextAssessmentDTO textAssessment) {
        log.debug("REST request to save text example assessment : {}", exampleSubmissionId);
        Optional<ExampleSubmission> optionalExampleSubmission = exampleSubmissionRepository.findBySubmissionId(exampleSubmissionId);
        if (optionalExampleSubmission.isPresent()) {
            ExampleSubmission exampleSubmission = optionalExampleSubmission.get();
            if (!exampleSubmission.getExercise().getId().equals(exerciseId)) {
                throw new BadRequestAlertException("exerciseId in ExampleSubmission of exampleSubmissionId " + exampleSubmissionId + " doesn't match the paths exerciseId!",
                        "exerciseId", "exerciseIdMismatch");
            }
            authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, exampleSubmission.getExercise(), null);
        }
        else {
            TextExercise textExercise = textExerciseRepository.findByIdElseThrow(exerciseId);
            authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, textExercise, null);
        }
        final var response = super.saveExampleAssessment(exampleSubmissionId, textAssessment.getFeedbacks());
        if (response.getStatusCode().is2xxSuccessful()) {
            final Submission submission = response.getBody().getSubmission();
            final var textSubmission = textSubmissionService.findOneWithEagerResultFeedbackAndTextBlocks(submission.getId());
            saveTextBlocks(textAssessment.getTextBlocks(), textSubmission, textExerciseRepository.findByIdElseThrow(exerciseId), response.getBody().getFeedbacks());
        }
        return response;
    }

    /**
     * DELETE exercises/:exerciseId/example-submissions/:exampleSubmissionId/example-assessment/feedback : delete feedback for example submission.
     *
     * @param exerciseId the id of the exercise the exampleSubmission belongs to
     * @param exampleSubmissionId id of the submission
     * @return 204 No Content
     */
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("exercises/{exerciseId}/example-submissions/{exampleSubmissionId}/example-text-assessment/feedback")
    @PreAuthorize("hasRole('TA')")
    public ResponseEntity<Void> deleteTextExampleAssessment(@PathVariable long exerciseId, @PathVariable long exampleSubmissionId) {
        log.debug("REST request to delete text example assessment : {}", exampleSubmissionId);
        User user = userRepository.getUserWithGroupsAndAuthorities();
        final var exampleSubmission = exampleSubmissionRepository.findByIdWithEagerResultAndFeedbackElseThrow(exampleSubmissionId);
        Submission submission = exampleSubmission.getSubmission();
        Exercise exercise = exampleSubmission.getExercise();
        checkAuthorization(exercise, user);
        if (!exercise.getId().equals(exerciseId)) {
            throw new BadRequestAlertException("exerciseId in ExampleSubmission of exampleSubmissionId " + exampleSubmissionId + " doesn't match the paths exerciseId!",
                    "exerciseId", "exerciseIdMismatch");
        }

        if (!(submission instanceof TextSubmission)) {
            return ResponseEntity.badRequest().build();
        }

        // 1. Delete Text blocks
        textBlockService.deleteForSubmission((TextSubmission) submission);

        // 2. Delete feedback and example assessment
        final var latestResult = submission.getLatestResult();
        if (latestResult != null) {
            latestResult.getFeedbacks().clear();
            resultService.deleteResult(latestResult, true);
            submission.setResults(List.of());
            submissionRepository.save(submission);
        }

        return ResponseEntity.noContent().build();
    }

    /**
     *
     * POST participations/:participationId/results/:resultId/submit-text-assessment : Submits manual textAssessments for a given result
     *     and notify the user if it's before the Assessment Due Date
     *
     * @param participationId the participationId of the participation whose assessment shall be saved
     * @param resultId the resultId the assessment belongs to
     * @param textAssessment the assessments which should be submitted
     * @return 200 Ok if successful with the corresponding result as a body, but sensitive information are filtered out
     */
    @PostMapping("participations/{participationId}/results/{resultId}/submit-text-assessment")
    @PreAuthorize("hasRole('TA')")
    public ResponseEntity<Result> submitTextAssessment(@PathVariable Long participationId, @PathVariable Long resultId, @RequestBody TextAssessmentDTO textAssessment) {
        final boolean hasAssessmentWithTooLongReference = textAssessment.getFeedbacks().stream().filter(Feedback::hasReference)
                .anyMatch(feedback -> feedback.getReference().length() > Feedback.MAX_REFERENCE_LENGTH);
        if (hasAssessmentWithTooLongReference) {
            throw new BadRequestAlertException("Please select a text block shorter than " + Feedback.MAX_REFERENCE_LENGTH + " characters.", "feedbackList",
                    "feedbackReferenceTooLong");
        }
        Result result = resultRepository.findByIdElseThrow(resultId);
        if (!(result.getParticipation().getExercise() instanceof final TextExercise exercise)) {
            throw new BadRequestAlertException("This exercise isn't a TextExercise!", "Exercise", "wrongExerciseType");
        }
        else if (!result.getParticipation().getId().equals(participationId)) {
            throw new BadRequestAlertException("participationId in Result of resultId " + resultId + " doesn't match the paths participationId!", "participationId", "participationIdMismatch");
        }
        checkAuthorization(exercise, null);
        final TextSubmission textSubmission = textSubmissionRepository.getTextSubmissionWithResultAndTextBlocksAndFeedbackByResultIdElseThrow(resultId);
        ResponseEntity<Result> response = super.saveAssessment(textSubmission, true, textAssessment.getFeedbacks(), resultId);

        if (response.getStatusCode().is2xxSuccessful()) {
            saveTextBlocks(textAssessment.getTextBlocks(), textSubmission, exercise, response.getBody().getFeedbacks());

            // call feedback conflict service
            if (exercise.isAutomaticAssessmentEnabled() && automaticTextAssessmentConflictService.isPresent()) {
                this.automaticTextAssessmentConflictService.get().asyncCheckFeedbackConsistency(textAssessment.getTextBlocks(), textSubmission.getLatestResult().getFeedbacks(),
                        exercise.getId());
            }
        }

        return response;
    }

    /**
     * PUT participations/:participationId/submissions/:submissionId/text-assessment-after-complaint : Update an assessment after a complaint was accepted.
     *
     * @param participationId  the id of the participation the submission belongs to
     * @param submissionId     the id of the submission for which the assessment should be updated
     * @param assessmentUpdate the assessment update containing the new feedback items and the response to the complaint
     * @return the updated result
     */
    @ResponseStatus(HttpStatus.OK)
    @PutMapping("participations/{participationId}/submissions/{submissionId}/text-assessment-after-complaint")
    @PreAuthorize("hasRole('TA')")
    public ResponseEntity<Result> updateTextAssessmentAfterComplaint(@PathVariable Long participationId, @PathVariable Long submissionId,
            @RequestBody TextAssessmentUpdateDTO assessmentUpdate) {
        log.debug("REST request to update the assessment of submission {} after complaint.", submissionId);
        User user = userRepository.getUserWithGroupsAndAuthorities();
        TextSubmission textSubmission = textSubmissionService.findOneWithEagerResultFeedbackAndTextBlocks(submissionId);
        StudentParticipation studentParticipation = (StudentParticipation) textSubmission.getParticipation();
        if (!studentParticipation.getId().equals(participationId)) {
            throw new BadRequestAlertException("participationId in Submission of submissionId " + submissionId + " doesn't match the paths participationId!", "participationId",
                    "participationIdMismatch");
        }
        long exerciseId = studentParticipation.getExercise().getId();
        TextExercise textExercise = textExerciseRepository.findByIdElseThrow(exerciseId);
        checkAuthorization(textExercise, user);
        Result result = textAssessmentService.updateAssessmentAfterComplaint(textSubmission.getLatestResult(), textExercise, assessmentUpdate);
        saveTextBlocks(assessmentUpdate.getTextBlocks(), textSubmission, textExercise, result.getFeedbacks());

        if (result.getParticipation() != null && result.getParticipation() instanceof StudentParticipation && !authCheckService.isAtLeastInstructorForExercise(textExercise)) {
            ((StudentParticipation) result.getParticipation()).setParticipant(null);
        }

        return ResponseEntity.ok(result);
    }

    /**
     * POST participations/:participationId/submissions/:submissionId/cancel-assessment : Cancel an assessment of a given submission for the current user, i.e. delete the corresponding result / release the lock. Then the submission is available for assessment
     * again.
     *
     * @param submissionId the id of the submission for which the current assessment should be canceled
     * @param participationId the participationId of the participation for which the assessment should get canceled
     * @return 200 Ok response if canceling was successful, 403 Forbidden if current user is not the assessor of the submission
     */
    @PostMapping("participations/{participationId}/submissions/{submissionId}/cancel-assessment")
    @PreAuthorize("hasRole('TA')")
    public ResponseEntity<Void> cancelAssessment(@PathVariable Long participationId, @PathVariable Long submissionId) {
        Submission submission = submissionRepository.findByIdWithResultsElseThrow(submissionId);
        if (!submission.getParticipation().getId().equals(participationId)) {
            throw new BadRequestAlertException("participationId in Submission of submissionId " + submissionId + " doesn't match the paths participationId!", "participationId",
                    "participationIdMismatch");
        }
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.TEACHING_ASSISTANT, submission.getParticipation().getExercise(), null);
        return super.cancelAssessment(submissionId);
    }

    /**
     * Delete an assessment of a given submission.
     *
     * @param participationId - the id of the participation to the submission
     * @param submissionId - the id of the submission for which the current assessment should be deleted
     * @param resultId     - the id of the result which should get deleted
     * @return 200 Ok response if canceling was successful, 403 Forbidden if current user is not an instructor of the course or an admin
     */
    @DeleteMapping("participations/{participationId}/text-submissions/{submissionId}/results/{resultId}")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<Void> deleteAssessment(@PathVariable Long participationId, @PathVariable Long submissionId, @PathVariable Long resultId) {
        return super.deleteAssessment(participationId, submissionId, resultId);
    }

    /**
     * GET participations/:participationId/submissions/:submissionId/for-text-assessment
     * Given an exerciseId and a submissionId, the method retrieves from the database all the data needed by the tutor to assess the submission. If the tutor has already started
     * assessing the submission, then we also return all the results the tutor has already inserted. If another tutor has already started working on this submission, the system
     * returns an error
     * In case an instructors calls, the resultId is used first. In case the resultId is not set, the correctionRound is used.
     * In case neither resultId nor correctionRound are set, the first correctionRound is used.
     *
     * @param participationId the id of the participation the submissions belongs to
     * @param submissionId the id of the submission we want
     * @param correctionRound correction round for which we want the submission
     * @param resultId if result already exists, we want to get the submission for this specific result
     * @return a Participation of the tutor in the submission
     */
    @GetMapping("participations/{participationId}/submissions/{submissionId}/for-text-assessment")
    @PreAuthorize("hasRole('TA')")
    public ResponseEntity<Participation> retrieveParticipationForSubmission(@PathVariable Long participationId, @PathVariable Long submissionId,
            @RequestParam(value = "correction-round", defaultValue = "0") int correctionRound, @RequestParam(value = "resultId", required = false) Long resultId) {

        log.debug("REST request to get data for tutors text assessment submission: {}", submissionId);
        final var textSubmission = textSubmissionRepository.findByIdWithParticipationExerciseResultAssessorElseThrow(submissionId);
        final Participation participation = textSubmission.getParticipation();
        if (!participation.getId().equals(participationId)) {
            throw new BadRequestAlertException("participationId in Submission of submissionId " + submissionId + " doesn't match the paths participationId!", "participationId",
                    "participationIdMismatch");
        }
        final var exercise = participation.getExercise();
        final User user = userRepository.getUserWithGroupsAndAuthorities();
        checkAuthorization(exercise, user);
        final boolean isAtLeastInstructorForExercise = authCheckService.isAtLeastInstructorForExercise(exercise, user);

        // return forbidden if caller is not allowed to assess
        authCheckService.checkIsAllowedToAssessExerciseElseThrow(exercise, user, resultId);

        Result result;
        if (resultId != null) {
            // in case resultId is set we get result by id
            result = textSubmission.getManualResultsById(resultId);

            if (result == null) {
                return ResponseEntity.badRequest()
                        .headers(HeaderUtil.createFailureAlert(applicationName, true, "TextSubmission", "ResultNotFound", "No Result was found for the given ID.")).body(null);
            }
        }
        else {
            // in case no resultId is set we get result by correctionRound
            result = textSubmission.getResultForCorrectionRound(correctionRound);

            if (result != null && !isAtLeastInstructorForExercise && result.getAssessor() != null && !result.getAssessor().getLogin().equals(user.getLogin())
                    && result.getCompletionDate() == null) {
                // If we already have a result, we need to check if it is locked.
                return ResponseEntity.status(HttpStatus.LOCKED)
                        .headers(HeaderUtil.createFailureAlert(applicationName, true, ENTITY_NAME, "alreadyAssessed", "This submission is being assessed by another tutor"))
                        .build();
            }

            textSubmissionService.lockTextSubmissionToBeAssessed(textSubmission, correctionRound);
            // set it since it has changed
            result = textSubmission.getResultForCorrectionRound(correctionRound);
        }

        // prepare and load in all feedbacks
        textAssessmentService.prepareSubmissionForAssessment(textSubmission, result);

        List<GradingCriterion> gradingCriteria = gradingCriterionRepository.findByExerciseIdWithEagerGradingCriteria(exercise.getId());
        exercise.setGradingCriteria(gradingCriteria);
        // Remove sensitive information of submission depending on user
        textSubmissionService.hideDetails(textSubmission, user);

        // Prepare for Response: Set Submissions and Results of Participation to include requested only.
        participation.setSubmissions(Set.of(textSubmission));
        textSubmission.getResults().forEach(r -> r.setSubmission(null));

        // set result again as it was changed
        if (resultId != null) {
            result = textSubmission.getManualResultsById(resultId);
            textSubmission.setResults(Collections.singletonList(result));
        }
        else {
            result = textSubmission.getResultForCorrectionRound(correctionRound);
        }

        textSubmission.removeNotNeededResults(correctionRound, resultId);
        participation.setResults(Set.copyOf(textSubmission.getResults()));

        final ResponseEntity.BodyBuilder bodyBuilder = ResponseEntity.ok();
        final Result finalResult = result;

        // Add the jwt token as a header to the response for tutor-assessment tracking to the request if the athene profile is set
        this.atheneTrackingTokenProvider.ifPresent(atheneTrackingTokenProvider -> atheneTrackingTokenProvider.addTokenToResponseEntity(bodyBuilder, finalResult));
        return bodyBuilder.body(participation);
    }

    /**
     * GET exercise/:exerciseId/submission/:submissionId/example-result : Retrieve the result of an example assessment, only if the user is an instructor or if the submission is used for tutorial purposes.
     *
     * @param exerciseId   the id of the exercise
     * @param submissionId the id of the submission which must be connected to an example submission
     * @return the example result linked to the submission
     */
    @GetMapping("/exercises/{exerciseId}/submissions/{submissionId}/example-result")
    @PreAuthorize("hasRole('TA')")
    public ResponseEntity<Result> getExampleResultForTutor(@PathVariable long exerciseId, @PathVariable long submissionId) {
        User user = userRepository.getUserWithGroupsAndAuthorities();
        log.debug("REST request to get example assessment for tutors text assessment: {}", submissionId);
        final ExampleSubmission exampleSubmission = exampleSubmissionRepository.findBySubmissionIdWithResultsElseThrow(submissionId);
        final var textExercise = exampleSubmission.getExercise();
        if (!textExercise.getId().equals(exerciseId)) {
            throw new BadRequestAlertException("Exercise to submission with submissionId " + submissionId + " doesn't have the exerciseId " + exerciseId + " !", "exerciseId", "exerciseIdMismatch");
        }

        // If the user is not at least a tutor for this exercise, return error
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.TEACHING_ASSISTANT, textExercise, user);

        Submission submission = exampleSubmission.getSubmission();

        if (!(submission instanceof final TextSubmission textSubmission)) {
            return ResponseEntity.badRequest().body(null);
        }

        final var textBlocks = textBlockService.findAllBySubmissionId(textSubmission.getId());
        textSubmission.setBlocks(textBlocks);
        if (textSubmission.getBlocks() == null || textSubmission.getBlocks().isEmpty()) {
            textBlockService.computeTextBlocksForSubmissionBasedOnSyntax(textSubmission);
        }

        Result result = null;
        if (!Boolean.TRUE.equals(exampleSubmission.isUsedForTutorial()) || authCheckService.isAtLeastInstructorForExercise(textExercise, user)) {
            result = textSubmission.getLatestResult();
            if (result != null) {
                final List<Feedback> assessments = feedbackRepository.findByResult(result);
                result.setFeedbacks(assessments);
            }
        }

        return ResponseEntity.ok().body(result);
    }

    /**
     * GET participations/:participationId/submissions/:submissionId/feedbacks/:feedbackId/feedback-conflicts : Retrieves all the text submissions that have conflicting feedback with the given feedback id.
     * User needs to be either assessor of the submission (with given feedback id) or an instructor for the exercise to check the conflicts.
     *
     * @param participationId - id of the participation to the submission
     * @param submissionId - id of the submission with the feedback that has conflicts
     * @param feedbackId - id of the feedback that has conflicts
     * @return - Set of text submissions
     */
    @GetMapping("/participations/{participationId}/submissions/{submissionId}/feedbacks/{feedbackId}/feedback-conflicts")
    @PreAuthorize("hasRole('TA')")
    public ResponseEntity<Set<TextSubmission>> getConflictingTextSubmissions(@PathVariable long participationId, @PathVariable long submissionId, @PathVariable long feedbackId) {
        log.debug("REST request to get conflicting text assessments for feedback id: {}", feedbackId);

        final TextSubmission textSubmission = textSubmissionRepository.findByIdWithEagerParticipationExerciseResultAssessorElseThrow(submissionId);

        final TextExercise textExercise = (TextExercise) textSubmission.getParticipation().getExercise();
        final Result result = textSubmission.getLatestResult();

        if (!textSubmission.getParticipation().getId().equals(participationId)) {
            throw new BadRequestAlertException("The participationId in the path doesnt match the participationId to the submission " + submissionId + " !", "participationId",
                    "participationIdMismatch");
        }

        final User user = userRepository.getUserWithGroupsAndAuthorities();
        checkTextExerciseForRequest(textExercise, user);

        if (!textExercise.isAutomaticAssessmentEnabled() || automaticTextAssessmentConflictService.isEmpty()) {
            throw new BadRequestAlertException("Automatic assessments are not enabled for this text exercise or text assessment conflict service is not available!",
                    "textAssessmentConflict", "AutomaticTextAssessmentConflictServiceNotFound");
        }
        final boolean isInstructorForExercise = authCheckService.isAtLeastInstructorForExercise(textExercise, user);
        if (result != null && result.getAssessor() != null && !result.getAssessor().getLogin().equals(user.getLogin()) && !isInstructorForExercise) {
            throw new AccessForbiddenException();
        }

        Set<TextSubmission> textSubmissionSet = this.automaticTextAssessmentConflictService.get().getConflictingSubmissions(feedbackId);

        final ResponseEntity.BodyBuilder bodyBuilder = ResponseEntity.ok();
        return bodyBuilder.body(textSubmissionSet);
    }

    /**
     * POST exercise/:exerciseId/feedbackConflict/:feedbackConflictId/solve : With given feedbackConflictId, finds the conflict and sets it as solved.
     * Checks; if the feedback conflict is present, if the user is the assessor of one of the feedback or
     * if the user is at least the instructor for the exercise.
     *
     * @param exerciseId - exercise id to check access rights.
     * @param feedbackConflictId - feedback conflict id to set the conflict as solved
     * @return - solved feedback conflict
     */
    @PostMapping("/exercises/{exerciseId}/feedback-conflicts/{feedbackConflictId}/solve")
    @PreAuthorize("hasRole('TA')")
    public ResponseEntity<FeedbackConflict> solveFeedbackConflict(@PathVariable long exerciseId, @PathVariable long feedbackConflictId) {
        log.debug("REST request to set feedback conflict as solved for feedbackConflictId: {}", feedbackConflictId);

        if (automaticTextAssessmentConflictService.isEmpty()) {
            throw new BadRequestAlertException("Automatic text assessment conflict service is not available!", "automaticTextAssessmentConflictService",
                    "AutomaticTextAssessmentConflictServiceNotFound");
        }
        final User user = userRepository.getUserWithGroupsAndAuthorities();

        final FeedbackConflict feedbackConflict = feedbackConflictRepository.findByFeedbackConflictIdElseThrow(feedbackConflictId);
        if (!feedbackConflict.getFirstFeedback().getResult().getParticipation().getExercise().getId().equals(exerciseId)) {
            throw new BadRequestAlertException("The exerciseId in the path doesnt match the exerciseId to the feedbackConflict " + feedbackConflictId + " !", "exerciseId",
                    "exerciseIdMismatch");
        }
        final TextExercise textExercise = textExerciseRepository.findByIdElseThrow(exerciseId);
        final User firstAssessor = feedbackConflict.getFirstFeedback().getResult().getAssessor();
        final User secondAssessor = feedbackConflict.getSecondFeedback().getResult().getAssessor();

        final boolean isInstructorForExercise = authCheckService.isAtLeastInstructorForExercise(textExercise, user);
        if (!firstAssessor.getLogin().equals(user.getLogin()) && !secondAssessor.getLogin().equals(user.getLogin()) && !isInstructorForExercise) {
            throw new AccessForbiddenException();
        }

        this.automaticTextAssessmentConflictService.get().solveFeedbackConflict(feedbackConflict);
        return ResponseEntity.ok(feedbackConflict);
    }

    @Override
    String getEntityName() {
        return ENTITY_NAME;
    }

    /**
     * Checks if the given textExercise is valid and if the requester have the
     * required permissions
     * @param textExercise which needs to be checked
     * @throws BadRequestAlertException if no request was found
     */
    private void checkTextExerciseForRequest(@Nullable TextExercise textExercise, User user) {
        if (textExercise == null) {
            throw new BadRequestAlertException("No exercise was found for the given ID.", "textExercise", "exerciseNotFound");
        }
        validateExercise(textExercise);
        checkAuthorization(textExercise, user);
    }

    /**
     * Save TextBlocks received from Client (if present). We need to reference them to the submission first.
     * @param textBlocks received from Client
     * @param textSubmission to associate blocks with
     */
    private void saveTextBlocks(final Set<TextBlock> textBlocks, final TextSubmission textSubmission, final TextExercise exercise, final List<Feedback> feedbacks) {
        if (textBlocks != null) {
            List<Feedback> nonGeneralFeedbacks = feedbacks.stream().filter(feedback -> feedback.getReference() != null).toList();
            Map<String, Feedback> feedbackMap = nonGeneralFeedbacks.stream().collect(Collectors.toMap(Feedback::getReference, Function.identity()));
            final Set<String> existingTextBlockIds = textSubmission.getBlocks().stream().map(TextBlock::getId).collect(toSet());
            final var updatedTextBlocks = textBlocks.stream().filter(tb -> !existingTextBlockIds.contains(tb.getId())).peek(tb -> {
                tb.setSubmission(textSubmission);
                tb.setFeedback(feedbackMap.get(tb.getId()));
                tb.setKnowledge(exercise.getKnowledge());
            }).collect(toSet());
            // Update the feedback_id for existing text blocks
            if (!existingTextBlockIds.isEmpty()) {
                final var blocksToUpdate = textSubmission.getBlocks();
                blocksToUpdate.forEach(tb -> tb.setFeedback(feedbackMap.get(tb.getId())));
                updatedTextBlocks.addAll(blocksToUpdate);
            }
            if (!updatedTextBlocks.isEmpty()) {
                // Reload text blocks to avoid trying to delete already removed referenced feedback.
                textBlockService.findAllBySubmissionId(textSubmission.getId());
                textBlockService.saveAll(updatedTextBlocks);
            }
        }
    }
}
