package de.tum.cit.aet.artemis.web.rest.hestia;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInExercise.EnforceAtLeastEditorInExercise;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisSubSettingsType;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.hestia.CodeHint;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.repository.hestia.CodeHintRepository;
import de.tum.cit.aet.artemis.programming.repository.hestia.ProgrammingExerciseSolutionEntryRepository;
import de.tum.cit.aet.artemis.service.hestia.CodeHintService;
import de.tum.cit.aet.artemis.service.iris.settings.IrisSettingsService;
import de.tum.cit.aet.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.cit.aet.artemis.web.rest.errors.ConflictException;

/**
 * REST controller for managing {@link CodeHint}.
 */
@Profile(PROFILE_CORE)
@RestController
@RequestMapping("api/")
public class CodeHintResource {

    private static final Logger log = LoggerFactory.getLogger(CodeHintResource.class);

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final ProgrammingExerciseSolutionEntryRepository solutionEntryRepository;

    private final CodeHintRepository codeHintRepository;

    private final CodeHintService codeHintService;

    private final Optional<IrisSettingsService> irisSettingsService;

    public CodeHintResource(ProgrammingExerciseRepository programmingExerciseRepository, ProgrammingExerciseSolutionEntryRepository solutionEntryRepository,
            CodeHintRepository codeHintRepository, CodeHintService codeHintService, Optional<IrisSettingsService> irisSettingsService) {
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.solutionEntryRepository = solutionEntryRepository;
        this.codeHintRepository = codeHintRepository;
        this.codeHintService = codeHintService;
        this.irisSettingsService = irisSettingsService;
    }

    /**
     * GET programming-exercises/{exerciseId}/code-hints: Retrieve all code hints for a programming exercise.
     *
     * @param exerciseId of the exercise
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body the code hints for the exercise
     */
    @GetMapping("programming-exercises/{exerciseId}/code-hints")
    @EnforceAtLeastEditorInExercise
    public ResponseEntity<Set<CodeHint>> getAllCodeHints(@PathVariable Long exerciseId) {
        var result = codeHintRepository.findByExerciseId(exerciseId);
        return ResponseEntity.ok(result);
    }

    /**
     * {@code POST programming-exercises/:exerciseId/code-hints} : Create a new exerciseHint for an exercise.
     *
     * @param exerciseId         the exerciseId of the exercise of which to create the exerciseHint
     * @param deleteOldCodeHints Whether old code hints should be deleted
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body the new code hints
     */
    @PostMapping("programming-exercises/{exerciseId}/code-hints")
    @EnforceAtLeastEditorInExercise
    public ResponseEntity<List<CodeHint>> generateCodeHintsForExercise(@PathVariable Long exerciseId,
            @RequestParam(value = "deleteOldCodeHints", defaultValue = "true") boolean deleteOldCodeHints) {
        log.debug("REST request to generate CodeHints for ProgrammingExercise: {}", exerciseId);

        ProgrammingExercise exercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);

        // Hints for exam exercises are not supported at the moment
        if (exercise.isExamExercise()) {
            throw new AccessForbiddenException("Code hints for exams are currently not supported");
        }

        var codeHints = codeHintService.generateCodeHintsForExercise(exercise, deleteOldCodeHints);
        return ResponseEntity.ok(codeHints);
    }

    /**
     * {@code POST programming-exercises/:exerciseId/code-hints/:codeHintId/generate-description} : Generate a description for a code hint using Iris.
     *
     * @param exerciseId The id of the exercise of the code hint
     * @param codeHintId The id of the code hint
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body the updated code hint
     */
    // TODO: move into some IrisResource
    @Profile("iris")
    @PostMapping("programming-exercises/{exerciseId}/code-hints/{codeHintId}/generate-description")
    @EnforceAtLeastEditorInExercise
    public ResponseEntity<CodeHint> generateDescriptionForCodeHint(@PathVariable Long exerciseId, @PathVariable Long codeHintId) {
        log.debug("REST request to generate description with Iris for CodeHint: {}", codeHintId);

        ProgrammingExercise exercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
        irisSettingsService.orElseThrow().isEnabledForElseThrow(IrisSubSettingsType.HESTIA, exercise);

        // Hints for exam exercises are not supported at the moment
        if (exercise.isExamExercise()) {
            throw new AccessForbiddenException("Code hints for exams are currently not supported");
        }

        var codeHint = codeHintRepository.findByIdWithSolutionEntriesElseThrow(codeHintId);
        if (!Objects.equals(codeHint.getExercise().getId(), exercise.getId())) {
            throw new ConflictException("The code hint does not belong to the exercise", "CodeHint", "codeHintExerciseConflict");
        }

        if (codeHint.getSolutionEntries().isEmpty()) {
            throw new ConflictException("The code hint does not have any solution entries", "CodeHint", "codeHintNoSolutionEntries");
        }

        codeHint = codeHintService.generateDescriptionWithIris(codeHint);
        return ResponseEntity.ok(codeHint);
    }

    /**
     * {@code DELETE programming-exercises/:exerciseId/code-hints/:codeHintId/solution-entries/:solutionEntryId} :
     * Removes a solution entry from a code hint.
     *
     * @param exerciseId      The id of the exercise of the code hint
     * @param codeHintId      The id of the code hint
     * @param solutionEntryId The id of the solution entry
     * @return 204 No Content
     */
    @DeleteMapping("programming-exercises/{exerciseId}/code-hints/{codeHintId}/solution-entries/{solutionEntryId}")
    @EnforceAtLeastEditorInExercise
    public ResponseEntity<Void> removeSolutionEntryFromCodeHint(@PathVariable Long exerciseId, @PathVariable Long codeHintId, @PathVariable Long solutionEntryId) {
        log.debug("REST request to remove SolutionEntry {} from CodeHint {} in ProgrammingExercise {}", solutionEntryId, codeHintId, exerciseId);

        var codeHint = codeHintRepository.findByIdWithSolutionEntriesElseThrow(codeHintId);
        if (!Objects.equals(codeHint.getExercise().getId(), exerciseId)) {
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
