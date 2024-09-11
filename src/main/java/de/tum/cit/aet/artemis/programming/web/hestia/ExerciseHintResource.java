package de.tum.cit.aet.artemis.programming.web.hestia;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Set;

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

import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInExercise.EnforceAtLeastEditorInExercise;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInExercise.EnforceAtLeastStudentInExercise;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInExercise.EnforceAtLeastTutorInExercise;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.hestia.CodeHint;
import de.tum.cit.aet.artemis.programming.domain.hestia.ExerciseHint;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.repository.hestia.ExerciseHintRepository;
import de.tum.cit.aet.artemis.programming.service.hestia.CodeHintService;
import de.tum.cit.aet.artemis.programming.service.hestia.ExerciseHintService;
import tech.jhipster.web.util.HeaderUtil;

/**
 * REST controller for managing {@link ExerciseHint}.
 */
@Profile(PROFILE_CORE)
@RestController
@RequestMapping("api/")
public class ExerciseHintResource {

    private static final String EXERCISE_HINT_ENTITY_NAME = "exerciseHint";

    private static final String CODE_HINT_ENTITY_NAME = "codeHint";

    private static final Logger log = LoggerFactory.getLogger(ExerciseHintResource.class);

    private final ExerciseHintService exerciseHintService;

    private final ExerciseHintRepository exerciseHintRepository;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final ExerciseRepository exerciseRepository;

    private final CodeHintService codeHintService;

    private final UserRepository userRepository;

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    public ExerciseHintResource(ExerciseHintService exerciseHintService, ExerciseHintRepository exerciseHintRepository, ProgrammingExerciseRepository programmingExerciseRepository,
            ExerciseRepository exerciseRepository, CodeHintService codeHintService, UserRepository userRepository) {
        this.exerciseHintService = exerciseHintService;
        this.exerciseHintRepository = exerciseHintRepository;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.exerciseRepository = exerciseRepository;
        this.codeHintService = codeHintService;
        this.userRepository = userRepository;
    }

    /**
     * {@code POST programming-exercises/:exerciseId/exercise-hints} : Create a new exerciseHint for an exercise.
     *
     * @param exerciseHint the exerciseHint to create
     * @param exerciseId   the exerciseId of the exercise of which to create the exerciseHint
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new exerciseHint,
     *         or with status {@code 409 (Conflict)} if the exerciseId is invalid,
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PostMapping("programming-exercises/{exerciseId}/exercise-hints")
    @EnforceAtLeastEditorInExercise
    public ResponseEntity<ExerciseHint> createExerciseHint(@RequestBody ExerciseHint exerciseHint, @PathVariable Long exerciseId) throws URISyntaxException {
        log.debug("REST request to save ExerciseHint : {}", exerciseHint);

        // Reload the exercise from the database as we can't trust data from the client
        Exercise exercise = exerciseRepository.findByIdElseThrow(exerciseId);

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
            throw new BadRequestAlertException("Exercise hints for exams are currently not supported", EXERCISE_HINT_ENTITY_NAME, "exerciseHintNotSupported");
        }
        ExerciseHint result = exerciseHintRepository.save(exerciseHint);
        return ResponseEntity.created(new URI("/api/programming-exercises/" + exerciseHint.getExercise().getId() + "/exercise-hints/" + result.getId()))
                .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, EXERCISE_HINT_ENTITY_NAME, result.getId().toString())).body(result);
    }

    /**
     * {@code PUT programming-exercises/:exerciseId/exercise-hints/:exerciseHintId} : Updates an existing exerciseHint.
     *
     * @param exerciseHint   the exerciseHint to update
     * @param exerciseId     the exerciseId of the exercise of which to update the exerciseHint
     * @param exerciseHintId the id to the exerciseHint
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated exerciseHint,
     *         or with status {@code 409 (Conflict} if the exerciseHint or exerciseId are not valid,
     *         or with status {@code 500 (Internal Server Error)} if the exerciseHint couldn't be updated.
     */
    @PutMapping("programming-exercises/{exerciseId}/exercise-hints/{exerciseHintId}")
    @EnforceAtLeastEditorInExercise
    public ResponseEntity<ExerciseHint> updateExerciseHint(@RequestBody ExerciseHint exerciseHint, @PathVariable Long exerciseHintId, @PathVariable Long exerciseId) {
        log.debug("REST request to update ExerciseHint : {}", exerciseHint);

        // Reload the exercise from the database as we can't trust data from the client
        Exercise exercise = exerciseRepository.findByIdElseThrow(exerciseId);
        var hintBeforeSaving = exerciseHintRepository.findByIdWithRelationsElseThrow(exerciseHintId);

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
            throw new BadRequestAlertException("Exercise hints for exams are currently not supported", EXERCISE_HINT_ENTITY_NAME, "exerciseHintNotSupported");
        }

