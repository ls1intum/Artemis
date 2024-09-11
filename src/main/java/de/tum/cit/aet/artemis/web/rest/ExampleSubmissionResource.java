
package de.tum.cit.aet.artemis.web.rest;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Optional;

import jakarta.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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

import de.tum.cit.aet.artemis.assessment.domain.ExampleSubmission;
import de.tum.cit.aet.artemis.assessment.repository.ExampleSubmissionRepository;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastEditor;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastInstructor;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastTutor;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.service.ExampleSubmissionService;
import de.tum.cit.aet.artemis.service.TextBlockService;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;
import de.tum.cit.aet.artemis.text.repository.TextSubmissionRepository;
import de.tum.cit.aet.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.cit.aet.artemis.web.rest.errors.EntityNotFoundException;
import de.tum.cit.aet.artemis.web.rest.util.HeaderUtil;

/**
 * REST controller for managing ExampleSubmission.
 */
@Profile(PROFILE_CORE)
@RestController
@RequestMapping("api/")
public class ExampleSubmissionResource {

    private static final Logger log = LoggerFactory.getLogger(ExampleSubmissionResource.class);

    private static final String ENTITY_NAME = "exampleSubmission";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final ExampleSubmissionService exampleSubmissionService;

    private final ExampleSubmissionRepository exampleSubmissionRepository;

    private final AuthorizationCheckService authCheckService;

    private final ExerciseRepository exerciseRepository;

    private final TextSubmissionRepository textSubmissionRepository;

    private final TextBlockService textBlockService;

    public ExampleSubmissionResource(ExampleSubmissionService exampleSubmissionService, ExampleSubmissionRepository exampleSubmissionRepository,
            AuthorizationCheckService authCheckService, ExerciseRepository exerciseRepository, TextSubmissionRepository textSubmissionRepository,
            TextBlockService textBlockService) {
        this.exampleSubmissionService = exampleSubmissionService;
        this.exampleSubmissionRepository = exampleSubmissionRepository;
        this.authCheckService = authCheckService;
        this.exerciseRepository = exerciseRepository;
        this.textSubmissionRepository = textSubmissionRepository;
        this.textBlockService = textBlockService;
    }

    /**
     * POST /exercises/{exerciseId}/example-submissions : Create a new exampleSubmission.
     *
     * @param exerciseId        the id of the corresponding exercise for which to init a participation
     * @param exampleSubmission the exampleSubmission to create
     * @return the ResponseEntity with status 200 (OK) and the Result as its body, or with status 4xx if the request is invalid
     */
    @PostMapping("exercises/{exerciseId}/example-submissions")
    @EnforceAtLeastEditor
    public ResponseEntity<ExampleSubmission> createExampleSubmission(@PathVariable Long exerciseId, @RequestBody ExampleSubmission exampleSubmission) {
        log.debug("REST request to save ExampleSubmission : {}", exampleSubmission);
        if (exampleSubmission.getId() != null) {
            throw new BadRequestAlertException("A new exampleSubmission cannot already have an ID", ENTITY_NAME, "idExists");
        }
        return handleExampleSubmission(exerciseId, exampleSubmission);
    }

    /**
     * PUT /exercises/{exerciseId}/example-submissions : Updates an existing exampleSubmission. This function is called by the text editor for saving and submitting text
     * submissions. The submit specific handling occurs in the ExampleSubmissionService.save() function.
     *
     * @param exerciseId        the id of the corresponding exercise
     * @param exampleSubmission the exampleSubmission to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated exampleSubmission, or with status 400 (Bad Request) if the exampleSubmission is not valid, or with
     *         status 500 (Internal Server Error) if the exampleSubmission couldn't be updated
     */
    @PutMapping("exercises/{exerciseId}/example-submissions")
    @EnforceAtLeastEditor
    public ResponseEntity<ExampleSubmission> updateExampleSubmission(@PathVariable Long exerciseId, @RequestBody ExampleSubmission exampleSubmission) {
        log.debug("REST request to update ExampleSubmission : {}", exampleSubmission);
        if (exampleSubmission.getId() == null) {
            return createExampleSubmission(exerciseId, exampleSubmission);
        }
        return handleExampleSubmission(exerciseId, exampleSubmission);
    }

