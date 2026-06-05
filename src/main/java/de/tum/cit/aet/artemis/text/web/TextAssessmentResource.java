package de.tum.cit.aet.artemis.text.web;

import static java.util.stream.Collectors.toSet;

import java.util.ArrayList;
import java.util.Collection;
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
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
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

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.assessment.domain.Complaint;
import de.tum.cit.aet.artemis.assessment.domain.ComplaintResponse;
import de.tum.cit.aet.artemis.assessment.domain.ExampleSubmission;
import de.tum.cit.aet.artemis.assessment.domain.Feedback;
import de.tum.cit.aet.artemis.assessment.domain.FeedbackType;
import de.tum.cit.aet.artemis.assessment.domain.GradingCriterion;
import de.tum.cit.aet.artemis.assessment.domain.GradingInstruction;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.dto.AssessmentUpdateBaseDTO;
import de.tum.cit.aet.artemis.assessment.dto.FeedbackDTO;
import de.tum.cit.aet.artemis.assessment.dto.ResultDTO;
import de.tum.cit.aet.artemis.assessment.repository.ExampleSubmissionRepository;
import de.tum.cit.aet.artemis.assessment.repository.FeedbackRepository;
import de.tum.cit.aet.artemis.assessment.repository.GradingCriterionRepository;
import de.tum.cit.aet.artemis.assessment.repository.GradingInstructionRepository;
import de.tum.cit.aet.artemis.assessment.repository.ResultRepository;
import de.tum.cit.aet.artemis.assessment.service.ResultService;
import de.tum.cit.aet.artemis.assessment.web.AssessmentResource;
import de.tum.cit.aet.artemis.athena.api.AthenaFeedbackApi;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastInstructor;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastTutor;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.util.HeaderUtil;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participation;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.exercise.repository.SubmissionRepository;
import de.tum.cit.aet.artemis.text.config.TextEnabled;
import de.tum.cit.aet.artemis.text.domain.TextBlock;
import de.tum.cit.aet.artemis.text.domain.TextBlockType;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;
import de.tum.cit.aet.artemis.text.dto.ComplaintResponseRequestDTO;
import de.tum.cit.aet.artemis.text.dto.TextAssessmentDTO;
import de.tum.cit.aet.artemis.text.dto.TextAssessmentUpdateDTO;
import de.tum.cit.aet.artemis.text.dto.TextBlockDTO;
import de.tum.cit.aet.artemis.text.dto.TextExampleResultDTO;
import de.tum.cit.aet.artemis.text.dto.TextExerciseResponseDTO;
import de.tum.cit.aet.artemis.text.dto.TextParticipationDTO;
import de.tum.cit.aet.artemis.text.repository.TextExerciseRepository;
import de.tum.cit.aet.artemis.text.repository.TextSubmissionRepository;
import de.tum.cit.aet.artemis.text.service.TextAssessmentService;
import de.tum.cit.aet.artemis.text.service.TextBlockService;
import de.tum.cit.aet.artemis.text.service.TextSubmissionService;

/**
 * REST controller for managing TextAssessment.
 */
