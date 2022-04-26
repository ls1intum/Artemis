package de.tum.in.www1.artemis.web.rest.hestia;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.hestia.CodeHint;
import de.tum.in.www1.artemis.domain.hestia.ExerciseHint;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.hestia.ExerciseHintRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.hestia.CodeHintService;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.errors.ConflictException;
import tech.jhipster.web.util.HeaderUtil;

/**
 * REST controller for managing {@link de.tum.in.www1.artemis.domain.hestia.ExerciseHint}.
 */
@RestController
@RequestMapping("api/")
public class ExerciseHintResource {

    private final Logger log = LoggerFactory.getLogger(ExerciseHintResource.class);

    private static final String EXERCISE_HINT_ENTITY_NAME = "exerciseHint";

    private static final String CODE_HINT_ENTITY_NAME = "codeHint";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final ExerciseHintRepository exerciseHintRepository;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final AuthorizationCheckService authCheckService;

    private final ExerciseRepository exerciseRepository;

    private final CodeHintService codeHintService;

    public ExerciseHintResource(ExerciseHintRepository exerciseHintRepository, AuthorizationCheckService authCheckService,
            ProgrammingExerciseRepository programmingExerciseRepository, ExerciseRepository exerciseRepository, CodeHintService codeHintService) {
        this.exerciseHintRepository = exerciseHintRepository;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.authCheckService = authCheckService;
        this.exerciseRepository = exerciseRepository;
        this.codeHintService = codeHintService;
    }

    /**
     * {@code POST  exercises/:exerciseId/exercise-hints} : Create a new exerciseHint for an exercise.
     *
     * @param exerciseHint the exerciseHint to create
     * @param exerciseId the exerciseId of the exercise of which to create the exerciseHint
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new exerciseHint,
     * or with status {@code 400 (Bad Request)} if the exerciseHint is a codeHint,
     * or with status {@code 409 (Conflict)} if the exerciseId is invalid,
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PostMapping("exercises/{exerciseId}/exercise-hints")
    @PreAuthorize("hasRole('EDITOR')")
    public ResponseEntity<ExerciseHint> createExerciseHint(@RequestBody ExerciseHint exerciseHint, @PathVariable Long exerciseId) throws URISyntaxException {
        log.debug("REST request to save ExerciseHint : {}", exerciseHint);

        // Reload the exercise from the database as we can't trust data from the client
        Exercise exercise = exerciseRepository.findByIdElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, exercise, null);

        if (exerciseHint instanceof CodeHint) {
            throw new BadRequestAlertException("A code hint cannot be created manually.", CODE_HINT_ENTITY_NAME, "manualCodeHintOperation");
        }
        if (exerciseHint.getExercise() == null) {
            throw new ConflictException("An exercise hint can only be created if the exercise is defined.", EXERCISE_HINT_ENTITY_NAME, "exerciseNotDefined");
        }

        if (!exerciseHint.getExercise().getId().equals(exerciseId)) {
            throw new ConflictException("An exercise hint can only be created if the exerciseIds match.", EXERCISE_HINT_ENTITY_NAME, "exerciseIdMismatch");
        }

        // Hints for exam exercises are not supported at the moment
        if (exercise.isExamExercise()) {
            throw new AccessForbiddenException("Exercise hints for exams are currently not supported");
        }
        ExerciseHint result = exerciseHintRepository.save(exerciseHint);
        return ResponseEntity.created(new URI("/api/exercises/" + exerciseHint.getExercise().getId() + "/exercise-hints/" + result.getId()))
                .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, EXERCISE_HINT_ENTITY_NAME, result.getId().toString())).body(result);
    }

    /**
     * {@code PUT  exercises/:exerciseId/exercise-hints/{id}} : Updates an existing exerciseHint.
     *
     * @param exerciseHint the exerciseHint to update
     * @param exerciseId the exerciseId of the exercise of which to update the exerciseHint
     * @param exerciseHintId the id to the exerciseHint
     *
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated exerciseHint,
     * or with status {@code 400 (Bad Request)} if the exerciseHint is a codeHint,
     * or with status {@code 409 (Conflict} if the exerciseHint or exerciseId are not valid,
     * or with status {@code 500 (Internal Server Error)} if the exerciseHint couldn't be updated.
     */
    @PutMapping("exercises/{exerciseId}/exercise-hints/{exerciseHintId}")
    @PreAuthorize("hasRole('EDITOR')")
    public ResponseEntity<ExerciseHint> updateExerciseHint(@RequestBody ExerciseHint exerciseHint, @PathVariable Long exerciseHintId, @PathVariable Long exerciseId) {
        log.debug("REST request to update ExerciseHint : {}", exerciseHint);

        // Reload the exercise from the database as we can't trust data from the client
        Exercise exercise = exerciseRepository.findByIdElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, exercise, null);
        var hintBeforeSaving = exerciseHintRepository.findByIdWithRelationsElseThrow(exerciseHintId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, hintBeforeSaving.getExercise(), null);