    /**
     * Prepare an example submission for assessment.
     * <p>
     * Currently only used for {@link TextExercise TextExercise} to create automatic text blocks.
     *
     * @param exerciseId          the id of the corresponding exercise
     * @param exampleSubmissionId the id of the exampleSubmission to prepare
     * @return ResponseEntity with status 200 (OK)
     */
    @PostMapping("exercises/{exerciseId}/example-submissions/{exampleSubmissionId}/prepare-assessment")
    @EnforceAtLeastEditor
    public ResponseEntity<Void> prepareExampleAssessment(@PathVariable Long exerciseId, @PathVariable Long exampleSubmissionId) {
        log.debug("REST request to prepare ExampleSubmission for assessment : {}", exampleSubmissionId);
        ExampleSubmission exampleSubmission = exampleSubmissionRepository.findByIdWithEagerResultAndFeedbackElseThrow(exampleSubmissionId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, exampleSubmission.getExercise(), null);
        if (!exampleSubmission.getExercise().getId().equals(exerciseId)) {
            throw new BadRequestAlertException("The exercise id in the path does not match the exercise id of the submission", ENTITY_NAME, "idsNotMatching");
        }

        // Prepare text blocks for fresh assessment
        if (exampleSubmission.getExercise().getExerciseType() == ExerciseType.TEXT && exampleSubmission.getSubmission() != null) {
            Optional<TextSubmission> textSubmission = textSubmissionRepository.findWithEagerResultsAndFeedbackAndTextBlocksById(exampleSubmission.getSubmission().getId());
            if (textSubmission.isPresent() && textSubmission.get().getLatestResult() == null
                    && (textSubmission.get().getBlocks() == null || textSubmission.get().getBlocks().isEmpty())) {
                TextSubmission submission = textSubmission.get();
                textBlockService.computeTextBlocksForSubmissionBasedOnSyntax(submission);
                textBlockService.saveAll(submission.getBlocks());
            }
        }

        return ResponseEntity.ok(null);
    }

    @NotNull
    private ResponseEntity<ExampleSubmission> handleExampleSubmission(Long exerciseId, ExampleSubmission exampleSubmission) {
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, exampleSubmission.getExercise(), null);
        if (!exampleSubmission.getExercise().getId().equals(exerciseId)) {
            throw new BadRequestAlertException("The exercise id in the path does not match the exercise id of the submission", ENTITY_NAME, "idsNotMatching");
        }
        exampleSubmission = exampleSubmissionService.save(exampleSubmission);
        return ResponseEntity.ok(exampleSubmission);
    }

    /**
     * GET /example-submissions/:exampleSubmissionId : get the "id" exampleSubmission.
     *
     * @param exampleSubmissionId the id of the exampleSubmission to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the exampleSubmission, or with status 404 (Not Found)
     */
    @GetMapping("example-submissions/{exampleSubmissionId}")
    @EnforceAtLeastTutor
    public ResponseEntity<ExampleSubmission> getExampleSubmission(@PathVariable Long exampleSubmissionId) {
        log.debug("REST request to get ExampleSubmission : {}", exampleSubmissionId);
        ExampleSubmission exampleSubmission = exampleSubmissionRepository.findWithSubmissionResultExerciseGradingCriteriaById(exampleSubmissionId)
                .orElseThrow(() -> new EntityNotFoundException("ExampleSubmission", exampleSubmissionId));

        // For TextExercise, we need to load the text blocks as well
        if (exampleSubmission.getExercise().getExerciseType() == ExerciseType.TEXT && exampleSubmission.getSubmission() != null) {
            Optional<TextSubmission> textSubmission = textSubmissionRepository.findWithEagerResultsAndFeedbackAndTextBlocksById(exampleSubmission.getSubmission().getId());
            textSubmission.ifPresent(exampleSubmission::setSubmission);
        }

        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.TEACHING_ASSISTANT, exampleSubmission.getExercise(), null);
        return ResponseEntity.ok(exampleSubmission);
    }

    /**
     * DELETE /example-submissions/:exampleSubmissionId : delete the "id" exampleSubmission.
     *
     * @param exampleSubmissionId the id of the exampleSubmission to delete
     * @return the ResponseEntity with status 200 (OK), or with status 404 (Not Found)
     */
    @DeleteMapping("example-submissions/{exampleSubmissionId}")
    @EnforceAtLeastEditor
    public ResponseEntity<Void> deleteExampleSubmission(@PathVariable Long exampleSubmissionId) {
        log.debug("REST request to delete ExampleSubmission : {}", exampleSubmissionId);
        ExampleSubmission exampleSubmission = exampleSubmissionRepository.findWithSubmissionResultExerciseGradingCriteriaById(exampleSubmissionId)
                .orElseThrow(() -> new EntityNotFoundException("ExampleSubmission", exampleSubmissionId));
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, exampleSubmission.getExercise(), null);
        exampleSubmissionService.deleteById(exampleSubmission.getId());
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, exampleSubmissionId.toString())).build();
    }

    /**
     * POST exercises/:exerciseId/example-submissions/import/:sourceSubmissionId : Imports an existing student submission as an example submission.
     *
     * @param exerciseId         the id of the corresponding exercise
     * @param sourceSubmissionId the submission id to be imported as an example submission
     * @return the ResponseEntity with status 200 (OK) and the Result as its body, or with status 4xx if the request is invalid
     */
    @PostMapping("exercises/{exerciseId}/example-submissions/import/{sourceSubmissionId}")
    @EnforceAtLeastInstructor
    public ResponseEntity<ExampleSubmission> importExampleSubmission(@PathVariable Long exerciseId, @PathVariable Long sourceSubmissionId) {
        log.debug("REST request to import Student Submission as ExampleSubmission : {}", sourceSubmissionId);
        Exercise exercise = exerciseRepository.findByIdElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.INSTRUCTOR, exercise, null);
        ExampleSubmission exampleSubmission = exampleSubmissionService.importStudentSubmissionAsExampleSubmission(sourceSubmissionId, exercise);
        return ResponseEntity.ok(exampleSubmission);
    }
}
