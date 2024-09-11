package de.tum.cit.aet.artemis.web.rest;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static java.util.stream.Collectors.toSet;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.assessment.domain.ExampleSubmission;
import de.tum.cit.aet.artemis.assessment.domain.Feedback;
import de.tum.cit.aet.artemis.assessment.domain.FeedbackType;
import de.tum.cit.aet.artemis.assessment.domain.GradingCriterion;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.repository.ExampleSubmissionRepository;
import de.tum.cit.aet.artemis.assessment.repository.FeedbackRepository;
import de.tum.cit.aet.artemis.assessment.repository.GradingCriterionRepository;
import de.tum.cit.aet.artemis.assessment.repository.ResultRepository;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastInstructor;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastTutor;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participation;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.exercise.repository.SubmissionRepository;
import de.tum.cit.aet.artemis.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.service.ResultService;
import de.tum.cit.aet.artemis.service.TextAssessmentService;
import de.tum.cit.aet.artemis.service.TextBlockService;
import de.tum.cit.aet.artemis.service.TextSubmissionService;
import de.tum.cit.aet.artemis.service.connectors.athena.AthenaFeedbackSendingService;
import de.tum.cit.aet.artemis.text.domain.TextBlock;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;
import de.tum.cit.aet.artemis.text.repository.TextExerciseRepository;
import de.tum.cit.aet.artemis.text.repository.TextSubmissionRepository;
import de.tum.cit.aet.artemis.web.rest.dto.TextAssessmentDTO;
import de.tum.cit.aet.artemis.web.rest.dto.TextAssessmentUpdateDTO;
import de.tum.cit.aet.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.cit.aet.artemis.web.rest.errors.ErrorConstants;
import de.tum.cit.aet.artemis.web.rest.util.HeaderUtil;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

/**
 * REST controller for managing TextAssessment.
 */
@Profile(PROFILE_CORE)
@RestController
@RequestMapping("api/")
public class TextAssessmentResource extends AssessmentResource {

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private static final String ENTITY_NAME = "textAssessment";

    private static final Logger log = LoggerFactory.getLogger(TextAssessmentResource.class);

    private final TextBlockService textBlockService;

    private final TextAssessmentService textAssessmentService;

    private final TextExerciseRepository textExerciseRepository;

    private final TextSubmissionService textSubmissionService;

    private final TextSubmissionRepository textSubmissionRepository;

    private final ExampleSubmissionRepository exampleSubmissionRepository;

    private final GradingCriterionRepository gradingCriterionRepository;

    private final FeedbackRepository feedbackRepository;

    private final ResultService resultService;

    private final Optional<AthenaFeedbackSendingService> athenaFeedbackSendingService;

    public TextAssessmentResource(AuthorizationCheckService authCheckService, TextAssessmentService textAssessmentService, TextBlockService textBlockService,
            TextExerciseRepository textExerciseRepository, TextSubmissionRepository textSubmissionRepository, UserRepository userRepository,
            TextSubmissionService textSubmissionService, ExerciseRepository exerciseRepository, ResultRepository resultRepository,
            GradingCriterionRepository gradingCriterionRepository, ExampleSubmissionRepository exampleSubmissionRepository, SubmissionRepository submissionRepository,
            FeedbackRepository feedbackRepository, ResultService resultService, Optional<AthenaFeedbackSendingService> athenaFeedbackSendingService) {
        super(authCheckService, userRepository, exerciseRepository, textAssessmentService, resultRepository, exampleSubmissionRepository, submissionRepository);

        this.textAssessmentService = textAssessmentService;
        this.textBlockService = textBlockService;
        this.textExerciseRepository = textExerciseRepository;
        this.textSubmissionRepository = textSubmissionRepository;
        this.textSubmissionService = textSubmissionService;
        this.gradingCriterionRepository = gradingCriterionRepository;
        this.feedbackRepository = feedbackRepository;
        this.exampleSubmissionRepository = exampleSubmissionRepository;
        this.resultService = resultService;
        this.athenaFeedbackSendingService = athenaFeedbackSendingService;
    }

