package de.tum.cit.aet.artemis.modeling.web;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
import de.tum.cit.aet.artemis.assessment.domain.Feedback;
import de.tum.cit.aet.artemis.assessment.domain.GradingInstruction;
import de.tum.cit.aet.artemis.assessment.domain.LongFeedbackText;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.dto.AssessmentUpdateBaseDTO;
import de.tum.cit.aet.artemis.assessment.dto.FeedbackDTO;
import de.tum.cit.aet.artemis.assessment.dto.ResultDTO;
import de.tum.cit.aet.artemis.assessment.repository.ExampleSubmissionRepository;
import de.tum.cit.aet.artemis.assessment.repository.FeedbackRepository;
import de.tum.cit.aet.artemis.assessment.repository.GradingInstructionRepository;
import de.tum.cit.aet.artemis.assessment.repository.LongFeedbackTextRepository;
import de.tum.cit.aet.artemis.assessment.repository.ResultRepository;
import de.tum.cit.aet.artemis.assessment.service.AssessmentService;
import de.tum.cit.aet.artemis.assessment.web.AssessmentResource;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastInstructor;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastTutor;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.service.featureusage.FeatureUsage;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.exercise.repository.SubmissionRepository;
import de.tum.cit.aet.artemis.modeling.config.ModelingEnabled;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.modeling.domain.ModelingSubmission;
import de.tum.cit.aet.artemis.modeling.dto.ComplaintResponseRequestDTO;
import de.tum.cit.aet.artemis.modeling.dto.ModelingAssessmentDTO;
import de.tum.cit.aet.artemis.modeling.dto.ModelingAssessmentUpdateDTO;
import de.tum.cit.aet.artemis.modeling.repository.ModelingExerciseRepository;
import de.tum.cit.aet.artemis.modeling.repository.ModelingSubmissionRepository;

/**
 * REST controller for managing ModelingAssessment.
 */
@Conditional(ModelingEnabled.class)
@Lazy
@FeatureUsage("assessment/manual-assessment")
@RestController
@RequestMapping("api/modeling/")
public class ModelingAssessmentResource extends AssessmentResource {

    private static final Logger log = LoggerFactory.getLogger(ModelingAssessmentResource.class);

    private static final String ENTITY_NAME = "modelingAssessment";

    private final ModelingExerciseRepository modelingExerciseRepository;

    private final AuthorizationCheckService authCheckService;

    private final ModelingSubmissionRepository modelingSubmissionRepository;

    private final FeedbackRepository feedbackRepository;

    private final GradingInstructionRepository gradingInstructionRepository;

    private final LongFeedbackTextRepository longFeedbackTextRepository;

    public ModelingAssessmentResource(AuthorizationCheckService authCheckService, UserRepository userRepository, ModelingExerciseRepository modelingExerciseRepository,
            AssessmentService assessmentService, ModelingSubmissionRepository modelingSubmissionRepository, ExampleSubmissionRepository exampleSubmissionRepository,
            ExerciseRepository exerciseRepository, ResultRepository resultRepository, SubmissionRepository submissionRepository, FeedbackRepository feedbackRepository,
            GradingInstructionRepository gradingInstructionRepository, LongFeedbackTextRepository longFeedbackTextRepository) {
        super(authCheckService, userRepository, exerciseRepository, assessmentService, resultRepository, exampleSubmissionRepository, submissionRepository);
        this.modelingExerciseRepository = modelingExerciseRepository;
        this.authCheckService = authCheckService;
        this.modelingSubmissionRepository = modelingSubmissionRepository;
        this.feedbackRepository = feedbackRepository;
        this.gradingInstructionRepository = gradingInstructionRepository;
        this.longFeedbackTextRepository = longFeedbackTextRepository;
    }

