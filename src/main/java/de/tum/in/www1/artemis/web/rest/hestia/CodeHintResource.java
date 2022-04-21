package de.tum.in.www1.artemis.web.rest.hestia;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.hestia.CodeHint;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.hestia.CodeHintService;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;

/**
 * REST controller for managing {@link CodeHint}.
 */
@RestController
@RequestMapping("api/")
public class CodeHintResource {

    private final Logger log = LoggerFactory.getLogger(CodeHintResource.class);

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final AuthorizationCheckService authCheckService;

    private final CodeHintService codeHintService;

    public CodeHintResource(AuthorizationCheckService authCheckService, ProgrammingExerciseRepository programmingExerciseRepository, CodeHintService codeHintService) {
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.authCheckService = authCheckService;
        this.codeHintService = codeHintService;
    }

    /**
     * {@code POST  exercises/:exerciseId/exercise-hints} : Create a new exerciseHint for an exercise.
     *
     * @param exerciseId the exerciseId of the exercise of which to create the exerciseHint
     * @param deleteOldCodeHints Whether old code hints should be deleted
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body the new code hints,
     */
    @PostMapping("programming-exercises/{exerciseId}/code-hints")
    @PreAuthorize("hasRole('EDITOR')")
    public ResponseEntity<List<CodeHint>> generateCodeHintsForExercise(@PathVariable Long exerciseId,
            @RequestParam(value = "deleteOldCodeHints", defaultValue = "true") boolean deleteOldCodeHints) {
        log.debug("REST request to generate CodeHints for ProgrammingExercise: {}", exerciseId);

        ProgrammingExercise exercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, exercise, null);

        // Hints for exam exercises are not supported at the moment
        if (exercise.isExamExercise()) {
            throw new AccessForbiddenException("Code hints for exams are currently not supported");
        }

        var codeHints = codeHintService.generateCodeHintsForExercise(exercise, deleteOldCodeHints);
        return ResponseEntity.ok(codeHints);
    }
}
