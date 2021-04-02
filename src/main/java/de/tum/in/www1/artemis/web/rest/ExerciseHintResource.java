package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.*;

import java.net.*;
import java.util.Set;

import org.slf4j.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import io.github.jhipster.web.util.HeaderUtil;

/**
 * REST controller for managing {@link de.tum.in.www1.artemis.domain.ExerciseHint}.
 */
@RestController
@RequestMapping("/api")
public class ExerciseHintResource {

    private final Logger log = LoggerFactory.getLogger(ExerciseHintResource.class);

    private static final String ENTITY_NAME = "exerciseHint";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final ExerciseHintRepository exerciseHintRepository;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final AuthorizationCheckService authCheckService;

    private final ExerciseRepository exerciseRepository;

    public ExerciseHintResource(ExerciseHintRepository exerciseHintRepository, AuthorizationCheckService authCheckService,
            ProgrammingExerciseRepository programmingExerciseRepository, UserRepository userRepository, ExerciseRepository exerciseRepository) {
        this.exerciseHintRepository = exerciseHintRepository;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.authCheckService = authCheckService;
        this.exerciseRepository = exerciseRepository;
    }

    /**
     * {@code POST  /exercise-hints} : Create a new exerciseHint.
     *
     * @param exerciseHint the exerciseHint to create.
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new exerciseHint, or with status {@code 400 (Bad Request)} if the exerciseHint has already an ID.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PostMapping("/exercise-hints")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<ExerciseHint> createExerciseHint(@RequestBody ExerciseHint exerciseHint) throws URISyntaxException {
        log.debug("REST request to save ExerciseHint : {}", exerciseHint);
        if (exerciseHint.getExercise() == null) {
            return badRequest();
        }
        // Reload the exercise from the database as we can't trust data from the client
        Exercise exercise = exerciseRepository.findByIdElseThrow(exerciseHint.getExercise().getId());

        // Hints for exam exercises are not supported at the moment
        if (exercise.isExamExercise()) {
            return forbidden();
        }
        authCheckService.checkIsAtLeastTeachingAssistantForExerciseElseThrow(exercise, null);
        ExerciseHint result = exerciseHintRepository.save(exerciseHint);
        return ResponseEntity.created(new URI("/api/exercise-hints/" + result.getId()))
                .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, result.getId().toString())).body(result);
    }

    /**
     * {@code PUT  /exercise-hints/{id}} : Updates an existing exerciseHint.
     *
     * @param exerciseHint the exerciseHint to update.
     * @param exerciseHintId  the id to the exerciseHint
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated exerciseHint,
     * or with status {@code 400 (Bad Request)} if the exerciseHint is not valid,
     * or with status {@code 500 (Internal Server Error)} if the exerciseHint couldn't be updated.
     */
    @PutMapping("/exercise-hints/{exerciseHintId}")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<ExerciseHint> updateExerciseHint(@RequestBody ExerciseHint exerciseHint, @PathVariable Long exerciseHintId) {
        log.debug("REST request to update ExerciseHint : {}", exerciseHint);
        if (exerciseHint.getId() == null || !exerciseHintId.equals(exerciseHint.getId()) || exerciseHint.getExercise() == null) {
            return badRequest();
        }
        var hintBeforeSaving = exerciseHintRepository.findByIdElseThrow(exerciseHintId);
        // Reload the exercise from the database as we can't trust data from the client
        Exercise exercise = exerciseRepository.findByIdElseThrow(exerciseHint.getExercise().getId());

        // Hints for exam exercises are not supported at the moment
        if (exercise.isExamExercise()) {
            return forbidden();
        }
        if (!authCheckService.isAtLeastTeachingAssistantForExercise(exercise) || !authCheckService.isAtLeastTeachingAssistantForExercise(hintBeforeSaving.getExercise())) {
            return forbidden();
        }
        ExerciseHint result = exerciseHintRepository.save(exerciseHint);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, exerciseHint.getId().toString())).body(result);
    }

    /**
     * GET /exercise-hints/:hintId/title : Returns the title of the hint with the given id
     *
     * @param hintId the id of the hint
     * @return the title of the hint wrapped in an ResponseEntity or 404 Not Found if no hint with that id exists
     */
    @GetMapping(value = "/exercise-hints/{hintId}/title")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<String> getHintTitle(@PathVariable Long hintId) {
        final var title = exerciseHintRepository.getHintTitle(hintId);
        return title == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(title);
    }

    /**
     * {@code GET  /exercise-hints/:id} : get the "id" exerciseHint.
     *
     * @param exerciseHintId the id of the exerciseHint to retrieve.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the exerciseHint, or with status {@code 404 (Not Found)}.
     */
    @GetMapping("/exercise-hints/{exerciseHintId}")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<ExerciseHint> getExerciseHint(@PathVariable Long exerciseHintId) {
        log.debug("REST request to get ExerciseHint : {}", exerciseHintId);
        var exerciseHint = exerciseHintRepository.findByIdElseThrow(exerciseHintId);

        if (!authCheckService.isAtLeastTeachingAssistantForExercise(exerciseHint.getExercise())) {
            return forbidden();
        }
        return ResponseEntity.ok().body(exerciseHint);
    }

    /**
     * {@code GET  /exercises/:exerciseId/hints} : get the exerciseHints of a provided exercise.
     *
     * @param exerciseId the exercise id of which to retrieve the exercise hints.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the exerciseHint, or with status {@code 404 (Not Found)}.
     */
    @GetMapping("/exercises/{exerciseId}/hints")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Set<ExerciseHint>> getExerciseHintsForExercise(@PathVariable Long exerciseId) {
        log.debug("REST request to get ExerciseHint : {}", exerciseId);
        ProgrammingExercise programmingExercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(exerciseId);
        authCheckService.checkIsAtLeastStudentForExerciseElseThrow(programmingExercise, null);
        Set<ExerciseHint> exerciseHints = exerciseHintRepository.findByExerciseId(exerciseId);
        return ResponseEntity.ok(exerciseHints);
    }

    /**
     * {@code DELETE  /exercise-hints/:id} : delete the "id" exerciseHint.
     *
     * @param exerciseHintId the id of the exerciseHint to delete.
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}.
     */
    @DeleteMapping("/exercise-hints/{exerciseHintId}")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Void> deleteExerciseHint(@PathVariable Long exerciseHintId) {
        log.debug("REST request to delete ExerciseHint : {}", exerciseHintId);
        var exerciseHint = exerciseHintRepository.findByIdElseThrow(exerciseHintId);
        authCheckService.checkIsAtLeastTeachingAssistantForExerciseElseThrow(exerciseHint.getExercise(), null);
        exerciseHintRepository.deleteById(exerciseHintId);
        return ResponseEntity.noContent().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, exerciseHintId.toString())).build();
    }
}