    /**
     * Get the result of the modeling submission with the given id. See {@link AssessmentResource#getAssessmentBySubmissionId}.
     * If a resultId is provided, retrieves that specific result with authorization and sensitive data filtering applied.
     *
     * @param submissionId the id of the submission that should be sent to the client
     * @param resultId     optional id of a specific result to retrieve; if not provided, returns the latest result
     * @return the assessment of the given submission
     */
    @GetMapping("modeling-submissions/{submissionId}/result")
    @EnforceAtLeastStudent
    public ResponseEntity<ResultDTO> getAssessmentBySubmissionId(@PathVariable Long submissionId, @RequestParam(value = "resultId", required = false) Long resultId) {
        if (resultId != null) {
            log.debug("REST request to get result {} for modeling submission {}", resultId, submissionId);
            ModelingSubmission submission = modelingSubmissionRepository
                    .findByIdWithEagerResultAndFeedbackAndAssessorAndAssessmentNoteAndParticipationResultsElseThrow(submissionId);
            Result result = submission.getResults().stream().filter(r -> r.getId().equals(resultId)).findFirst().orElseThrow(() -> new EntityNotFoundException("Result", resultId));

            if (!(submission.getParticipation() instanceof StudentParticipation participation)) {
                throw new AccessForbiddenException();
            }
            ModelingExercise exercise = modelingExerciseRepository.findByIdElseThrow(participation.getExercise().getId());

            if (!authCheckService.isUserAllowedToGetResult(exercise, participation, result)) {
                throw new AccessForbiddenException();
            }

            if (!authCheckService.isAtLeastTeachingAssistantForExercise(exercise)) {
                exercise.filterSensitiveInformation();
                result.filterSensitiveInformation();
            }

            return ResponseEntity.ok(ResultDTO.of(result));
        }
        ResponseEntity<Result> response = super.getAssessmentBySubmissionId(submissionId);
        return ResponseEntity.status(response.getStatusCode()).headers(response.getHeaders()).body(ResultDTO.of(response.getBody()));
    }

    /**
     * Retrieve the result for an example submission, only if the user is an instructor or if the example submission is not used for tutorial purposes.
     *
     * @param exerciseId   the id of the exercise
     * @param submissionId the id of the example submission
     * @return the result linked to the example submission
     */
    @GetMapping({ "exercises/{exerciseId}/modeling-submissions/{submissionId}/example-assessment", "exercise/{exerciseId}/modeling-submissions/{submissionId}/example-assessment" })
    @EnforceAtLeastTutor
    public ResponseEntity<ResultDTO> getModelingExampleAssessment(@PathVariable long exerciseId, @PathVariable long submissionId) {
        log.debug("REST request to get example assessment for tutors text assessment: {}", submissionId);
        ResponseEntity<Result> response = super.getExampleAssessment(exerciseId, submissionId);
        return ResponseEntity.status(response.getStatusCode()).headers(response.getHeaders()).body(ResultDTO.of(response.getBody()));
    }

    /**
     * PUT modeling-submissions/:submissionId/result/resultId/assessment : save manual modeling assessment. See {@link AssessmentResource#saveAssessment}.
     *
     * @param submissionId       id of the submission
     * @param resultId           id of the result
     * @param submit             if true the assessment is submitted, else only saved
     * @param modelingAssessment the DTO containing the list of feedbacks and the assessment note, if one exists
     * @return result after saving/submitting modeling assessment
     */
    @ResponseStatus(HttpStatus.OK)
    @PutMapping({ "modeling-submissions/{submissionId}/results/{resultId}/assessment", "modeling-submissions/{submissionId}/result/{resultId}/assessment" })
    @EnforceAtLeastTutor
    public ResponseEntity<ResultDTO> saveModelingAssessment(@PathVariable long submissionId, @PathVariable long resultId,
            @RequestParam(value = "submit", defaultValue = "false") boolean submit, @RequestBody ModelingAssessmentDTO modelingAssessment) {
        Submission submission = submissionRepository.findOneWithEagerResultAndFeedbackAndAssessmentNote(submissionId);
        final List<Feedback> feedbacks = feedbacksFromDtos(modelingAssessment.feedbacks());
        ResponseEntity<Result> response = super.saveAssessment(submission, submit, feedbacks, resultId, modelingAssessment.assessmentNote());
        return ResponseEntity.status(response.getStatusCode()).headers(response.getHeaders()).body(ResultDTO.of(response.getBody()));
    }

    /**
     * PUT modeling-submissions/:submissionId/example-assessment : save manual example modeling assessment
     *
     * @param exampleSubmissionId id of the example submission
     * @param feedbacks           list of feedbacks
     * @return result after saving example modeling assessment
     */
    @ResponseStatus(HttpStatus.OK)
    @PutMapping("modeling-submissions/{submissionId}/example-assessment")
    @EnforceAtLeastTutor
    public ResponseEntity<ResultDTO> saveModelingExampleAssessment(@PathVariable("submissionId") long exampleSubmissionId, @RequestBody List<FeedbackDTO> feedbacks) {
        log.debug("REST request to save modeling example assessment : {}", exampleSubmissionId);
        Result result = saveExampleAssessment(exampleSubmissionId, feedbacksFromDtos(feedbacks));
        return ResponseEntity.ok(ResultDTO.of(result));
    }

