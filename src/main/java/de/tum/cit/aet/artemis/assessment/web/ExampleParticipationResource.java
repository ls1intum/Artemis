
package de.tum.cit.aet.artemis.assessment.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Optional;

import jakarta.validation.Valid;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.assessment.domain.ExampleParticipation;
import de.tum.cit.aet.artemis.assessment.dto.ExampleParticipationInputDTO;
import de.tum.cit.aet.artemis.assessment.repository.ExampleParticipationRepository;
import de.tum.cit.aet.artemis.assessment.service.ExampleParticipationService;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastEditor;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastInstructor;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastTutor;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.util.HeaderUtil;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.text.api.TextSubmissionExportApi;
import de.tum.cit.aet.artemis.text.config.TextApiNotPresentException;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;

/**
 * REST controller for managing ExampleParticipation.
 */
@Profile(PROFILE_CORE)
@Lazy
@RestController
@RequestMapping("api/assessment/")
public class ExampleParticipationResource {

    private static final Logger log = LoggerFactory.getLogger(ExampleParticipationResource.class);

    private static final String ENTITY_NAME = "exampleParticipation";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final ExampleParticipationService exampleParticipationService;

    private final ExampleParticipationRepository exampleParticipationRepository;

    private final AuthorizationCheckService authCheckService;

    private final ExerciseRepository exerciseRepository;

    private final Optional<TextSubmissionExportApi> textSubmissionExportApi;

    public ExampleParticipationResource(ExampleParticipationService exampleParticipationService, ExampleParticipationRepository exampleParticipationRepository,
            AuthorizationCheckService authCheckService, ExerciseRepository exerciseRepository, Optional<TextSubmissionExportApi> textSubmissionExportApi) {
        this.exampleParticipationService = exampleParticipationService;
        this.exampleParticipationRepository = exampleParticipationRepository;
        this.authCheckService = authCheckService;
        this.exerciseRepository = exerciseRepository;
        this.textSubmissionExportApi = textSubmissionExportApi;
    }

    /**
     * POST /exercises/{exerciseId}/example-participations : Create a new exampleParticipation.
     *
     * @param exerciseId the id of the corresponding exercise for which to init a participation
     * @param dto        the example participation input DTO
     * @return the ResponseEntity with status 200 (OK) and the Result as its body, or with status 4xx if the request is invalid
     */
    @PostMapping("exercises/{exerciseId}/example-participations")
    @EnforceAtLeastEditor
    public ResponseEntity<ExampleParticipation> createExampleParticipation(@PathVariable Long exerciseId, @Valid @RequestBody ExampleParticipationInputDTO dto) {
        log.debug("REST request to save ExampleParticipation for exercise: {}", exerciseId);
        if (dto.id() != null) {
            throw new BadRequestAlertException("A new exampleParticipation cannot already have an ID", ENTITY_NAME, "idExists");
        }
        return handleExampleParticipationCreate(exerciseId, dto);
    }

    /**
     * PUT /exercises/{exerciseId}/example-participations : Updates an existing exampleParticipation. This function is called by the text editor for saving and submitting text
     * submissions. The submit specific handling occurs in the ExampleParticipationService.updateFromDTO() function.
     *
     * @param exerciseId the id of the corresponding exercise
     * @param dto        the example participation input DTO
     * @return the ResponseEntity with status 200 (OK) and with body the updated exampleParticipation, or with status 400 (Bad Request) if the exampleParticipation is not valid,
     *         or with status 500 (Internal Server Error) if the exampleParticipation couldn't be updated
     */
    @PutMapping("exercises/{exerciseId}/example-participations")
    @EnforceAtLeastEditor
    public ResponseEntity<ExampleParticipation> updateExampleParticipation(@PathVariable Long exerciseId, @Valid @RequestBody ExampleParticipationInputDTO dto) {
        log.debug("REST request to update ExampleParticipation for exercise: {}", exerciseId);
        if (dto.id() == null) {
            return createExampleParticipation(exerciseId, dto);
        }
        return handleExampleParticipationUpdate(exerciseId, dto);
    }

    /**
     * Prepare an example participation for assessment.
     * <p>
     * Currently only used for TextExercise to create automatic text blocks.
     *
     * @param exerciseId             the id of the corresponding exercise
     * @param exampleParticipationId the id of the exampleParticipation to prepare
     * @return ResponseEntity with status 200 (OK)
     */
    @PostMapping("exercises/{exerciseId}/example-participations/{exampleParticipationId}/prepare-assessment")
    @EnforceAtLeastEditor
    public ResponseEntity<Void> prepareExampleAssessment(@PathVariable Long exerciseId, @PathVariable Long exampleParticipationId) {
        log.debug("REST request to prepare ExampleParticipation for assessment : {}", exampleParticipationId);
        ExampleParticipation exampleParticipation = exampleParticipationRepository.findByIdWithEagerResultAndFeedbackElseThrow(exampleParticipationId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, exampleParticipation.getExercise(), null);
        if (!exampleParticipation.getExercise().getId().equals(exerciseId)) {
            throw new BadRequestAlertException("The exercise id in the path does not match the exercise id of the participation", ENTITY_NAME, "idsNotMatching");
        }

        // Prepare text blocks for fresh assessment
        if (exampleParticipation.getExercise().getExerciseType() == ExerciseType.TEXT && exampleParticipation.getSubmission() != null) {
            textSubmissionExportApi.orElseThrow(() -> new TextApiNotPresentException(TextSubmissionExportApi.class))
                    .prepareTextBlockForExampleSubmission(exampleParticipation.getSubmission().getId());
        }

        return ResponseEntity.ok(null);
    }

