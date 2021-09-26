
package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.forbidden;

import java.util.Optional;

import javax.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.ExampleSubmission;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.repository.ExampleSubmissionRepository;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.ExampleSubmissionService;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;
import tech.jhipster.web.util.ResponseUtil;

/**
 * REST controller for managing ExampleSubmission.
 */
@RestController
@RequestMapping("/api")
public class ExampleSubmissionResource {

    private final Logger log = LoggerFactory.getLogger(ExampleSubmissionResource.class);

    private static final String ENTITY_NAME = "exampleSubmission";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final ExampleSubmissionService exampleSubmissionService;

    private final ExampleSubmissionRepository exampleSubmissionRepository;

    private final AuthorizationCheckService authCheckService;

    private final ExerciseRepository exerciseRepository;

    public ExampleSubmissionResource(ExampleSubmissionService exampleSubmissionService, ExampleSubmissionRepository exampleSubmissionRepository,
            AuthorizationCheckService authCheckService, ExerciseRepository exerciseRepository) {
        this.exampleSubmissionService = exampleSubmissionService;
        this.exampleSubmissionRepository = exampleSubmissionRepository;
        this.authCheckService = authCheckService;
        this.exerciseRepository = exerciseRepository;
    }

    /**
     * POST /exercises/{exerciseId}/example-submissions : Create a new exampleSubmission.
     *
     * @param exerciseId        the id of the corresponding exercise for which to init a participation
     * @param exampleSubmission the exampleSubmission to create
     * @return the ResponseEntity with status 200 (OK) and the Result as its body, or with status 4xx if the request is invalid
     */
    @PostMapping("/exercises/{exerciseId}/example-submissions")
    @PreAuthorize("hasRole('EDITOR')")
    public ResponseEntity<ExampleSubmission> createExampleSubmission(@PathVariable Long exerciseId, @RequestBody ExampleSubmission exampleSubmission) {
        log.debug("REST request to save ExampleSubmission : {}", exampleSubmission);
        if (exampleSubmission.getId() != null) {
            throw new BadRequestAlertException("A new exampleSubmission cannot already have an ID", ENTITY_NAME, "idexists");
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
    @PutMapping("/exercises/{exerciseId}/example-submissions")
    @PreAuthorize("hasRole('EDITOR')")
    public ResponseEntity<ExampleSubmission> updateExampleSubmission(@PathVariable Long exerciseId, @RequestBody ExampleSubmission exampleSubmission) {
        log.debug("REST request to update ExampleSubmission : {}", exampleSubmission);
        if (exampleSubmission.getId() == null) {
            return createExampleSubmission(exerciseId, exampleSubmission);
        }

        return handleExampleSubmission(exerciseId, exampleSubmission);
    }

    @NotNull
    private ResponseEntity<ExampleSubmission> handleExampleSubmission(Long exerciseId, ExampleSubmission exampleSubmission) {
        if (!authCheckService.isAtLeastEditorForExercise(exampleSubmission.getExercise())) {
            return forbidden();
        }
        if (!exampleSubmission.getExercise().getId().equals(exerciseId)) {
            throw new BadRequestAlertException("The exercise id in the path does not match the exercise id of the submission", ENTITY_NAME, "idsNotMatching");
        }
        exampleSubmission = exampleSubmissionService.save(exampleSubmission);
        return ResponseEntity.ok(exampleSubmission);
    }

    /**
     * GET /example-submissions/:id : get the "id" exampleSubmission.
     *
     * @param id the id of the exampleSubmission to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the exampleSubmission, or with status 404 (Not Found)
     */
    @GetMapping("/example-submissions/{id}")
    @PreAuthorize("hasRole('TA')")
    public ResponseEntity<ExampleSubmission> getExampleSubmission(@PathVariable Long id) {
        log.debug("REST request to get ExampleSubmission : {}", id);
        Optional<ExampleSubmission> exampleSubmission = exampleSubmissionRepository.findWithSubmissionResultExerciseGradingCriteriaById(id);

        if (exampleSubmission.isPresent()) {
            if (!authCheckService.isAtLeastTeachingAssistantForExercise(exampleSubmission.get().getExercise())) {
                return forbidden();
            }
        }

        return ResponseUtil.wrapOrNotFound(exampleSubmission);
    }

    /**
     * DELETE /example-submissions/:id : delete the "id" exampleSubmission.
     *
     * @param id the id of the exampleSubmission to delete
     * @return the ResponseEntity with status 200 (OK), or with status 404 (Not Found)
     */
    @DeleteMapping("/example-submissions/{id}")
    @PreAuthorize("hasRole('EDITOR')")
    public ResponseEntity<Void> deleteExampleSubmission(@PathVariable Long id) {
        log.debug("REST request to delete ExampleSubmission : {}", id);
        Optional<ExampleSubmission> exampleSubmission = exampleSubmissionRepository.findWithSubmissionResultExerciseGradingCriteriaById(id);

        if (exampleSubmission.isEmpty()) {
            throw new EntityNotFoundException("ExampleSubmission with " + id + " was not found!");
        }

        if (!authCheckService.isAtLeastEditorForExercise(exampleSubmission.get().getExercise())) {
            return forbidden();
        }

        exampleSubmissionService.deleteById(exampleSubmission.get().getId());

        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, id.toString())).build();
    }

    /**
     * POST exercises/:exerciseId/example-submissions/import/:sourceSubmissionId : Imports an existing student submission as an example submission.
     *
     * @param exerciseId                the id of the corresponding exercise
     * @param sourceSubmissionId        the submission id to be imported as an example submission
     * @return the ResponseEntity with status 200 (OK) and the Result as its body, or with status 4xx if the request is invalid
     */
    @PostMapping("exercises/{exerciseId}/example-submissions/import/{sourceSubmissionId}")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<ExampleSubmission> importExampleSubmission(@PathVariable Long exerciseId, @PathVariable Long sourceSubmissionId) {
        log.debug("REST request to import Student Submission as ExampleSubmission : {}", sourceSubmissionId);

        Exercise exercise = exerciseRepository.findByIdElseThrow(exerciseId);

        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.INSTRUCTOR, exercise, null);

        ExampleSubmission exampleSubmission = exampleSubmissionService.importStudentSubmissionAsExampleSubmission(sourceSubmissionId, exercise);

        return ResponseEntity.ok(exampleSubmission);
    }
}