    /**
     * Update an assessment after a complaint was accepted.
     *
     * @param submissionId     the id of the submission for which the assessment should be updated
     * @param assessmentUpdate the assessment update containing the new feedback items and the response to the complaint
     * @return the updated result
     */
    @ResponseStatus(HttpStatus.OK)
    @PutMapping("modeling-submissions/{submissionId}/assessment-after-complaint")
    @EnforceAtLeastTutor
    public ResponseEntity<ResultDTO> updateModelingAssessmentAfterComplaint(@PathVariable Long submissionId, @RequestBody ModelingAssessmentUpdateDTO assessmentUpdate) {
        log.debug("REST request to update the assessment of submission {} after complaint.", submissionId);
        User user = userRepository.getUserWithAuthorities();
        ModelingSubmission modelingSubmission = modelingSubmissionRepository.findByIdWithEagerResultAndFeedbackElseThrow(submissionId);
        long exerciseId = modelingSubmission.getParticipation().getExercise().getId();
        ModelingExercise modelingExercise = modelingExerciseRepository.findByIdElseThrow(exerciseId);
        checkAuthorization(modelingExercise, user);

        final AssessmentUpdateBaseDTO assessmentUpdateEntities = assessmentUpdateFromDto(assessmentUpdate);
        Result result = assessmentService.updateAssessmentAfterComplaint(modelingSubmission.getLatestResult(), modelingExercise, assessmentUpdateEntities);

        var participation = result.getSubmission().getParticipation();
        // remove circular dependencies if the results of the participation are there

        if (participation instanceof StudentParticipation studentParticipation && !authCheckService.isAtLeastInstructorForExercise(modelingExercise, user)) {
            studentParticipation.setParticipant(null);
        }

        return ResponseEntity.ok(ResultDTO.of(result));
    }