    /**
     * PUT participations/:participationId/results/:resultId/text-assessment : Saves a given manual textAssessment
     *
     * @param participationId the participationId of the participation the result belongs to
     * @param resultId        the resultId the assessment belongs to
     * @param textAssessment  the assessments
     * @return 200 Ok if successful with the corresponding result as body, but sensitive information are filtered out
     */
    @PutMapping("participations/{participationId}/results/{resultId}/text-assessment")
    @EnforceAtLeastTutor
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
        ResponseEntity<Result> response = super.saveAssessment(textSubmission, false, textAssessment.getFeedbacks(), resultId, textAssessment.getAssessmentNote());

        if (response.getStatusCode().is2xxSuccessful()) {
            final var feedbacksWithIds = response.getBody().getFeedbacks();
            saveTextBlocks(textAssessment.getTextBlocks(), textSubmission, feedbacksWithIds);
        }

        return response;
    }

    /**
     * PUT exercises/:exerciseId/example-submissions/:exampleSubmissionId/example-text-assessment : save manual example text assessment
     *
     * @param exerciseId          the id of the exercise the exampleSubmission belongs to
     * @param exampleSubmissionId id of the submission
     * @param textAssessment      list of text assessments (consists of feedbacks and text blocks)
     * @return result after saving example text assessment
     */
    @ResponseStatus(HttpStatus.OK)
    @ApiResponses({ @ApiResponse(code = 403, message = ErrorConstants.REQ_403_REASON), @ApiResponse(code = 404, message = ErrorConstants.REQ_404_REASON) })
    @PutMapping("exercises/{exerciseId}/example-submissions/{exampleSubmissionId}/example-text-assessment")
    @EnforceAtLeastTutor
    public ResponseEntity<Result> saveTextExampleAssessment(@PathVariable long exerciseId, @PathVariable long exampleSubmissionId, @RequestBody TextAssessmentDTO textAssessment) {
        log.debug("REST request to save text example assessment : {}", exampleSubmissionId);
        Optional<ExampleSubmission> optionalExampleSubmission = exampleSubmissionRepository.findById(exampleSubmissionId);
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
        final Result result = super.saveExampleAssessment(exampleSubmissionId, textAssessment.getFeedbacks());
        final Submission submission = result.getSubmission();
        final TextSubmission textSubmission = textSubmissionService.findOneWithEagerResultFeedbackAndTextBlocks(submission.getId());
        final List<Feedback> feedbacksWithIds = result.getFeedbacks();
        saveTextBlocks(textAssessment.getTextBlocks(), textSubmission, feedbacksWithIds);
        return ResponseEntity.ok(result);
    }

    /**
     * DELETE exercises/:exerciseId/example-submissions/:exampleSubmissionId/example-assessment/feedback : delete feedback for example submission.
     *
     * @param exerciseId          the id of the exercise the exampleSubmission belongs to
     * @param exampleSubmissionId id of the submission
     * @return 204 No Content
     */
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("exercises/{exerciseId}/example-submissions/{exampleSubmissionId}/example-text-assessment/feedback")
    @EnforceAtLeastTutor
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
     * POST participations/:participationId/results/:resultId/submit-text-assessment : Submits manual textAssessments for a given result
     * and notify the user if it's before the Assessment Due Date
     *
     * @param participationId the participationId of the participation whose assessment shall be saved
     * @param resultId        the resultId the assessment belongs to
     * @param textAssessment  the assessments which should be submitted
     * @return 200 Ok if successful with the corresponding result as a body, but sensitive information are filtered out
     */
    @PostMapping("participations/{participationId}/results/{resultId}/submit-text-assessment")
    @EnforceAtLeastTutor
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
            throw new BadRequestAlertException("participationId in Result of resultId " + resultId + " doesn't match the paths participationId!", "participationId",
                    "participationIdMismatch");
        }
        checkAuthorization(exercise, null);
        final TextSubmission textSubmission = textSubmissionRepository.getTextSubmissionWithResultAndTextBlocksAndFeedbackByResultIdElseThrow(resultId);
        ResponseEntity<Result> response = super.saveAssessment(textSubmission, true, textAssessment.getFeedbacks(), resultId, textAssessment.getAssessmentNote());

        if (response.getStatusCode().is2xxSuccessful()) {
            final var feedbacksWithIds = response.getBody().getFeedbacks();
            saveTextBlocks(textAssessment.getTextBlocks(), textSubmission, feedbacksWithIds);
            sendFeedbackToAthena(exercise, textSubmission, feedbacksWithIds);
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
    @EnforceAtLeastTutor
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
        saveTextBlocks(assessmentUpdate.textBlocks(), textSubmission, result.getFeedbacks());

        if (result.getParticipation() != null && result.getParticipation() instanceof StudentParticipation && !authCheckService.isAtLeastInstructorForExercise(textExercise)) {
            ((StudentParticipation) result.getParticipation()).setParticipant(null);
        }

        return ResponseEntity.ok(result);
    }

    /**
     * POST participations/:participationId/submissions/:submissionId/cancel-assessment : Cancel an assessment of a given submission for the current user, i.e. delete the
     * corresponding result / release the lock. Then the submission is available for assessment again.
     *
     * @param submissionId    the id of the submission for which the current assessment should be canceled
     * @param participationId the participationId of the participation for which the assessment should get canceled
     * @return 200 Ok response if canceling was successful, 403 Forbidden if current user is not the assessor of the submission
     */
    @PostMapping("participations/{participationId}/submissions/{submissionId}/cancel-assessment")
    @EnforceAtLeastTutor
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
     * @param submissionId    - the id of the submission for which the current assessment should be deleted
     * @param resultId        - the id of the result which should get deleted
     * @return 200 Ok response if canceling was successful, 403 Forbidden if current user is not an instructor of the course or an admin
     */
    @Override
    @DeleteMapping("participations/{participationId}/text-submissions/{submissionId}/results/{resultId}")
    @EnforceAtLeastInstructor
    public ResponseEntity<Void> deleteAssessment(@PathVariable Long participationId, @PathVariable Long submissionId, @PathVariable Long resultId) {
        return super.deleteAssessment(participationId, submissionId, resultId);
    }

    /**
     * GET text-submissions/:submissionId/for-assessment
     * Given a submissionId, the method retrieves from the database all the data needed by the tutor to assess the submission. If the tutor has already started
     * assessing the submission, then we also return all the results the tutor has already inserted. If another tutor has already started working on this submission, the system
     * returns an error
     * In case an instructors calls, the resultId is used first. In case the resultId is not set, the correctionRound is used.
     * In case neither resultId nor correctionRound are set, the first correctionRound is used.
     *
     * @param submissionId    the id of the submission we want
     * @param correctionRound correction round for which we want the submission
     * @param resultId        if result already exists, we want to get the submission for this specific result
     * @return a Participation of the tutor in the submission
     */
    @GetMapping("text-submissions/{submissionId}/for-assessment")
    @EnforceAtLeastTutor
    public ResponseEntity<Participation> retrieveParticipationForSubmission(@PathVariable Long submissionId,
            @RequestParam(value = "correction-round", defaultValue = "0") int correctionRound, @RequestParam(value = "resultId", required = false) Long resultId) {
        log.debug("REST request to get data for tutors text assessment submission: {}", submissionId);
        final var textSubmission = textSubmissionRepository.findByIdWithParticipationExerciseResultAssessorAssessmentNoteElseThrow(submissionId);
        final Participation participation = textSubmission.getParticipation();
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

        Set<GradingCriterion> gradingCriteria = gradingCriterionRepository.findByExerciseIdWithEagerGradingCriteria(exercise.getId());
        exercise.setGradingCriteria(gradingCriteria);
        // Remove sensitive information of submission depending on user
        textSubmissionService.hideDetails(textSubmission, user);

        // Prepare for Response: Set Submissions and Results of Participation to include requested only.
        participation.setSubmissions(Set.of(textSubmission));
        textSubmission.getResults().forEach(res -> res.setSubmission(null));

        // set result again as it was changed
        if (resultId != null) {
            result = textSubmission.getManualResultsById(resultId);
            textSubmission.setResults(Collections.singletonList(result));
        }
        else {
            textSubmission.getResultForCorrectionRound(correctionRound);
        }

        textSubmission.removeNotNeededResults(correctionRound, resultId);
        participation.setResults(Set.copyOf(textSubmission.getResults()));

        return ResponseEntity.ok().body(participation);
    }

    /**
     * GET exercise/:exerciseId/submission/:submissionId/example-result : Retrieve the result of an example assessment
     *
     * @param exerciseId   the id of the exercise
     * @param submissionId the id of the submission which must be connected to an example submission
     * @return the example result linked to the submission
     */
    @GetMapping("exercises/{exerciseId}/submissions/{submissionId}/example-result")
    @EnforceAtLeastTutor
    public ResponseEntity<Result> getExampleResultForTutor(@PathVariable long exerciseId, @PathVariable long submissionId) {
        User user = userRepository.getUserWithGroupsAndAuthorities();
        log.debug("REST request to get example assessment for tutors text assessment: {}", submissionId);
        final ExampleSubmission exampleSubmission = exampleSubmissionRepository.findBySubmissionIdWithResultsElseThrow(submissionId);
        final var textExercise = exampleSubmission.getExercise();
        if (!textExercise.getId().equals(exerciseId)) {
            throw new BadRequestAlertException("Exercise to submission with submissionId " + submissionId + " doesn't have the exerciseId " + exerciseId + " !", "exerciseId",
                    "exerciseIdMismatch");
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

        var result = textSubmission.getLatestResult();
        if (result != null) {
            final List<Feedback> assessments = feedbackRepository.findByResult(result);
            result.setFeedbacks(assessments);

            if (Boolean.TRUE.equals(exampleSubmission.isUsedForTutorial()) && !authCheckService.isAtLeastInstructorForExercise(textExercise, user)) {
                Result freshResult = new Result();
                // set the id to null to make sure that the client does know it is a restricted result and treat it accordingly
                result.setId(null);
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
        }

        return ResponseEntity.ok().body(result);
    }

    @Override
    protected String getEntityName() {
        return ENTITY_NAME;
    }

    /**
     * Save TextBlocks received from Client (if present). We need to reference them to the submission first.
     *
     * @param textBlocks     received from Client
     * @param textSubmission to associate blocks with
     * @param feedbacks      the feedbacks to associate with the blocks
     */
    private void saveTextBlocks(final Set<TextBlock> textBlocks, final TextSubmission textSubmission, final List<Feedback> feedbacks) {
        if (textBlocks == null) {
            return;
        }

        List<Feedback> nonGeneralFeedbacks = feedbacks.stream().filter(feedback -> feedback.getReference() != null).toList();
        Map<String, Feedback> feedbackMap = nonGeneralFeedbacks.stream().collect(Collectors.toMap(Feedback::getReference, Function.identity()));
        final Set<String> existingTextBlockIds = textSubmission.getBlocks().stream().map(TextBlock::getId).collect(toSet());
        final var updatedTextBlocks = textBlocks.stream().filter(tb -> !existingTextBlockIds.contains(tb.getId())).peek(tb -> {
            tb.setSubmission(textSubmission);
            tb.setFeedback(feedbackMap.get(tb.getId()));
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

    /**
     * Send feedback to Athena (if enabled for both the Artemis instance and the exercise).
     */
    private void sendFeedbackToAthena(final TextExercise exercise, final TextSubmission textSubmission, final List<Feedback> feedbacks) {
        if (athenaFeedbackSendingService.isPresent() && exercise.areFeedbackSuggestionsEnabled()) {
            athenaFeedbackSendingService.get().sendFeedback(exercise, textSubmission, feedbacks);
        }
    }
}
