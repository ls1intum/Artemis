package de.tum.in.www1.artemis.web.rest;

import de.tum.in.www1.artemis.domain.ExampleSubmission;
import de.tum.in.www1.artemis.service.ExampleSubmissionService;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import io.github.jhipster.web.util.ResponseUtil;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

/**
 * REST controller for managing ExampleSubmission.
 */
@RestController
@RequestMapping("/api")
public class ExampleSubmissionResource {

    private static final String ENTITY_NAME = "exampleSubmission";
    private final Logger log = LoggerFactory.getLogger(ExampleSubmissionResource.class);
    private final ExampleSubmissionService exampleSubmissionService;

    public ExampleSubmissionResource(ExampleSubmissionService exampleSubmissionService) {
        this.exampleSubmissionService = exampleSubmissionService;
    }

    /**
     * POST  /exercises/{exerciseId}/example-submissions : Create a new exampleSubmission.
     *
     * @param exerciseId     the id of the exercise for which to init a participation
     * @param exampleSubmission the exampleSubmission to create
     * @return the ResponseEntity with status 200 (OK) and the Result as its body, or with status 4xx if the request is invalid
     */
    @PostMapping("/exercises/{exerciseId}/example-submissions")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public ResponseEntity<ExampleSubmission> createExampleSubmission(@PathVariable Long exerciseId, @RequestBody ExampleSubmission exampleSubmission) {
        log.debug("REST request to save ExampleSubmission : {}", exampleSubmission);
        if (exampleSubmission.getId() != null) {
            throw new BadRequestAlertException("A new exampleSubmission cannot already have an ID", ENTITY_NAME, "idexists");
        }

        return handleExampleSubmission(exerciseId, exampleSubmission);
    }

    /**
     * PUT  /exercises/{exerciseId}/example-submissions : Updates an existing exampleSubmission.
     * This function is called by the text editor for saving and submitting text submissions.
     * The submit specific handling occurs in the ExampleSubmissionService.save() function.
     *
     * @param exerciseId     the id of the exercise for which to init a participation
     * @param exampleSubmission the exampleSubmission to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated exampleSubmission,
     * or with status 400 (Bad Request) if the exampleSubmission is not valid,
     * or with status 500 (Internal Server Error) if the exampleSubmission couldn't be updated
     */
    @PutMapping("/exercises/{exerciseId}/example-submissions")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    @Transactional
    public ResponseEntity<ExampleSubmission> updateExampleSubmission(@PathVariable Long exerciseId, @RequestBody ExampleSubmission exampleSubmission) {
        log.debug("REST request to update ExampleSubmission : {}", exampleSubmission);
        if (exampleSubmission.getId() == null) {
            return createExampleSubmission(exerciseId, exampleSubmission);
        }

        return handleExampleSubmission(exerciseId, exampleSubmission);
    }

    @NotNull
    private ResponseEntity<ExampleSubmission> handleExampleSubmission(@PathVariable Long exerciseId, @RequestBody ExampleSubmission exampleSubmission) {
        // update and save submission
        exampleSubmission = exampleSubmissionService.save(exampleSubmission);
        return ResponseEntity.ok(exampleSubmission);
    }

    /**
     * GET  /example-submissions/:id : get the "id" exampleSubmission.
     *
     * @param id the id of the exampleSubmission to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the exampleSubmission, or with status 404 (Not Found)
     */
    @GetMapping("/example-submissions/{id}")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<ExampleSubmission> getExampleSubmission(@PathVariable Long id) {
        log.debug("REST request to get ExampleSubmission : {}", id);
        Optional<ExampleSubmission> exampleSubmission = exampleSubmissionService.get(id);
        return ResponseUtil.wrapOrNotFound(exampleSubmission);
    }
}