@Conditional(TextEnabled.class)
@Lazy
@RestController
@RequestMapping("api/text/")
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

    private final GradingInstructionRepository gradingInstructionRepository;

    private final Optional<AthenaFeedbackApi> athenaFeedbackApi;

    public TextAssessmentResource(AuthorizationCheckService authCheckService, TextAssessmentService textAssessmentService, TextBlockService textBlockService,
            TextExerciseRepository textExerciseRepository, TextSubmissionRepository textSubmissionRepository, UserRepository userRepository,
            TextSubmissionService textSubmissionService, ExerciseRepository exerciseRepository, ResultRepository resultRepository,
            GradingCriterionRepository gradingCriterionRepository, ExampleSubmissionRepository exampleSubmissionRepository, SubmissionRepository submissionRepository,
            FeedbackRepository feedbackRepository, ResultService resultService, GradingInstructionRepository gradingInstructionRepository,
            Optional<AthenaFeedbackApi> athenaFeedbackApi) {
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
        this.gradingInstructionRepository = gradingInstructionRepository;
        this.athenaFeedbackApi = athenaFeedbackApi;
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
    public ResponseEntity<ResultDTO> saveTextAssessment(@PathVariable Long participationId, @PathVariable Long resultId, @RequestBody TextAssessmentDTO textAssessment) {
        final List<Feedback> feedbacks = feedbacksFromDtos(textAssessment.feedbacks());
        final boolean hasAssessmentWithTooLongReference = feedbacks != null
                && feedbacks.stream().filter(Feedback::hasReference).anyMatch(feedback -> feedback.getReference().length() > Feedback.MAX_REFERENCE_LENGTH);
        if (hasAssessmentWithTooLongReference) {
            throw new BadRequestAlertException("Please select a text block shorter than " + Feedback.MAX_REFERENCE_LENGTH + " characters.", "feedbackList",
                    "feedbackReferenceTooLong");
        }
        final Set<TextBlock> textBlocks = textBlocksFromDtos(textAssessment.textBlocks());
        Result result = resultRepository.findByIdElseThrow(resultId);
        if (!result.getSubmission().getParticipation().getId().equals(participationId)) {
            throw new BadRequestAlertException("participationId in Result of resultId " + resultId + " doesn't match the paths participationId!", "feedbackList",
                    "participationIdMismatch");
        }
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.TEACHING_ASSISTANT, result.getSubmission().getParticipation().getExercise(), null);
        final var textSubmission = textSubmissionRepository.getTextSubmissionWithResultAndTextBlocksAndFeedbackByResultIdElseThrow(resultId);
        ResponseEntity<Result> response = super.saveAssessment(textSubmission, false, feedbacks, resultId, textAssessment.assessmentNote());

        if (response.getStatusCode().is2xxSuccessful()) {
            final var feedbacksWithIds = response.getBody().getFeedbacks();
            saveTextBlocks(textBlocks, textSubmission, feedbacksWithIds);
        }

        return ResponseEntity.status(response.getStatusCode()).headers(response.getHeaders()).body(ResultDTO.of(response.getBody()));
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
    @PutMapping("exercises/{exerciseId}/example-submissions/{exampleSubmissionId}/example-text-assessment")
    @EnforceAtLeastTutor
    public ResponseEntity<ResultDTO> saveTextExampleAssessment(@PathVariable long exerciseId, @PathVariable long exampleSubmissionId,
            @RequestBody TextAssessmentDTO textAssessment) {
        log.debug("REST request to save text example assessment : {}", exampleSubmissionId);
        final List<Feedback> feedbacks = feedbacksFromDtos(textAssessment.feedbacks());
        final Set<TextBlock> textBlocks = textBlocksFromDtos(textAssessment.textBlocks());
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
        final Result result = super.saveExampleAssessment(exampleSubmissionId, feedbacks);
        final Submission submission = result.getSubmission();
        final TextSubmission textSubmission = textSubmissionService.findOneWithEagerResultFeedbackAndTextBlocks(submission.getId());
        final Set<Feedback> feedbacksWithIds = result.getFeedbacks();
        saveTextBlocks(textBlocks, textSubmission, feedbacksWithIds);
        return ResponseEntity.ok(ResultDTO.of(result));
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
    public ResponseEntity<ResultDTO> submitTextAssessment(@PathVariable Long participationId, @PathVariable Long resultId, @RequestBody TextAssessmentDTO textAssessment) {
        final List<Feedback> feedbacks = feedbacksFromDtos(textAssessment.feedbacks());
        final boolean hasAssessmentWithTooLongReference = feedbacks.stream().filter(Feedback::hasReference)
                .anyMatch(feedback -> feedback.getReference().length() > Feedback.MAX_REFERENCE_LENGTH);
        if (hasAssessmentWithTooLongReference) {
            throw new BadRequestAlertException("Please select a text block shorter than " + Feedback.MAX_REFERENCE_LENGTH + " characters.", "feedbackList",
                    "feedbackReferenceTooLong");
        }
        final Set<TextBlock> textBlocks = textBlocksFromDtos(textAssessment.textBlocks());
        Result result = resultRepository.findByIdElseThrow(resultId);
        if (!(result.getSubmission().getParticipation().getExercise() instanceof final TextExercise exercise)) {
            throw new BadRequestAlertException("This exercise isn't a TextExercise!", "Exercise", "wrongExerciseType");
        }
        else if (!result.getSubmission().getParticipation().getId().equals(participationId)) {
            throw new BadRequestAlertException("participationId in Result of resultId " + resultId + " doesn't match the paths participationId!", "participationId",
                    "participationIdMismatch");
        }
        checkAuthorization(exercise, null);
        final TextSubmission textSubmission = textSubmissionRepository.getTextSubmissionWithResultAndTextBlocksAndFeedbackByResultIdElseThrow(resultId);
        ResponseEntity<Result> response = super.saveAssessment(textSubmission, true, feedbacks, resultId, textAssessment.assessmentNote());

        if (response.getStatusCode().is2xxSuccessful()) {
            final var feedbacksWithIds = response.getBody().getFeedbacks();
            saveTextBlocks(textBlocks, textSubmission, feedbacksWithIds);
            sendFeedbackToAthena(exercise, textSubmission, feedbacksWithIds);
        }

        return ResponseEntity.status(response.getStatusCode()).headers(response.getHeaders()).body(ResultDTO.of(response.getBody()));
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
    public ResponseEntity<ResultDTO> updateTextAssessmentAfterComplaint(@PathVariable Long participationId, @PathVariable Long submissionId,
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
        final AssessmentUpdateBaseDTO assessmentUpdateEntities = assessmentUpdateFromDto(assessmentUpdate);
        final Set<TextBlock> textBlocks = textBlocksFromDtos(assessmentUpdate.textBlocks());
        Result result = textAssessmentService.updateAssessmentAfterComplaint(textSubmission.getLatestResult(), textExercise, assessmentUpdateEntities);
        saveTextBlocks(textBlocks, textSubmission, result.getFeedbacks());

        if (result.getSubmission().getParticipation() != null && result.getSubmission().getParticipation() instanceof StudentParticipation
                && !authCheckService.isAtLeastInstructorForExercise(textExercise)) {
            ((StudentParticipation) result.getSubmission().getParticipation()).setParticipant(null);
        }

        return ResponseEntity.ok(ResultDTO.of(result));
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
     * @return a Participation with relevant data for a tutor or instructor to assess the submission
     */
    @GetMapping("text-submissions/{submissionId}/for-assessment")
    @EnforceAtLeastTutor
    public ResponseEntity<TextParticipationDTO> retrieveParticipationForSubmission(@PathVariable Long submissionId,
            @RequestParam(value = "correction-round", defaultValue = "0") int correctionRound, @RequestParam(value = "resultId", required = false) Long resultId) {
        log.debug("REST request to get data for tutors text assessment submission: {}", submissionId);
        var textSubmission = textSubmissionRepository.findByIdWithParticipationExerciseResultAssessorAssessmentNoteElseThrow(submissionId);
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
                        .headers(HeaderUtil.createFailureAlert(applicationName, true, "TextSubmission", "ResultNotFound", "No Result was found for the given ID."))
                        .body((TextParticipationDTO) null);
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

            textSubmission = textSubmissionService.lockTextSubmissionToBeAssessed(textSubmission.getId(), correctionRound);
            // reconnect with participation
            textSubmission.setParticipation(participation);
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

        final TextParticipationDTO participationDTO = TextParticipationDTO.of((StudentParticipation) participation, isAtLeastInstructorForExercise)
                .withExercise(TextExerciseResponseDTO.of((TextExercise) exercise));
        return ResponseEntity.ok().body(participationDTO);
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
    public ResponseEntity<TextExampleResultDTO> getExampleResultForTutor(@PathVariable long exerciseId, @PathVariable long submissionId) {
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
            return ResponseEntity.badRequest().body((TextExampleResultDTO) null);
        }

        final var textBlocks = textBlockService.findAllBySubmissionId(textSubmission.getId());
        textSubmission.setBlocks(textBlocks);
        if (textSubmission.getBlocks() == null || textSubmission.getBlocks().isEmpty()) {
            textBlockService.computeTextBlocksForSubmissionBasedOnSyntax(textSubmission);
        }

        var result = textSubmission.getLatestResult();
        if (result == null) {
            return ResponseEntity.ok().body((TextExampleResultDTO) null);
        }

        // The tutor needs the submission text and blocks to display and assess the example, in both the masked and full cases.
        final List<TextBlockDTO> blockDTOs = textSubmission.getBlocks() == null ? List.of() : textSubmission.getBlocks().stream().map(TextBlockDTO::of).toList();
        final TextExampleResultDTO.ExampleTextSubmissionDTO submissionDTO = new TextExampleResultDTO.ExampleTextSubmissionDTO(textSubmission.getId(), textSubmission.getText(),
                textSubmission.getLanguage(), blockDTOs);

        final List<Feedback> assessments = feedbackRepository.findByResult(result);
        result.setFeedbacks(assessments);

        if (Boolean.TRUE.equals(exampleSubmission.isUsedForTutorial()) && !authCheckService.isAtLeastInstructorForExercise(textExercise, user)) {
            // Restricted result: the id is null so the client knows it is restricted, and only id/reference/type of the
            // (non-general, referenced) feedbacks are exposed; the submission text/blocks are still included so the tutor can assess.
            final List<FeedbackDTO> maskedFeedbacks = result.getFeedbacks() == null ? List.of()
                    : result.getFeedbacks().stream()
                            .filter(feedback -> !FeedbackType.MANUAL_UNREFERENCED.equals(feedback.getType()) && StringUtils.hasText(feedback.getReference()))
                            .map(feedback -> new FeedbackDTO(feedback.getId(), null, null, false, feedback.getReference(), null, null, feedback.getType(), null, null)).toList();
            return ResponseEntity.ok().body(new TextExampleResultDTO(null, maskedFeedbacks, submissionDTO));
        }

        final List<FeedbackDTO> feedbackDTOs = result.getFeedbacks() == null ? List.of() : result.getFeedbacks().stream().map(FeedbackDTO::of).toList();
        return ResponseEntity.ok().body(new TextExampleResultDTO(result.getId(), feedbackDTOs, submissionDTO));
    }

    @Override
    protected String getEntityName() {
        return ENTITY_NAME;
    }

    /**
     * Maps a list of {@link FeedbackDTO} to transient {@link Feedback} entities, setting only the allowed scalar fields.
     * For feedbacks that reference a grading instruction, the managed {@link GradingInstruction} is loaded by id and attached
     * (never a detached graph).
     *
     * @param feedbackDTOs the DTOs received from the client (may be {@code null})
     * @return the mapped list, or {@code null} if the input was {@code null}
     */
    private List<Feedback> feedbacksFromDtos(final List<FeedbackDTO> feedbackDTOs) {
        if (feedbackDTOs == null) {
            return null;
        }
        return feedbackDTOs.stream().map(this::feedbackFromDto).collect(Collectors.toCollection(ArrayList::new));
    }

    private Feedback feedbackFromDto(final FeedbackDTO dto) {
        final Feedback feedback = new Feedback();
        feedback.setCredits(dto.credits());
        feedback.setDetailText(dto.detailText());
        feedback.setText(dto.text());
        feedback.setReference(dto.reference());
        feedback.setType(dto.type());
        feedback.setPositive(dto.positive());
        feedback.setVisibility(dto.visibility());
        if (dto.gradingInstruction() != null && dto.gradingInstruction().id() != null) {
            final GradingInstruction gradingInstruction = gradingInstructionRepository.findByIdElseThrow(dto.gradingInstruction().id());
            feedback.setGradingInstruction(gradingInstruction);
        }
        return feedback;
    }

    /**
     * Maps a set of {@link TextBlockDTO} to transient {@link TextBlock} entities, setting only the allowed scalar fields.
     *
     * @param textBlockDTOs the DTOs received from the client (may be {@code null})
     * @return the mapped set, or {@code null} if the input was {@code null}
     */
    private Set<TextBlock> textBlocksFromDtos(final Set<TextBlockDTO> textBlockDTOs) {
        if (textBlockDTOs == null) {
            return null;
        }
        return textBlockDTOs.stream().map(this::textBlockFromDto).collect(toSet());
    }

    private TextBlock textBlockFromDto(final TextBlockDTO dto) {
        final TextBlock textBlock = new TextBlock();
        textBlock.setId(dto.id());
        textBlock.setText(dto.text());
        textBlock.setStartIndex(dto.startIndex());
        textBlock.setEndIndex(dto.endIndex());
        if (dto.type() == TextBlockType.AUTOMATIC) {
            textBlock.automatic();
        }
        else if (dto.type() == TextBlockType.MANUAL) {
            textBlock.manual();
        }
        return textBlock;
    }

    /**
     * Adapts a dumb {@link TextAssessmentUpdateDTO} into the entity-shaped {@link AssessmentUpdateBaseDTO} expected by
     * {@link TextAssessmentService#updateAssessmentAfterComplaint}. The feedbacks are mapped to transient entities and a
     * transient {@link ComplaintResponse} is reconstructed from the client payload (lock id, response text and the nested
     * complaint's accepted flag). This mirrors the previous behavior where the shared assessment-update logic received the
     * client-sent complaint response and resolved it by id; the reconstructed graph is never persisted directly.
     *
     * @param assessmentUpdate the dumb DTO received from the client
     * @return an {@link AssessmentUpdateBaseDTO} carrying the mapped entities
     */
    private AssessmentUpdateBaseDTO assessmentUpdateFromDto(final TextAssessmentUpdateDTO assessmentUpdate) {
        final List<Feedback> feedbacks = feedbacksFromDtos(assessmentUpdate.feedbacks());
        ComplaintResponse complaintResponse = null;
        final ComplaintResponseRequestDTO complaintResponseDTO = assessmentUpdate.complaintResponse();
        if (complaintResponseDTO != null) {
            complaintResponse = new ComplaintResponse();
            complaintResponse.setId(complaintResponseDTO.id());
            complaintResponse.setResponseText(complaintResponseDTO.responseText());
            final Complaint complaint = new Complaint();
            if (complaintResponseDTO.complaint() != null) {
                complaint.setId(complaintResponseDTO.complaint().id());
                complaint.setAccepted(complaintResponseDTO.complaint().accepted());
            }
            complaintResponse.setComplaint(complaint);
        }
        return new TextAssessmentUpdateAdapter(feedbacks, complaintResponse, assessmentUpdate.assessmentNote());
    }

    /**
     * Minimal entity-shaped adapter implementing {@link AssessmentUpdateBaseDTO} so the controller can delegate to the shared
     * assessment-update logic without exposing entities at the REST boundary.
     */
    private record TextAssessmentUpdateAdapter(List<Feedback> feedbacks, ComplaintResponse complaintResponse, String assessmentNote) implements AssessmentUpdateBaseDTO {
    }

    /**
     * Save TextBlocks received from Client (if present). We need to reference them to the submission first.
     *
     * @param textBlocks     received from Client
     * @param textSubmission to associate blocks with
     * @param feedbacks      the feedbacks to associate with the blocks
     */
    private void saveTextBlocks(final Set<TextBlock> textBlocks, final TextSubmission textSubmission, final Collection<Feedback> feedbacks) {
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
    private void sendFeedbackToAthena(final TextExercise exercise, final TextSubmission textSubmission, final Collection<Feedback> feedbacks) {
        if (athenaFeedbackApi.isPresent() && exercise.areFeedbackSuggestionsEnabled()) {
            athenaFeedbackApi.get().sendFeedback(exercise, textSubmission, new ArrayList<>(feedbacks));
        }
    }
}
