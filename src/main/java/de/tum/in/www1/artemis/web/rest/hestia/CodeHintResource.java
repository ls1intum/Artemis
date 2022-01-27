package de.tum.in.www1.artemis.web.rest.hestia;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.hestia.CodeHint;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.repository.hestia.CodeHintRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;

/**
 * REST controller for managing {@link de.tum.in.www1.artemis.domain.hestia.CodeHint}.
 */
@RestController
@RequestMapping("/api")
public class CodeHintResource {

    private final Logger log = LoggerFactory.getLogger(CodeHintResource.class);

    private final CodeHintRepository codeHintRepository;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final AuthorizationCheckService authCheckService;

    public CodeHintResource(CodeHintRepository codeHintRepository, AuthorizationCheckService authCheckService, ProgrammingExerciseRepository programmingExerciseRepository) {
        this.codeHintRepository = codeHintRepository;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.authCheckService = authCheckService;
    }

    /**
     * GET /code-hints/:hintId/title : Returns the title of the hint with the given id
     *
     * @param hintId the id of the hint
     * @return the title of the hint wrapped in an ResponseEntity or 404 Not Found if no hint with that id exists
     */
    @GetMapping(value = "/code-hints/{hintId}/title")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<String> getHintTitle(@PathVariable Long hintId) {
        final var title = codeHintRepository.getHintTitle(hintId);
        return title == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(title);
    }

    /**
     * {@code GET  /code-hints/:id} : get the "id" codeHint.
     *
     * @param codeHintId the id of the codeHint to retrieve.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the codeHint, or with status {@code 404 (Not Found)}.
     */
    @GetMapping("/code-hints/{codeHintId}")
    @PreAuthorize("hasRole('EDITOR')")
    public ResponseEntity<CodeHint> getCodeHint(@PathVariable Long codeHintId) {
        log.debug("REST request to get CodeHint : {}", codeHintId);
        var codeHint = codeHintRepository.findByIdElseThrow(codeHintId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, codeHint.getExercise(), null);
        return ResponseEntity.ok().body(codeHint);
    }

    /**
     * {@code GET  /exercises/:exerciseId/code-hints} : get the codeHints of a provided exercise.
     *
     * @param exerciseId the exercise id of which to retrieve the code hints.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the codeHint, or with status {@code 404 (Not Found)}.
     */
    @GetMapping("/exercises/{exerciseId}/code-hints")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Set<CodeHint>> getCodeHintsForExercise(@PathVariable Long exerciseId) {
        log.debug("REST request to get CodeHint : {}", exerciseId);
        ProgrammingExercise programmingExercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.STUDENT, programmingExercise, null);
        Set<CodeHint> codeHints = codeHintRepository.findByExerciseId(exerciseId);
        return ResponseEntity.ok(codeHints);
    }
}