        if (exerciseHint instanceof CodeHint codeHint && codeHint.getSolutionEntries() != null) {
            codeHintService.updateSolutionEntriesForCodeHint(codeHint);
        }
        exerciseHint.setExerciseHintActivations(hintBeforeSaving.getExerciseHintActivations());
        ExerciseHint result = exerciseHintRepository.save(exerciseHint);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, EXERCISE_HINT_ENTITY_NAME, exerciseHint.getId().toString())).body(result);
    }

    /**
     * {@code GET programming-exercises/:exerciseId/exercise-hints/:exerciseHintId/title} : Returns the title of the hint with the given id
     *
     * @param exerciseHintId the id of the exerciseHint
     * @param exerciseId     the exerciseId of the exercise of which to retrieve the exerciseHints' title
     * @return the title of the hint wrapped in an ResponseEntity or 404 Not Found if no hint with that id exists
     *         or with status {@code 409 (Conflict)} if the exerciseId is not valid.
     */
    @GetMapping("programming-exercises/{exerciseId}/exercise-hints/{exerciseHintId}/title")
    @EnforceAtLeastStudent
    public ResponseEntity<String> getHintTitle(@PathVariable Long exerciseId, @PathVariable Long exerciseHintId) {
        var title = exerciseHintService.getExerciseHintTitle(exerciseId, exerciseHintId);
        return title == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(title);
    }

    /**
     * {@code GET programming-exercises/:exerciseId/exercise-hints/:exerciseHintId} : get the exerciseHint with the given id.
     *
     * @param exerciseHintId the id of the exerciseHint to retrieve.
     * @param exerciseId     the exerciseId of the exercise of which to retrieve the exerciseHint
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the exerciseHint,
     *         or with status {@code 404 (Not Found)},
     *         or with status {@code 409 (Conflict)} if the exerciseId is not valid.
     */
    @GetMapping("programming-exercises/{exerciseId}/exercise-hints/{exerciseHintId}")
    @EnforceAtLeastTutorInExercise
    public ResponseEntity<ExerciseHint> getExerciseHint(@PathVariable Long exerciseId, @PathVariable Long exerciseHintId) {
        log.debug("REST request to get ExerciseHint : {}", exerciseHintId);
        ProgrammingExercise exercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
        if (exercise.isExamExercise()) {
            // not allowed for exam exercises
            throw new BadRequestAlertException("Exercise hints for exams are currently not supported", EXERCISE_HINT_ENTITY_NAME, "exerciseHintNotSupported");
        }

        var exerciseHint = exerciseHintRepository.findByIdWithRelationsElseThrow(exerciseHintId);

        if (!exerciseHint.getExercise().getId().equals(exerciseId)) {
            throw new ConflictException("An exercise hint can only be retrieved if the exerciseIds match.", EXERCISE_HINT_ENTITY_NAME, "exerciseIdsMismatch");
        }

        return ResponseEntity.ok().body(exerciseHint);
    }

    /**
     * {@code GET programming-exercises/:exerciseId/exercise-hints} : get the exerciseHints of a provided exercise.
     *
     * @param exerciseId the exercise id of which to retrieve the exercise hints.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the exerciseHint,
     *         or with status {@code 404 (Not Found)},
     *         or with status {@code 409 (Conflict)} if the exerciseId is not valid.
     */
    @GetMapping("programming-exercises/{exerciseId}/exercise-hints")
    @EnforceAtLeastTutorInExercise
    public ResponseEntity<Set<ExerciseHint>> getExerciseHintsForExercise(@PathVariable Long exerciseId) {
        log.debug("REST request to get ExerciseHints : {}", exerciseId);
        var exerciseHints = exerciseHintRepository.findByExerciseIdWithRelations(exerciseId);
        return ResponseEntity.ok(exerciseHints);
    }

    /**
     * {@code GET programming-exercises/:exerciseId/exercise-hints/activated} : get the exercise hints of a provided exercise that the user has activated
     *
     * @param exerciseId the exercise id of which to retrieve the exercise hints.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the exercise hints,
     *         or with status {@code 404 (Not Found)}
     */
    @GetMapping("programming-exercises/{exerciseId}/exercise-hints/activated")
    @EnforceAtLeastStudentInExercise
    public ResponseEntity<Set<ExerciseHint>> getActivatedExerciseHintsForExercise(@PathVariable Long exerciseId) {
        log.debug("REST request to get activated ExerciseHints : {}", exerciseId);
        ProgrammingExercise exercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
        if (exercise.isExamExercise()) {
            // not allowed for exam exercises
            throw new BadRequestAlertException("Exercise hints for exams are currently not supported", EXERCISE_HINT_ENTITY_NAME, "exerciseHintNotSupported");
        }

        var user = userRepository.getUserWithGroupsAndAuthorities();
        var exerciseHints = exerciseHintService.getActivatedExerciseHints(exercise, user);
        return ResponseEntity.ok(exerciseHints);
    }

    /**
     * {@code GET programming-exercises/:exerciseId/available-exercise-hints} : get the available exercise hints.
     *
     * @param exerciseId the exerciseId of the exercise of which to retrieve the exercise hint
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the exercise hints
     */
    @GetMapping("programming-exercises/{exerciseId}/exercise-hints/available")
    @EnforceAtLeastStudentInExercise
    public ResponseEntity<Set<ExerciseHint>> getAvailableExerciseHintsForExercise(@PathVariable Long exerciseId) {
        log.debug("REST request to get a CodeHint for programming exercise : {}", exerciseId);
        ProgrammingExercise exercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
        if (exercise.isExamExercise()) {
            // not allowed for exam exercises
            throw new BadRequestAlertException("Exercise hints for exams are currently not supported", EXERCISE_HINT_ENTITY_NAME, "exerciseHintNotSupported");
        }

        var user = userRepository.getUserWithGroupsAndAuthorities();
        var availableExerciseHints = exerciseHintService.getAvailableExerciseHints(exercise, user);
        availableExerciseHints.forEach(ExerciseHint::removeContent);

        return ResponseEntity.ok().body(availableExerciseHints);
    }

    /**
     * {@code POST programming-exercises/:exerciseId/exercise-hints/:exerciseHintId/activate}
     * Activates a single exercise hint of an exercise for the logged-in user
     *
     * @param exerciseId     The id of the exercise of which to activate the exercise hint
     * @param exerciseHintId The id of the exercise hint to activate
     * @return The {@link ResponseEntity} with status {@code 200 (OK)} and with body the activated exercise hint with content
     *         or with status {@code 400 (BAD_REQUEST)} if the hint could not be activated
     */
    @PostMapping("programming-exercises/{exerciseId}/exercise-hints/{exerciseHintId}/activate")
    @EnforceAtLeastStudentInExercise
    public ResponseEntity<ExerciseHint> activateExerciseHint(@PathVariable Long exerciseId, @PathVariable Long exerciseHintId) {
        log.debug("REST request to activate ExerciseHint : {}", exerciseHintId);
        var exercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
        var user = userRepository.getUserWithGroupsAndAuthorities();

        var exerciseHint = exerciseHintRepository.findByIdWithRelationsElseThrow(exerciseHintId);

        if (!exerciseHint.getExercise().getId().equals(exercise.getId())) {
            throw new ConflictException("An exercise hint can only be deleted if the exerciseIds match.", EXERCISE_HINT_ENTITY_NAME, "exerciseIdsMismatch");
        }

        if (exerciseHintService.activateHint(exerciseHint, user)) {
            return ResponseEntity.ok(exerciseHint);
        }
        else {
            throw new BadRequestAlertException("Unable to activate exercise hint", EXERCISE_HINT_ENTITY_NAME, "exerciseHintIdActivationFailed");
        }
    }

    /**
     * {@code POST programming-exercises/:exerciseId/exercise-hints/:exerciseHintId/rating/:ratingValue}: Rates an exercise hint
     *
     * @param exerciseId     The id of the exercise of which to activate the exercise hint
     * @param exerciseHintId The id of the exercise hint to activate
     * @param ratingValue    The value of the rating
     * @return The {@link ResponseEntity} with status {@code 200 (OK)}
     */
    @PostMapping("programming-exercises/{exerciseId}/exercise-hints/{exerciseHintId}/rating/{ratingValue}")
    @EnforceAtLeastStudentInExercise
    public ResponseEntity<Void> rateExerciseHint(@PathVariable Long exerciseId, @PathVariable Long exerciseHintId, @PathVariable Integer ratingValue) {
        log.debug("REST request to rate ExerciseHint : {}", exerciseHintId);
        var user = userRepository.getUserWithGroupsAndAuthorities();

        var exerciseHint = exerciseHintRepository.findByIdWithRelationsElseThrow(exerciseHintId);
        if (!exerciseHint.getExercise().getId().equals(exerciseId)) {
            throw new ConflictException("An exercise hint can only be deleted if the exerciseIds match.", EXERCISE_HINT_ENTITY_NAME, "exerciseIdsMismatch");
        }

        exerciseHintService.rateExerciseHint(exerciseHint, user, ratingValue);

        return ResponseEntity.ok().build();
    }

    /**
     * {@code DELETE programming-exercises/:exerciseId/exercise-hints/:exerciseHintId} : delete the exerciseHint with given id.
     *
     * @param exerciseHintId the id of the exerciseHint to delete
     * @param exerciseId     the exercise id of which to delete the exercise hint
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)},
     *         or with status {@code 409 (Conflict)} if the exerciseId is not valid.
     */
    @DeleteMapping("programming-exercises/{exerciseId}/exercise-hints/{exerciseHintId}")
    @EnforceAtLeastEditorInExercise
    public ResponseEntity<Void> deleteExerciseHint(@PathVariable Long exerciseId, @PathVariable Long exerciseHintId) {
        log.debug("REST request to delete ExerciseHint : {}", exerciseHintId);
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
