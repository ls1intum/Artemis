package de.tum.in.www1.artemis.web.rest.hestia;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.hestia.ExerciseHint;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.repository.hestia.ExerciseHintRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;

/**
 * REST controller for managing {@link de.tum.in.www1.artemis.domain.hestia.ExerciseHint}.
 */
@RestController
@RequestMapping("/api")
public class ExerciseHintResource {

    private final Logger log = LoggerFactory.getLogger(ExerciseHintResource.class);

    private final ExerciseHintRepository exerciseHintRepository;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final AuthorizationCheckService authCheckService;

    public ExerciseHintResource(ExerciseHintRepository exerciseHintRepository, AuthorizationCheckService authCheckService,
            ProgrammingExerciseRepository programmingExerciseRepository) {
        this.exerciseHintRepository = exerciseHintRepository;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.authCheckService = authCheckService;
    }

    /**
     * GET /exercise-hints/:hintId/title : Returns the title of the hint with the given id
     *
     * @param hintId the id of the hint
     * @return the title of the hint wrapped in an ResponseEntity or 404 Not Found if no hint with that id exists
     */
    @GetMapping(value = "/exercise-hints/{hintId}/title")
    @PreAuthorize("hasRole('USER')")
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
    @PreAuthorize("hasRole('EDITOR')")
    public ResponseEntity<ExerciseHint> getExerciseHint(@PathVariable Long exerciseHintId) {
        log.debug("REST request to get ExerciseHint : {}", exerciseHintId);
        var exerciseHint = exerciseHintRepository.findByIdElseThrow(exerciseHintId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, exerciseHint.getExercise(), null);
        return ResponseEntity.ok().body(exerciseHint);
    }

    /**
     * {@code GET  /exercises/:exerciseId/hints} : get the exerciseHints of a provided exercise.
     *
     * @param exerciseId the exercise id of which to retrieve the exercise hints.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the exerciseHint, or with status {@code 404 (Not Found)}.
     */
    @GetMapping("/exercises/{exerciseId}/exercise-hints")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Set<ExerciseHint>> getExerciseHintsForExercise(@PathVariable Long exerciseId) {
        log.debug("REST request to get ExerciseHint : {}", exerciseId);
        ProgrammingExercise programmingExercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.STUDENT, programmingExercise, null);
        Set<ExerciseHint> exerciseHints = exerciseHintRepository.findByExerciseId(exerciseId);
        return ResponseEntity.ok(exerciseHints);
    }
}
