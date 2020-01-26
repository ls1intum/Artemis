package de.tum.in.www1.artemis.web.rest;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.GradingCriteria;
import de.tum.in.www1.artemis.domain.GradingInstruction;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.ExerciseService;
import de.tum.in.www1.artemis.service.GradingCriteriaService;
import de.tum.in.www1.artemis.service.GradingInstructionService;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.forbidden;

/**
 * REST controller for managing Grading Criteria.
 */
@RestController
@RequestMapping({ GradingCriteriaResource.Endpoints.ROOT })
public class GradingCriteriaResource {

    private final Logger log = LoggerFactory.getLogger(GradingCriteriaResource.class);

    private static final String ENTITY_NAME = "gradingInstruction";

    @Value("${jhipster.clientApp.name}")
    private String APPLICATION_NAME;

    private final GradingCriteriaService gradingCriteriaService;

    private final ExerciseService exerciseService;

    private final AuthorizationCheckService authCheckService;

    public GradingCriteriaResource(GradingCriteriaService gradingCriteriaService, ExerciseService exerciseService, AuthorizationCheckService authCheckService) {
        this.gradingCriteriaService = gradingCriteriaService;
        this.exerciseService = exerciseService;
        this.authCheckService = authCheckService;
    }

    /**
     * GET /exercises/:exerciseId/grading-criteria : get the "id" exercise.
     *
     * @param exerciseId the id of the exercise to retrieve its grading criteria
     * @return the ResponseEntity with status 200 (OK) and with body the exercise, or with status 404 (Not Found)
     */
    @GetMapping(GradingCriteriaResource.Endpoints.GRADING_CRITERIA_OF_EXERCISE)
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<List<GradingCriteria>> getGradingCriteriaByExerciseId(@PathVariable long exerciseId) {
        log.debug("REST request to get Exercise : {}", exerciseId);

        Exercise exercise = exerciseService.findOne(exerciseId);

        if (!authCheckService.isAtLeastTeachingAssistantForExercise(exercise)) {
            return forbidden();
        }
        List<GradingCriteria> gradingCriteria = gradingCriteriaService.findAllForExercise(exercise);
        return ResponseEntity.ok(gradingCriteria);
    }

    /**
     * POST /grading-criteria : Create a new gradingCriteria.
     *
     * @param gradingCriteria the gradingCriteria to create
     * @return the ResponseEntity with status 201 (Created) and with body the new gradingCriteria, or with status 400 (Bad Request) if the gradingCriteria has already an ID
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping(GradingCriteriaResource.Endpoints.GRADING_CRITERIA)
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<GradingCriteria> createGradingCriteria(@RequestBody GradingCriteria gradingCriteria) throws URISyntaxException {
        log.debug("REST request to save GradingCriteria : {}", gradingCriteria);
        if (gradingCriteria.getId() != null) {
            return ResponseEntity.badRequest()
                .headers(HeaderUtil.createFailureAlert(APPLICATION_NAME, true, ENTITY_NAME, "idexists", "A new gradingCriteria cannot already have an ID")).body(null);
        }

        // fetch exercise from database to make sure client didn't change groups
        Exercise exercise = exerciseService.findOne(gradingCriteria.getExercise().getId());
        if (!authCheckService.isAtLeastInstructorForExercise(exercise)) {
            return forbidden();
        }
        gradingCriteria = gradingCriteriaService.save(gradingCriteria);
        return ResponseEntity.created(new URI(GradingCriteriaResource.Endpoints.ROOT + GradingCriteriaResource.Endpoints.GRADING_CRITERIA + "/" + gradingCriteria.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(APPLICATION_NAME, true, ENTITY_NAME, gradingCriteria.getId().toString())).body(gradingCriteria);

    }

    /**
     * PUT /grading-criteria : Updates an existing gradingCriteria.
     *
     * @param gradingCriteria the gradingCriteria to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated gradingCriteria, or with status 400 (Bad Request) if the gradingCriteria is not valid, or with status 500
     * (Internal Server Error) if the gradingCriteria couldn't be updated
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PutMapping(GradingCriteriaResource.Endpoints.GRADING_CRITERIA)
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<GradingCriteria> updateGradingCriteria(@RequestBody GradingCriteria gradingCriteria) throws URISyntaxException {
        log.debug("REST request to update GradingCriteria : {}", gradingCriteria);
        if (gradingCriteria.getId() == null) {
            return createGradingCriteria(gradingCriteria);
        }

        // fetch exercise from database to make sure client didn't change groups
        Exercise exercise = exerciseService.findOne(gradingCriteria.getExercise().getId());
        if (!authCheckService.isAtLeastInstructorForExercise(exercise)) {
            return forbidden();
        }
        gradingCriteria = gradingCriteriaService.save(gradingCriteria);

        return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(APPLICATION_NAME, true, ENTITY_NAME, gradingCriteria.getId().toString())).body(gradingCriteria);
    }

    /**
     * DELETE /grading-criteria/:gradingCriteriaId : delete the "id" gradingCriteria.
     *
     * @param gradingCriteriaId the id of the gradingCriteria to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping(GradingCriteriaResource.Endpoints.GRADING_CRITERIA)
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Void> deleteGradingCriteria(@PathVariable long gradingCriteriaId) {
        log.debug("REST request to delete GradingCriteria : {}", gradingCriteriaId);

        GradingCriteria gradingCriteria = gradingCriteriaService.findOne(gradingCriteriaId);
        Exercise exercise = exerciseService.findOne(gradingCriteria.getExercise().getId());
        if (!authCheckService.isAtLeastInstructorForExercise(exercise)) {
            return forbidden();
        }
        gradingCriteriaService.delete(gradingCriteria);

        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(APPLICATION_NAME, true, ENTITY_NAME, gradingCriteriaId + "")).build();
    }

    public static final class Endpoints {

        public static final String ROOT = "/api";

        public static final String GRADING_CRITERIA = "/grading-criteria";

        public static final String GRADING_CRITERION = GRADING_CRITERIA + "/{gradingCriteriaId}";

        public static final String EXERCISES = "/exercises";

        public static final String EXERCISE = EXERCISES + "/{exerciseId}";

        public static final String GRADING_CRITERIA_OF_EXERCISE = EXERCISE + GRADING_CRITERIA;

        private Endpoints() {
        }
    }
}
