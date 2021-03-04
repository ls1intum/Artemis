package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.forbidden;
import static java.util.stream.Collectors.toSet;

import java.util.List;
import java.util.Optional;
import java.util.Set;

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
import de.tum.in.www1.artemis.security.jwt.AtheneTrackingTokenProvider;
import de.tum.in.www1.artemis.service.*;
import de.tum.in.www1.artemis.service.exam.ExamService;
import de.tum.in.www1.artemis.web.rest.dto.TextAssessmentDTO;
import de.tum.in.www1.artemis.web.rest.dto.TextAssessmentUpdateDTO;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.errors.ErrorConstants;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

/**
 * REST controller for managing TextAssessment.
 */
@RestController
// TODO: remove 'text-assessments' here
@RequestMapping("/api/text-assessments")
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

    private final Optional<AtheneTrackingTokenProvider> atheneTrackingTokenProvider;

    private final Optional<AutomaticTextAssessmentConflictService> automaticTextAssessmentConflictService;

    private final GradingCriterionService gradingCriterionService;

    private final FeedbackConflictRepository feedbackConflictRepository;

    public TextAssessmentResource(AuthorizationCheckService authCheckService, TextAssessmentService textAssessmentService, TextBlockService textBlockService,
            TextExerciseRepository textExerciseRepository, TextSubmissionRepository textSubmissionRepository, UserRepository userRepository,
            TextSubmissionService textSubmissionService, WebsocketMessagingService messagingService, ExerciseRepository exerciseRepository, ResultRepository resultRepository,
            GradingCriterionService gradingCriterionService, Optional<AtheneTrackingTokenProvider> atheneTrackingTokenProvider, ExamService examService,
            Optional<AutomaticTextAssessmentConflictService> automaticTextAssessmentConflictService, FeedbackConflictRepository feedbackConflictRepository,
            ExampleSubmissionService exampleSubmissionService) {
        super(authCheckService, userRepository, exerciseRepository, textSubmissionService, textAssessmentService, resultRepository, examService, messagingService,
                exampleSubmissionService);

        this.textAssessmentService = textAssessmentService;
        this.textBlockService = textBlockService;
        this.textExerciseRepository = textExerciseRepository;
        this.textSubmissionRepository = textSubmissionRepository;
        this.textSubmissionService = textSubmissionService;
        this.gradingCriterionService = gradingCriterionService;
        this.atheneTrackingTokenProvider = atheneTrackingTokenProvider;
        this.automaticTextAssessmentConflictService = automaticTextAssessmentConflictService;
        this.feedbackConflictRepository = feedbackConflictRepository;
    }

    /**
     * Saves a given manual textAssessment
     * TODO SE: refactor this restcall to not use the exerciseId anymore, and make uniform with other save..Assessment callse
     * @param exerciseId the exerciseId of the exercise which will be saved
     * @param resultId the resultId the assessment belongs to
     * @param textAssessment the assessments
     * @return 200 Ok if successful with the corresponding result as body, but sensitive information are filtered out
     */
    @PutMapping("/exercise/{exerciseId}/result/{resultId}")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Result> saveTextAssessment(@PathVariable Long exerciseId, @PathVariable Long resultId, @RequestBody TextAssessmentDTO textAssessment) {
        final boolean hasAssessmentWithTooLongReference = textAssessment.getFeedbacks().stream().filter(Feedback::hasReference)
                .anyMatch(f -> f.getReference().length() > Feedback.MAX_REFERENCE_LENGTH);
        if (hasAssessmentWithTooLongReference) {
            throw new BadRequestAlertException("Please select a text block shorter than " + Feedback.MAX_REFERENCE_LENGTH + " characters.", "feedbackList",
                    "feedbackReferenceTooLong");
        }
        final TextSubmission textSubmission = textSubmissionService.getTextSubmissionWithResultAndTextBlocksAndFeedbackByResultId(resultId);
        ResponseEntity<Result> response = super.saveAssessment(textSubmission, false, textAssessment.getFeedbacks(), resultId);

        if (response.getStatusCode().is2xxSuccessful()) {
            saveTextBlocks(textAssessment.getTextBlocks(), textSubmission);
        }

        return response;
    }

    /**
     * PUT text-submissions/:submissionId/example-assessment : save manual example text assessment
     *
     * @param exampleSubmissionId id of the submission
     * @param textAssessment list of text assessments (consists of feedbacks and text blocks)
     * @return result after saving example text assessment
     */
    @ResponseStatus(HttpStatus.OK)
    @ApiResponses({ @ApiResponse(code = 403, message = ErrorConstants.REQ_403_REASON), @ApiResponse(code = 404, message = ErrorConstants.REQ_404_REASON) })
    @PutMapping("/text-submissions/{exampleSubmissionId}/example-assessment")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Result> saveTextExampleAssessment(@PathVariable long exampleSubmissionId, @RequestBody TextAssessmentDTO textAssessment) {
        log.debug("REST request to save text example assessment : {}", exampleSubmissionId);
        final var response = super.saveExampleAssessment(exampleSubmissionId, textAssessment.getFeedbacks());
        if (response.getStatusCode().is2xxSuccessful()) {
            final Submission submission = response.getBody().getSubmission();
            final var textSubmission = textSubmissionService.findOneWithEagerResultFeedbackAndTextBlocks(submission.getId());
            saveTextBlocks(textAssessment.getTextBlocks(), textSubmission);
        }
        return response;
    }

    /**
     * DELETE text-submissions/:exampleSubmissionId/example-assessment : delete result & text blocks for example submission.
     * This is used when updating the text of the example assessment.
     *
     * @param exampleSubmissionId id of the submission
     * @return 204 No Content
     */
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/text-submissions/{exampleSubmissionId}/example-assessment/feedback")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Void> deleteTextExampleAssessment(@PathVariable long exampleSubmissionId) {
        log.debug("REST request to delete text example assessment : {}", exampleSubmissionId);
        User user = userRepository.getUserWithGroupsAndAuthorities();
        ExampleSubmission exampleSubmission = exampleSubmissionService.findOneWithEagerResult(exampleSubmissionId);
        Submission submission = exampleSubmission.getSubmission();
        Exercise exercise = exampleSubmission.getExercise();
        checkAuthorization(exercise, user);

        if (!(submission instanceof TextSubmission)) {
            return ResponseEntity.badRequest().build();
        }

        // 1. Delete Textblocks
        textBlockService.deleteForSubmission((TextSubmission) submission);

        // 2. Delete Feedbacks
        final var latestResult = submission.getLatestResult();
        if (latestResult != null) {
            latestResult.getFeedbacks().clear();
            resultRepository.save(latestResult);
        }

        return ResponseEntity.noContent().build();
    }

    /**
     * Submits manual textAssessments for a given result and notify the user if it's before the Assessment Due Date
     *
     * @param exerciseId the exerciseId of the exercise which will be saved
     * @param resultId the resultId the assessment belongs to
     * @param textAssessment the assessments which should be submitted
     * @return 200 Ok if successful with the corresponding result as a body, but sensitive information are filtered out
     */
    @PutMapping("/exercise/{exerciseId}/result/{resultId}/submit")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Result> submitTextAssessment(@PathVariable Long exerciseId, @PathVariable Long resultId, @RequestBody TextAssessmentDTO textAssessment) {
        final boolean hasAssessmentWithTooLongReference = textAssessment.getFeedbacks().stream().filter(Feedback::hasReference)
                .anyMatch(feedback -> feedback.getReference().length() > Feedback.MAX_REFERENCE_LENGTH);
        if (hasAssessmentWithTooLongReference) {
            throw new BadRequestAlertException("Please select a text block shorter than " + Feedback.MAX_REFERENCE_LENGTH + " characters.", "feedbackList",
                    "feedbackReferenceTooLong");
        }
        final TextExercise exercise = textExerciseRepository.findByIdElseThrow(exerciseId);
        final TextSubmission textSubmission = textSubmissionService.getTextSubmissionWithResultAndTextBlocksAndFeedbackByResultId(resultId);
        ResponseEntity<Result> response = super.saveAssessment(textSubmission, true, textAssessment.getFeedbacks(), resultId);

        if (response.getStatusCode().is2xxSuccessful()) {
            saveTextBlocks(textAssessment.getTextBlocks(), textSubmission);

            // call feedback conflict service
            if (exercise.isAutomaticAssessmentEnabled() && automaticTextAssessmentConflictService.isPresent()) {
                this.automaticTextAssessmentConflictService.get().asyncCheckFeedbackConsistency(textAssessment.getTextBlocks(), textSubmission.getLatestResult().getFeedbacks(),
                        exerciseId);
            }
        }

        return response;
    }

    /**
     * Update an assessment after a complaint was accepted.
     *
     * @param submissionId     the id of the submission for which the assessment should be updated
     * @param assessmentUpdate the assessment update containing the new feedback items and the response to the complaint
     * @return the updated result
     */
    @ResponseStatus(HttpStatus.OK)
    @PutMapping("/text-submissions/{submissionId}/assessment-after-complaint")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Result> updateTextAssessmentAfterComplaint(@PathVariable Long submissionId, @RequestBody TextAssessmentUpdateDTO assessmentUpdate) {
        log.debug("REST request to update the assessment of submission {} after complaint.", submissionId);
        User user = userRepository.getUserWithGroupsAndAuthorities();
        TextSubmission textSubmission = textSubmissionService.findOneWithEagerResultFeedbackAndTextBlocks(submissionId);
        StudentParticipation studentParticipation = (StudentParticipation) textSubmission.getParticipation();
        long exerciseId = studentParticipation.getExercise().getId();
        TextExercise textExercise = textExerciseRepository.findByIdElseThrow(exerciseId);
        checkAuthorization(textExercise, user);
        saveTextBlocks(assessmentUpdate.getTextBlocks(), textSubmission);
        Result result = textAssessmentService.updateAssessmentAfterComplaint(textSubmission.getLatestResult(), textExercise, assessmentUpdate);

        if (result.getParticipation() != null && result.getParticipation() instanceof StudentParticipation && !authCheckService.isAtLeastInstructorForExercise(textExercise)) {
            ((StudentParticipation) result.getParticipation()).setParticipant(null);
        }

        return ResponseEntity.ok(result);
    }

    /**
     * Cancel an assessment of a given submission for the current user, i.e. delete the corresponding result / release the lock. Then the submission is available for assessment
     * again.
     *
     * @param submissionId the id of the submission for which the current assessment should be canceled
     * @param exerciseId the exerciseId of the exercise for which the assessment gets canceled
     * @return 200 Ok response if canceling was successful, 403 Forbidden if current user is not the assessor of the submission
     */
    @PutMapping("/exercise/{exerciseId}/submission/{submissionId}/cancel-assessment")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Void> cancelAssessment(@PathVariable Long exerciseId, @PathVariable Long submissionId) {
        return super.cancelAssessment(submissionId);
    }

    /**
     * Given an exerciseId and a submissionId, the method retrieves from the database all the data needed by the tutor to assess the submission. If the tutor has already started
     * assessing the submission, then we also return all the results the tutor has already inserted. If another tutor has already started working on this submission, the system
     * returns an error
     *
     * @param submissionId the id of the submission we want
     * @param correctionRound correction round for which we want the submission
     * @return a Participation of the tutor in the submission
     */
    @GetMapping("/submission/{submissionId}")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Participation> retrieveParticipationForSubmission(@PathVariable Long submissionId,
            @RequestParam(value = "correction-round", defaultValue = "0") int correctionRound) {
        log.debug("REST request to get data for tutors text assessment submission: {}", submissionId);

        final Optional<TextSubmission> optionalTextSubmission = textSubmissionRepository.findByIdWithEagerParticipationExerciseResultAssessor(submissionId);
        if (optionalTextSubmission.isEmpty()) {
            return ResponseEntity.badRequest()
                    .headers(HeaderUtil.createFailureAlert(applicationName, true, "textSubmission", "textSubmissionNotFound", "No Submission was found for the given ID."))
                    .body(null);
        }

        final TextSubmission textSubmission = optionalTextSubmission.get();
        final Participation participation = textSubmission.getParticipation();
        final TextExercise exercise = (TextExercise) participation.getExercise();
        Result result = textSubmission.getResultForCorrectionRound(correctionRound);

        final User user = userRepository.getUserWithGroupsAndAuthorities();
        checkAuthorization(exercise, user);
        final boolean isAtLeastInstructorForExercise = authCheckService.isAtLeastInstructorForExercise(exercise, user);

        if (result != null && !isAtLeastInstructorForExercise && result.getAssessor() != null && !result.getAssessor().getLogin().equals(user.getLogin())
                && result.getCompletionDate() == null) {
            // If we already have a result, we need to check if it is locked.
            throw new BadRequestAlertException("This submission is being assessed by another tutor", ENTITY_NAME, "alreadyAssessed");
        }

        textSubmissionService.lockTextSubmissionToBeAssessed(textSubmission, correctionRound);
        textAssessmentService.prepareSubmissionForAssessment(textSubmission, correctionRound);

        List<GradingCriterion> gradingCriteria = gradingCriterionService.findByExerciseIdWithEagerGradingCriteria(exercise.getId());
        exercise.setGradingCriteria(gradingCriteria);
        // Remove sensitive information of submission depending on user
        textSubmissionService.hideDetails(textSubmission, user);

        // Prepare for Response: Set Submissions and Results of Participation to include requested only.
        participation.setSubmissions(Set.of(textSubmission));
        textSubmission.getResults().forEach(r -> r.setSubmission(null));

        // set result again as it was changed
        result = textSubmission.getResultForCorrectionRound(correctionRound);

        // sets results for participation as legacy requires it, will change in follow up NR SE
        participation.setResults(Set.copyOf(textSubmission.getResults()));

        final ResponseEntity.BodyBuilder bodyBuilder = ResponseEntity.ok();
        final Result finalResult = result;

        // Add the jwt token as a header to the response for tutor-assessment tracking to the request if the athene profile is set
        this.atheneTrackingTokenProvider.ifPresent(atheneTrackingTokenProvider -> atheneTrackingTokenProvider.addTokenToResponseEntity(bodyBuilder, finalResult));
        return bodyBuilder.body(participation);
    }

    /**
     * Retrieve the result of an example assessment, only if the user is an instructor or if the submission is used for tutorial purposes.
     *
     * @param exerciseId   the id of the exercise
     * @param submissionId the id of the submission which must be connected to an example submission
     * @return the example result linked to the submission
     */
    @GetMapping("/exercise/{exerciseId}/submission/{submissionId}/example-result")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Result> getExampleResultForTutor(@PathVariable long exerciseId, @PathVariable long submissionId) {
        User user = userRepository.getUserWithGroupsAndAuthorities();
        log.debug("REST request to get example assessment for tutors text assessment: {}", submissionId);
        final var textExercise = textExerciseRepository.findByIdElseThrow(exerciseId);

        // If the user is not at least a tutor for this exercise, return error
        if (!authCheckService.isAtLeastTeachingAssistantForExercise(textExercise, user)) {
            return forbidden();
        }
        final ExampleSubmission exampleSubmission = exampleSubmissionService.findOneBySubmissionId(submissionId);
        Submission submission = exampleSubmission.getSubmission();

        if (!(submission instanceof TextSubmission)) {
            return ResponseEntity.badRequest().body(null);
        }

        final TextSubmission textSubmission = (TextSubmission) submission;
        final var textBlocks = textBlockService.findAllBySubmissionId(textSubmission.getId());
        textSubmission.setBlocks(textBlocks);
        if (textSubmission.getBlocks() == null || textSubmission.getBlocks().isEmpty()) {
            textBlockService.computeTextBlocksForSubmissionBasedOnSyntax(textSubmission);
        }

        Result result;
        if (!Boolean.TRUE.equals(exampleSubmission.isUsedForTutorial()) || authCheckService.isAtLeastInstructorForExercise(textExercise, user)) {
            result = textSubmission.getLatestResult();
            final List<Feedback> assessments = textAssessmentService.getAssessmentsForResult(result);
            result.setFeedbacks(assessments);
        }
        else {
            result = new Result();
            result.setSubmission(textSubmission);
        }

        return ResponseEntity.ok().body(result);
    }

    /**
     * Retrieves all the text submissions that have conflicting feedback with the given feedback id.
     * User needs to be either assessor of the submission (with given feedback id) or an instructor for the exercise to check the conflicts.
     *
     * @param submissionId - id of the submission with the feedback that has conflicts
     * @param feedbackId - id of the feedback that has conflicts
     * @return - Set of text submissions
     */
    @GetMapping("/submission/{submissionId}/feedback/{feedbackId}/feedback-conflicts")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Set<TextSubmission>> getConflictingTextSubmissions(@PathVariable long submissionId, @PathVariable long feedbackId) {
        log.debug("REST request to get conflicting text assessments for feedback id: {}", feedbackId);

        final Optional<TextSubmission> textSubmission = textSubmissionRepository.findByIdWithEagerParticipationExerciseResultAssessor(submissionId);
        if (textSubmission.isEmpty()) {
            return ResponseEntity.badRequest()
                    .headers(HeaderUtil.createFailureAlert(applicationName, true, "textSubmission", "textSubmissionNotFound", "No Submission was found for the given ID."))
                    .body(null);
        }

        final TextExercise textExercise = (TextExercise) textSubmission.get().getParticipation().getExercise();
        final Result result = textSubmission.get().getLatestResult();

        final User user = userRepository.getUserWithGroupsAndAuthorities();
        checkTextExerciseForRequest(textExercise, user);

        if (!textExercise.isAutomaticAssessmentEnabled() || automaticTextAssessmentConflictService.isEmpty()) {
            throw new BadRequestAlertException("Automatic assessments are not enabled for this text exercise or text assessment conflict service is not available!",
                    "textAssessmentConflict", "AutomaticTextAssessmentConflictServiceNotFound");
        }

        final boolean isAtLeastInstructorForExercise = authCheckService.isAtLeastInstructorForExercise(textExercise, user);

        if (result != null && result.getAssessor() != null && !result.getAssessor().getLogin().equals(user.getLogin()) && !isAtLeastInstructorForExercise) {
            return forbidden();
        }

        Set<TextSubmission> textSubmissionSet = this.automaticTextAssessmentConflictService.get().getConflictingSubmissions(feedbackId);

        final ResponseEntity.BodyBuilder bodyBuilder = ResponseEntity.ok();
        return bodyBuilder.body(textSubmissionSet);
    }

    /**
     * With given feedbackConflictId, finds the conflict and sets it as solved.
     * Checks; if the feedback conflict is present, if the user is the assessor of one of the feedback or
     * if the user is at least the instructor for the exercise.
     *
     * @param exerciseId - exercise id to check access rights.
     * @param feedbackConflictId - feedback conflict id to set the conflict as solved
     * @return - solved feedback conflict
     */
    @GetMapping("/exercise/{exerciseId}/feedbackConflict/{feedbackConflictId}/solve-feedback-conflict")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<FeedbackConflict> solveFeedbackConflict(@PathVariable long exerciseId, @PathVariable long feedbackConflictId) {
        log.debug("REST request to set feedback conflict as solved for feedbackConflictId: {}", feedbackConflictId);

        if (automaticTextAssessmentConflictService.isEmpty()) {
            throw new BadRequestAlertException("Automatic text assessment conflict service is not available!", "automaticTextAssessmentConflictService",
                    "AutomaticTextAssessmentConflictServiceNotFound");
        }

        final User user = userRepository.getUserWithGroupsAndAuthorities();
        final var textExercise = textExerciseRepository.findByIdElseThrow(exerciseId);

        Optional<FeedbackConflict> optionalFeedbackConflict = this.feedbackConflictRepository.findByFeedbackConflictId(feedbackConflictId);
        if (optionalFeedbackConflict.isEmpty()) {
            return ResponseEntity.badRequest().headers(
                    HeaderUtil.createFailureAlert(applicationName, true, "feedbackConflict", "feedbackConflictNotFound", "No feedback conflict was found for the given ID."))
                    .body(null);
        }

        final FeedbackConflict feedbackConflict = optionalFeedbackConflict.get();
        final User firstAssessor = feedbackConflict.getFirstFeedback().getResult().getAssessor();
        final User secondAssessor = feedbackConflict.getSecondFeedback().getResult().getAssessor();

        final boolean isAtLeastInstructorForExercise = authCheckService.isAtLeastInstructorForExercise(textExercise, user);

        if (!isAtLeastInstructorForExercise && !firstAssessor.getLogin().equals(user.getLogin()) && !secondAssessor.getLogin().equals(user.getLogin())) {
            return forbidden();
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
    private void saveTextBlocks(final Set<TextBlock> textBlocks, final TextSubmission textSubmission) {
        if (textBlocks != null) {
            final Set<String> existingTextBlockIds = textSubmission.getBlocks().stream().map(TextBlock::getId).collect(toSet());
            final var updatedTextBlocks = textBlocks.stream().filter(tb -> !existingTextBlockIds.contains(tb.getId())).peek(tb -> tb.setSubmission(textSubmission))
                    .collect(toSet());
            textBlockService.saveAll(updatedTextBlocks);
        }
    }
}