    @NonNull
    private ResponseEntity<ExampleParticipation> handleExampleParticipationCreate(Long exerciseId, ExampleParticipationInputDTO dto) {
        if (!dto.exerciseId().equals(exerciseId)) {
            throw new BadRequestAlertException("The exercise id in the path does not match the exercise id in the DTO", ENTITY_NAME, "idsNotMatching");
        }
        Exercise exercise = exerciseRepository.findByIdElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, exercise, null);
        ExampleParticipation exampleParticipation = exampleParticipationService.createFromDTO(dto, exercise);
        return ResponseEntity.ok(exampleParticipation);
    }

    @NonNull
    private ResponseEntity<ExampleParticipation> handleExampleParticipationUpdate(Long exerciseId, ExampleParticipationInputDTO dto) {
        if (!dto.exerciseId().equals(exerciseId)) {
            throw new BadRequestAlertException("The exercise id in the path does not match the exercise id in the DTO", ENTITY_NAME, "idsNotMatching");
        }
        Exercise exercise = exerciseRepository.findByIdElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, exercise, null);
        ExampleParticipation exampleParticipation = exampleParticipationService.updateFromDTO(dto);
        return ResponseEntity.ok(exampleParticipation);
    }

    /**
     * GET /example-participations/:exampleParticipationId : get the "id" exampleParticipation.
     *
     * @param exampleParticipationId the id of the exampleParticipation to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the exampleParticipation, or with status 404 (Not Found)
     */
    @GetMapping("example-participations/{exampleParticipationId}")
    @EnforceAtLeastTutor
    public ResponseEntity<ExampleParticipation> getExampleParticipation(@PathVariable Long exampleParticipationId) {
        log.debug("REST request to get ExampleParticipation : {}", exampleParticipationId);
        ExampleParticipation exampleParticipation = exampleParticipationRepository.findWithSubmissionResultExerciseGradingCriteriaById(exampleParticipationId)
                .orElseThrow(() -> new EntityNotFoundException("ExampleParticipation", exampleParticipationId));

        // For TextExercise, we need to load the text blocks as well
        if (exampleParticipation.getExercise().getExerciseType() == ExerciseType.TEXT && exampleParticipation.getSubmission() != null) {
            Optional<TextSubmission> textSubmission = textSubmissionExportApi.orElseThrow(() -> new TextApiNotPresentException(TextSubmissionExportApi.class))
                    .getSubmissionForExampleSubmission(exampleParticipation.getSubmission().getId());
            textSubmission.ifPresent(submission -> {
                exampleParticipation.getSubmissions().clear();
                exampleParticipation.addSubmission(submission);
            });
        }

        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.TEACHING_ASSISTANT, exampleParticipation.getExercise(), null);
        return ResponseEntity.ok(exampleParticipation);
    }

    /**
     * DELETE /example-participations/:exampleParticipationId : delete the "id" exampleParticipation.
     *
     * @param exampleParticipationId the id of the exampleParticipation to delete
     * @return the ResponseEntity with status 200 (OK), or with status 404 (Not Found)
     */
    @DeleteMapping("example-participations/{exampleParticipationId}")
    @EnforceAtLeastEditor
    public ResponseEntity<Void> deleteExampleParticipation(@PathVariable Long exampleParticipationId) {
        log.debug("REST request to delete ExampleParticipation : {}", exampleParticipationId);
        ExampleParticipation exampleParticipation = exampleParticipationRepository.findWithSubmissionResultExerciseGradingCriteriaById(exampleParticipationId)
                .orElseThrow(() -> new EntityNotFoundException("ExampleParticipation", exampleParticipationId));
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, exampleParticipation.getExercise(), null);
        exampleParticipationService.deleteById(exampleParticipation.getId());
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, exampleParticipationId.toString())).build();
    }

    /**
     * POST exercises/:exerciseId/example-participations/import/:sourceSubmissionId : Imports an existing student submission as an example participation.
     *
     * @param exerciseId         the id of the corresponding exercise
     * @param sourceSubmissionId the submission id to be imported as an example participation
     * @return the ResponseEntity with status 200 (OK) and the Result as its body, or with status 4xx if the request is invalid
     */
    @PostMapping("exercises/{exerciseId}/example-participations/import/{sourceSubmissionId}")
    @EnforceAtLeastInstructor
    public ResponseEntity<ExampleParticipation> importExampleParticipation(@PathVariable Long exerciseId, @PathVariable Long sourceSubmissionId) {
        log.debug("REST request to import Student Submission as ExampleParticipation : {}", sourceSubmissionId);
        Exercise exercise = exerciseRepository.findByIdElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.INSTRUCTOR, exercise, null);
        ExampleParticipation exampleParticipation = exampleParticipationService.importStudentSubmissionAsExampleParticipation(sourceSubmissionId, exercise);
        return ResponseEntity.ok(exampleParticipation);
    }
}