    /**
     * Cancel an assessment of a given submission for the current user, i.e. delete the corresponding result / release the lock. Then the submission is available for assessment
     * again.
     *
     * @param submissionId the id of the submission for which the current assessment should be canceled
     * @param resultId     the id of the result to cancel; without it the newest correction round is released
     * @return 200 Ok response if canceling was successful, 403 Forbidden if current user is not the assessor of the submission
     */
    @PutMapping("modeling-submissions/{submissionId}/cancel-assessment")
    @EnforceAtLeastTutor
    public ResponseEntity<Void> cancelAssessment(@PathVariable Long submissionId, @RequestParam(value = "resultId", required = false) Long resultId) {
        return super.cancelAssessment(submissionId, resultId);
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
    @DeleteMapping("participations/{participationId}/modeling-submissions/{submissionId}/results/{resultId}")
    @EnforceAtLeastInstructor
    public ResponseEntity<Void> deleteAssessment(@PathVariable Long participationId, @PathVariable Long submissionId, @PathVariable Long resultId) {
        return super.deleteAssessment(participationId, submissionId, resultId);
    }

    @Override
    protected String getEntityName() {
        return ENTITY_NAME;
    }

    /**
     * Maps a list of {@link FeedbackDTO} to transient {@link Feedback} entities, setting only the allowed scalar fields.
     * All distinct grading instruction IDs referenced by the incoming DTOs are loaded in a single batch query before
     * the per-item mapping loop, so the conversion stays constant-query for large assessments.
     *
     * @param feedbackDTOs the DTOs received from the client (may be {@code null})
     * @return the mapped list, never {@code null}
     */
    private List<Feedback> feedbacksFromDtos(final List<FeedbackDTO> feedbackDTOs) {
        // Mirror the previous behavior where the deserialized feedback list was never null: an omitted/null feedbacks field
        // must map to an empty list, not null, so the save path clears the existing feedback instead of NPEing.
        if (feedbackDTOs == null) {
            return new ArrayList<>();
        }

        List<Long> longFeedbackIds = feedbackDTOs.stream().filter(dto -> dto.id() != null && dto.hasLongFeedbackText()).map(FeedbackDTO::id).distinct().toList();
        Map<Long, Feedback> storedFeedbacksById;
        Map<Long, String> storedLongTextsById;
        if (longFeedbackIds.isEmpty()) {
            storedFeedbacksById = Map.of();
            storedLongTextsById = Map.of();
        }
        else {
            storedFeedbacksById = feedbackRepository.findAllById(longFeedbackIds).stream()
                    .collect(Collectors.toMap(Feedback::getId, feedback -> feedback, (first, second) -> first));
            storedLongTextsById = longFeedbackTextRepository.findByFeedbackIds(longFeedbackIds).stream()
                    .collect(Collectors.toMap(longFeedback -> longFeedback.getFeedback().getId(), LongFeedbackText::getText, (first, second) -> first));
        }

        List<Long> gradingInstructionIds = feedbackDTOs.stream().filter(dto -> dto.gradingInstruction() != null && dto.gradingInstruction().id() != null)
                .map(dto -> dto.gradingInstruction().id()).distinct().toList();
        Map<Long, GradingInstruction> gradingInstructionsById = gradingInstructionIds.isEmpty() ? Map.of()
                : gradingInstructionRepository.findAllById(gradingInstructionIds).stream()
                        .collect(Collectors.toMap(GradingInstruction::getId, instruction -> instruction, (first, second) -> first));

        return feedbackDTOs.stream().map(dto -> feedbackFromDto(dto, storedFeedbacksById, storedLongTextsById, gradingInstructionsById))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private Feedback feedbackFromDto(final FeedbackDTO dto, Map<Long, Feedback> storedFeedbacksById, Map<Long, String> storedLongTextsById,
            Map<Long, GradingInstruction> gradingInstructionsById) {
        final Feedback feedback = new Feedback();
        // Preserve the id so an existing feedback is matched (not recreated) on re-save; the long-feedback persistence
        // and cleanup paths (ResultService) key on feedback id.
        feedback.setId(dto.id());
        feedback.setCredits(dto.credits());
        feedback.setDetailText(detailTextFromDto(dto, storedFeedbacksById, storedLongTextsById));
        feedback.setText(dto.text());
        feedback.setReference(dto.reference());
        feedback.setType(dto.type());
        feedback.setPositive(dto.positive());
        feedback.setVisibility(dto.visibility());
        if (dto.gradingInstruction() != null && dto.gradingInstruction().id() != null) {
            final Long instructionId = dto.gradingInstruction().id();
            // Preserves the not-found semantics of the previous findByIdElseThrow call; any ID not in the batch is absent.
            final GradingInstruction gradingInstruction = gradingInstructionsById.get(instructionId);
            if (gradingInstruction == null) {
                throw new EntityNotFoundException("GradingInstruction", instructionId);
            }
            feedback.setGradingInstruction(gradingInstruction);
        }
        return feedback;
    }

    private String detailTextFromDto(final FeedbackDTO dto, Map<Long, Feedback> storedFeedbacksById, Map<Long, String> storedLongTextsById) {
        if (!dto.hasLongFeedbackText() || dto.id() == null) {
            return dto.detailText();
        }

        // The read DTO can carry only the stored preview of long feedback, but the editor loads the full long text before
        // changes. Restore old full texts only for preview-only saves; otherwise preserve the user's edited detail text.
        // Both maps are loaded once per request so this conversion stays constant-query for large assessments.
        final boolean dtoContainsOnlyPreview = storedFeedbacksById.containsKey(dto.id()) && Objects.equals(dto.detailText(), storedFeedbacksById.get(dto.id()).getDetailText());
        if (dto.detailText() == null || dtoContainsOnlyPreview) {
            return storedLongTextsById.getOrDefault(dto.id(), dto.detailText());
        }
        return dto.detailText();
    }

    /**
     * Adapts a dumb {@link ModelingAssessmentUpdateDTO} into the entity-shaped {@link AssessmentUpdateBaseDTO} expected by
     * {@link AssessmentService#updateAssessmentAfterComplaint}. The feedbacks are mapped to transient entities and a transient
     * {@link ComplaintResponse} is reconstructed from the client payload (lock id, response text and the nested complaint's
     * accepted flag). This mirrors the previous behavior where the shared assessment-update logic received the client-sent
     * complaint response and resolved it by id; the reconstructed graph is never persisted directly.
     *
     * @param assessmentUpdate the dumb DTO received from the client
     * @return an {@link AssessmentUpdateBaseDTO} carrying the mapped entities
     */
    private AssessmentUpdateBaseDTO assessmentUpdateFromDto(final ModelingAssessmentUpdateDTO assessmentUpdate) {
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
        return new ModelingAssessmentUpdateAdapter(feedbacks, complaintResponse, assessmentUpdate.assessmentNote());
    }

    /**
     * Minimal entity-shaped adapter implementing {@link AssessmentUpdateBaseDTO} so the controller can delegate to the shared
     * assessment-update logic without exposing entities at the REST boundary.
     */
    private record ModelingAssessmentUpdateAdapter(List<Feedback> feedbacks, ComplaintResponse complaintResponse, String assessmentNote) implements AssessmentUpdateBaseDTO {
    }
}
