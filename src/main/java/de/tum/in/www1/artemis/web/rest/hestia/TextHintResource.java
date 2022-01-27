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
import de.tum.in.www1.artemis.domain.hestia.TextHint;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.repository.hestia.TextHintRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import tech.jhipster.web.util.HeaderUtil;

/**
 * REST controller for managing {@link de.tum.in.www1.artemis.domain.hestia.TextHint}.
 */
@RestController
@RequestMapping("/api")
public class TextHintResource {

    private final Logger log = LoggerFactory.getLogger(TextHintResource.class);

    private static final String ENTITY_NAME = "textHint";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final TextHintRepository textHintRepository;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final AuthorizationCheckService authCheckService;

    private final ExerciseRepository exerciseRepository;

    public TextHintResource(TextHintRepository textHintRepository, AuthorizationCheckService authCheckService, ProgrammingExerciseRepository programmingExerciseRepository,
            ExerciseRepository exerciseRepository) {
        this.textHintRepository = textHintRepository;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.authCheckService = authCheckService;
        this.exerciseRepository = exerciseRepository;
    }

    /**
     * {@code POST  /text-hints} : Create a new textHint.
     *
     * @param textHint the textHint to create.
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new textHint, or with status {@code 400 (Bad Request)} if the exerciseHint has already an ID.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PostMapping("/text-hints")
    @PreAuthorize("hasRole('EDITOR')")
    public ResponseEntity<TextHint> createTextHint(@RequestBody TextHint textHint) throws URISyntaxException {
        log.debug("REST request to save TextHint : {}", textHint);
        if (textHint.getExercise() == null) {
            throw new BadRequestAlertException("A text hint can only be created if the exercise is defined", ENTITY_NAME, "idnull");
        }
        // Reload the exercise from the database as we can't trust data from the client
        Exercise exercise = exerciseRepository.findByIdElseThrow(textHint.getExercise().getId());

        // Hints for exam exercises are not supported at the moment
        if (exercise.isExamExercise()) {
            throw new AccessForbiddenException("Text hints for exams are currently not supported");
        }
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, exercise, null);
        TextHint result = textHintRepository.save(textHint);
        return ResponseEntity.created(new URI("/api/text-hints/" + result.getId()))
                .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, result.getId().toString())).body(result);
    }

    /**
     * {@code PUT  /text-hints/{id}} : Updates an existing textHint.
     *
     * @param textHint the textHint to update.
     * @param textHintId  the id to the textHint
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated textHint,
     * or with status {@code 400 (Bad Request)} if the textHint is not valid,
     * or with status {@code 500 (Internal Server Error)} if the textHint couldn't be updated.
     */
    @PutMapping("/text-hints/{textHintId}")
    @PreAuthorize("hasRole('EDITOR')")
    public ResponseEntity<TextHint> updateTextHint(@RequestBody TextHint textHint, @PathVariable Long textHintId) {
        log.debug("REST request to update TextHint : {}", textHint);
        if (textHint.getId() == null || !textHintId.equals(textHint.getId()) || textHint.getExercise() == null) {
            throw new BadRequestAlertException("A text hint can only be changed if it has an ID and if the exercise is not null", ENTITY_NAME, "idnull");
        }
        var hintBeforeSaving = textHintRepository.findByIdElseThrow(textHintId);
        // Reload the exercise from the database as we can't trust data from the client
        Exercise exercise = exerciseRepository.findByIdElseThrow(textHint.getExercise().getId());

        // Hints for exam exercises are not supported at the moment
        if (exercise.isExamExercise()) {
            throw new AccessForbiddenException("Text hints for exams are currently not supported");
        }
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, hintBeforeSaving.getExercise(), null);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, textHint.getExercise(), null);
        TextHint result = textHintRepository.save(textHint);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, textHint.getId().toString())).body(result);
    }

    /**
     * {@code GET  /exercises/:exerciseId/text-hints} : get the textHints of a provided exercise.
     *
     * @param exerciseId the exercise id of which to retrieve the text hints.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the textHints, or with status {@code 404 (Not Found)}.
     */
    @GetMapping("/exercises/{exerciseId}/text-hints")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Set<TextHint>> getTextHintsForExercise(@PathVariable Long exerciseId) {
        log.debug("REST request to get TextHint : {}", exerciseId);
        ProgrammingExercise programmingExercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.STUDENT, programmingExercise, null);
        Set<TextHint> textHints = textHintRepository.findByExerciseId(exerciseId);
        return ResponseEntity.ok(textHints);
    }

    /**
     * {@code DELETE  /text-hints/:id} : delete the "id" textHint.
     *
     * @param textHintId the id of the textHint to delete.
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}.
     */
    @DeleteMapping("/text-hints/{textHintId}")
    @PreAuthorize("hasRole('EDITOR')")
    public ResponseEntity<Void> deleteTextHint(@PathVariable Long textHintId) {
        log.debug("REST request to delete TextHint : {}", textHintId);
        var textHint = textHintRepository.findByIdElseThrow(textHintId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, textHint.getExercise(), null);
        textHintRepository.deleteById(textHintId);
        return ResponseEntity.noContent().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, textHintId.toString())).build();
    }
}
