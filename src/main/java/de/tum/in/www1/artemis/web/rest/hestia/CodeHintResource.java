package de.tum.in.www1.artemis.web.rest.hestia;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.hestia.CodeHint;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.hestia.CodeHintRepository;
import de.tum.in.www1.artemis.repository.hestia.ProgrammingExerciseSolutionEntryRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.hestia.CodeHintService;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.in.www1.artemis.web.rest.errors.ConflictException;

/**
 * REST controller for managing {@link CodeHint}.
 */
@RestController
@RequestMapping("api/")
public class CodeHintResource {

    private final Logger log = LoggerFactory.getLogger(CodeHintResource.class);

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final AuthorizationCheckService authCheckService;

    private final ProgrammingExerciseSolutionEntryRepository solutionEntryRepository;

    private final CodeHintRepository codeHintRepository;

    private final CodeHintService codeHintService;

    public CodeHintResource(AuthorizationCheckService authCheckService, ProgrammingExerciseRepository programmingExerciseRepository,
            ProgrammingExerciseSolutionEntryRepository solutionEntryRepository, CodeHintRepository codeHintRepository, CodeHintService codeHintService) {
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.authCheckService = authCheckService;
        this.solutionEntryRepository = solutionEntryRepository;
        this.codeHintRepository = codeHintRepository;
        this.codeHintService = codeHintService;
    }

    /**
     * GET programming-exercises/{exerciseId}/code-hints: Retrieve all code hints for a programming exercise.
     * @param exerciseId of the exercise
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body the code hints for the exercise
     */
    @GetMapping("programming-exercises/{exerciseId}/code-hints")
    @PreAuthorize("hasRole('EDITOR')")
    public ResponseEntity<Set<CodeHint>> getAllCodeHints(@PathVariable Long exerciseId) {
        ProgrammingExercise exercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, exercise, null);

        var result = codeHintRepository.findByExerciseId(exercise.getId());
        return ResponseEntity.ok(result);
    }

    /**
     * {@code POST programming-exercises/:exerciseId/code-hints} : Create a new exerciseHint for an exercise.
     *
     * @param exerciseId the exerciseId of the exercise of which to create the exerciseHint
     * @param deleteOldCodeHints Whether old code hints should be deleted
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body the new code hints
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

    /**
     * {@code DELETE programming-exercises/:exerciseId/code-hints/:codeHintId/solution-entries/:solutionEntryId} :
     * Removes a solution entry from a code hint.
     *
     * @param exerciseId The id of the exercise of the code hint
     * @param codeHintId The id of the code hint
     * @param solutionEntryId The id of the solution entry
     * @return 204 No Content
     */
    @DeleteMapping("programming-exercises/{exerciseId}/code-hints/{codeHintId}/solution-entries/{solutionEntryId}")
    @PreAuthorize("hasRole('EDITOR')")
    public ResponseEntity<Void> removeSolutionEntryFromCodeHint(@PathVariable Long exerciseId, @PathVariable Long codeHintId, @PathVariable Long solutionEntryId) {
        log.debug("REST request to remove SolutionEntry {} from CodeHint {} in ProgrammingExercise {}", solutionEntryId, codeHintId, exerciseId);

        ProgrammingExercise exercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, exercise, null);

        var codeHint = codeHintRepository.findByIdWithSolutionEntriesElseThrow(codeHintId);
        if (!Objects.equals(codeHint.getExercise().getId(), exercise.getId())) {
            throw new ConflictException("The code hint does not belong to the exercise", "CodeHint", "codeHintExerciseConflict");
        }

        var solutionEntry = codeHint.getSolutionEntries().stream().filter(solutionEntry1 -> solutionEntry1.getId().equals(solutionEntryId)).findFirst().orElse(null);
        if (solutionEntry == null) {
            throw new ConflictException("The solution entry does not belong to the code hint", "SolutionEntry", "solutionEntryCodeHintConflict");
        }

        solutionEntry.setCodeHint(null);
        solutionEntryRepository.save(solutionEntry);
        codeHint.getSolutionEntries().remove(solutionEntry);

        return ResponseEntity.noContent().build();
    }
}