        if (!exerciseHint.getClass().equals(hintBeforeSaving.getClass())) {
            throw new BadRequestAlertException("A code hint cannot be converted to or from a normal hint.", CODE_HINT_ENTITY_NAME, "manualCodeHintOperation");
        }

        if (exerciseHint.getId() == null || !exerciseHintId.equals(exerciseHint.getId()) || exerciseHint.getExercise() == null) {
            throw new ConflictException("An exercise hint can only be changed if it has an ID and if the exercise is not null.", EXERCISE_HINT_ENTITY_NAME, "exerciseNotDefined");
        }

        if (!exerciseHint.getExercise().getId().equals(exerciseId)) {
            throw new ConflictException("An exercise hint can only be updated if the exerciseIds match.", EXERCISE_HINT_ENTITY_NAME, "exerciseIdsMismatch");
        }

        // Hints for exam exercises are not supported at the moment
        if (exercise.isExamExercise()) {
            throw new AccessForbiddenException("Exercise hints for exams are currently not supported");
        }

        if (exerciseHint instanceof CodeHint codeHint && hintBeforeSaving instanceof CodeHint codeHintBeforeSaving) {
            codeHint.setSolutionEntries(codeHintBeforeSaving.getSolutionEntries());
        }
        ExerciseHint result = exerciseHintRepository.save(exerciseHint);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, EXERCISE_HINT_ENTITY_NAME, exerciseHint.getId().toString())).body(result);
    }

    /**
     * GET exercises/:exerciseId/exercise-hints/:hintId/title : Returns the title of the hint with the given id
     *
     * @param exerciseHintId the id of the exerciseHint
     * @param exerciseId the exerciseId of the exercise of which to retrieve the exerciseHints' title
     * @return the title of the hint wrapped in an ResponseEntity or 404 Not Found if no hint with that id exists
     * or with status {@code 409 (Conflict)} if the exerciseId is not valid.
     */
    @GetMapping("exercises/{exerciseId}/exercise-hints/{exerciseHintId}/title")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<String> getHintTitle(@PathVariable Long exerciseId, @PathVariable Long exerciseHintId) {
        final var hint = exerciseHintRepository.findByIdElseThrow(exerciseHintId);

        if (hint.getExercise() == null || !hint.getExercise().getId().equals(exerciseId)) {
            throw new ConflictException("An exercise hint can only be retrieved if the exerciseIds match.", EXERCISE_HINT_ENTITY_NAME, "exerciseIdsMismatch");
        }

        return hint.getTitle() == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(hint.getTitle());
    }

    /**
     * {@code GET  exercises/:exerciseId/exercise-hints/:exerciseHintId} : get the exerciseHint with the given id.
     *
     * @param exerciseHintId the id of the exerciseHint to retrieve.
     * @param exerciseId the exerciseId of the exercise of which to retrieve the exerciseHint
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the exerciseHint,
     * or with status {@code 404 (Not Found)},
     * or with status {@code 409 (Conflict)} if the exerciseId is not valid.
     */
    @GetMapping("exercises/{exerciseId}/exercise-hints/{exerciseHintId}")
    @PreAuthorize("hasRole('EDITOR')")
    public ResponseEntity<ExerciseHint> getExerciseHint(@PathVariable Long exerciseId, @PathVariable Long exerciseHintId) {
        log.debug("REST request to get ExerciseHint : {}", exerciseHintId);
        ProgrammingExercise exercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, exercise, null);
        var exerciseHint = exerciseHintRepository.findByIdWithRelationsElseThrow(exerciseHintId);

        if (!exerciseHint.getExercise().getId().equals(exerciseId)) {
            throw new ConflictException("An exercise hint can only be retrieved if the exerciseIds match.", EXERCISE_HINT_ENTITY_NAME, "exerciseIdsMismatch");
        }

        return ResponseEntity.ok().body(exerciseHint);
    }

    /**
     * {@code GET  exercises/:exerciseId/exercise-hints} : get the exerciseHints of a provided exercise.
     *
     * @param exerciseId the exercise id of which to retrieve the exercise hints.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the exerciseHint,
     * or with status {@code 404 (Not Found)},
     * or with status {@code 409 (Conflict)} if the exerciseId is not valid.
     */
    @GetMapping("exercises/{exerciseId}/exercise-hints")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Set<ExerciseHint>> getExerciseHintsForExercise(@PathVariable Long exerciseId) {
        log.debug("REST request to get ExerciseHint : {}", exerciseId);
        ProgrammingExercise programmingExercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.STUDENT, programmingExercise, null);
        Set<ExerciseHint> exerciseHints = exerciseHintRepository.findByExerciseId(exerciseId);
        return ResponseEntity.ok(exerciseHints);
    }

    /**
     * {@code GET  exercises/:exerciseId/full-exercise-hints} : get the exercise hints of a provided exercise.
     * Fetches all relevant relations as well. This currently only includes the solution entries of a code hint.
     *
     * @param exerciseId the exercise id of which to retrieve the exercise hints.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the exerciseHint,
     * or with status {@code 404 (Not Found)},
     * or with status {@code 409 (Conflict)} if the exerciseId is not valid.
     */
    @GetMapping("exercises/{exerciseId}/full-exercise-hints")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Set<ExerciseHint>> getExerciseHintsWithRelationsForExercise(@PathVariable Long exerciseId) {
        log.debug("REST request to get ExerciseHints for Exercise: {}", exerciseId);
        ProgrammingExercise programmingExercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.STUDENT, programmingExercise, null);
        Set<ExerciseHint> exerciseHints = exerciseHintRepository.findByExerciseIdWithRelations(exerciseId);
        return ResponseEntity.ok(exerciseHints);
    }

    /**
     * {@code DELETE  exercises/:exerciseId/exercise-hints/:exerciseHintId} : delete the exerciseHint with given id.
     *
     * @param exerciseHintId the id of the exerciseHint to delete
     * @param exerciseId the exercise id of which to delete the exercise hint
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)},
     * or with status {@code 409 (Conflict)} if the exerciseId is not valid.
     */
    @DeleteMapping("exercises/{exerciseId}/exercise-hints/{exerciseHintId}")
    @PreAuthorize("hasRole('EDITOR')")
    public ResponseEntity<Void> deleteExerciseHint(@PathVariable Long exerciseId, @PathVariable Long exerciseHintId) {
        log.debug("REST request to delete ExerciseHint : {}", exerciseHintId);
        ProgrammingExercise exercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, exercise, null);

        var exerciseHint = exerciseHintRepository.findByIdElseThrow(exerciseHintId);

        if (!exerciseHint.getExercise().getId().equals(exerciseId)) {
            throw new ConflictException("An exercise hint can only be deleted if the exerciseIds match.", EXERCISE_HINT_ENTITY_NAME, "exerciseIdsMismatch");
        }

        String entityName;

        if (exerciseHint instanceof CodeHint codeHint) {
            codeHintService.deleteCodeHint(codeHint);
            entityName = CODE_HINT_ENTITY_NAME;
        }
        else {
            exerciseHintRepository.deleteById(exerciseHintId);
            entityName = EXERCISE_HINT_ENTITY_NAME;
        }
        return ResponseEntity.noContent().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, entityName, exerciseHintId.toString())).build();
    }
}
