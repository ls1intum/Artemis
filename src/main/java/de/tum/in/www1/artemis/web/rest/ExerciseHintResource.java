package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.*;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.ExerciseHint;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.ExerciseHintService;
import de.tum.in.www1.artemis.service.ProgrammingExerciseService;
import de.tum.in.www1.artemis.service.UserService;
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

    private final ExerciseHintService exerciseHintService;

    private final ProgrammingExerciseService programmingExerciseService;

    private final AuthorizationCheckService authCheckService;

    private final UserService userService;

    public ExerciseHintResource(ExerciseHintService exerciseHintService, AuthorizationCheckService authCheckService, ProgrammingExerciseService programmingExerciseService,
            UserService userService) {
        this.exerciseHintService = exerciseHintService;
        this.programmingExerciseService = programmingExerciseService;
        this.authCheckService = authCheckService;
        this.userService = userService;
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
        if (!authCheckService.isAtLeastTeachingAssistantForExercise(exerciseHint.getExercise())) {
            return forbidden();
        }
        ExerciseHint result = exerciseHintService.save(exerciseHint);
        return ResponseEntity.created(new URI("/api/exercise-hints/" + result.getId()))
                .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, result.getId().toString())).body(result);
    }

    /**
     * {@code PUT  /exercise-hints/{id}} : Updates an existing exerciseHint.
     *
     * @param exerciseHint the exerciseHint to update.
     * @param id  the id to the exerciseHint
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated exerciseHint,
     * or with status {@code 400 (Bad Request)} if the exerciseHint is not valid,
     * or with status {@code 500 (Internal Server Error)} if the exerciseHint couldn't be updated.
     */
    @PutMapping("/exercise-hints/{id}")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<ExerciseHint> updateExerciseHint(@RequestBody ExerciseHint exerciseHint, @PathVariable Long id) {
        log.debug("REST request to update ExerciseHint : {}", exerciseHint);
        if (exerciseHint.getId() == null || !id.equals(exerciseHint.getId()) || exerciseHint.getExercise() == null) {
            return badRequest();
        }
        Optional<ExerciseHint> hintBeforeSaving = exerciseHintService.findOne(id);
        if (!hintBeforeSaving.isPresent()) {
            return notFound();
        }
        if (!authCheckService.isAtLeastTeachingAssistantForExercise(exerciseHint.getExercise())
                || !authCheckService.isAtLeastTeachingAssistantForExercise(hintBeforeSaving.get().getExercise())) {
            return forbidden();
        }
        ExerciseHint result = exerciseHintService.save(exerciseHint);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, exerciseHint.getId().toString())).body(result);
    }

    /**
     * {@code GET  /exercise-hints/:id} : get the "id" exerciseHint.
     *
     * @param id the id of the exerciseHint to retrieve.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the exerciseHint, or with status {@code 404 (Not Found)}.
     */
    @GetMapping("/exercise-hints/{id}")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<ExerciseHint> getExerciseHint(@PathVariable Long id) {
        log.debug("REST request to get ExerciseHint : {}", id);
        Optional<ExerciseHint> exerciseHint = exerciseHintService.findOne(id);
        if (!exerciseHint.isPresent()) {
            return notFound();
        }
        if (!authCheckService.isAtLeastTeachingAssistantForExercise(exerciseHint.get().getExercise())) {
            return forbidden();
        }
        return ResponseEntity.ok().body(exerciseHint.get());
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
        ProgrammingExercise programmingExercise = programmingExerciseService.findById(exerciseId);
        User user = userService.getUserWithGroupsAndAuthorities();
        Course course = programmingExercise.getCourse();
        if (!authCheckService.isStudentInCourse(course, user) && !authCheckService.isAtLeastTeachingAssistantInCourse(course, user))
            return forbidden();

        Set<ExerciseHint> exerciseHints = exerciseHintService.findByExerciseId(exerciseId);
        return ResponseEntity.ok(exerciseHints);
    }

    /**
     * {@code DELETE  /exercise-hints/:id} : delete the "id" exerciseHint.
     *
     * @param id the id of the exerciseHint to delete.
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}.
     */
    @DeleteMapping("/exercise-hints/{id}")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Void> deleteExerciseHint(@PathVariable Long id) {
        log.debug("REST request to delete ExerciseHint : {}", id);
        Optional<ExerciseHint> exerciseHint = exerciseHintService.findOne(id);
        if (!exerciseHint.isPresent()) {
            return notFound();
        }
        if (!authCheckService.isAtLeastTeachingAssistantForExercise(exerciseHint.get().getExercise())) {
            return forbidden();
        }
        exerciseHintService.delete(id);
        return ResponseEntity.noContent().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, id.toString())).build();
    }
}
