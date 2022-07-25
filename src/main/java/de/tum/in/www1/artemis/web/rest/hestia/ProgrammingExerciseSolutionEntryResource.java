package de.tum.in.www1.artemis.web.rest.hestia;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.ProgrammingExerciseTestCase;
import de.tum.in.www1.artemis.domain.hestia.CodeHint;
import de.tum.in.www1.artemis.domain.hestia.ProgrammingExerciseSolutionEntry;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseTestCaseRepository;
import de.tum.in.www1.artemis.repository.hestia.CodeHintRepository;
import de.tum.in.www1.artemis.repository.hestia.ProgrammingExerciseSolutionEntryRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.hestia.behavioral.BehavioralSolutionEntryGenerationException;
import de.tum.in.www1.artemis.service.hestia.behavioral.BehavioralTestCaseService;
import de.tum.in.www1.artemis.service.hestia.structural.StructuralSolutionEntryGenerationException;
import de.tum.in.www1.artemis.service.hestia.structural.StructuralTestCaseService;
import de.tum.in.www1.artemis.web.rest.errors.ConflictException;
import de.tum.in.www1.artemis.web.rest.errors.InternalServerErrorException;
import tech.jhipster.web.util.HeaderUtil;

/**
 * REST controller for managing {@link de.tum.in.www1.artemis.domain.hestia.ProgrammingExerciseSolutionEntry}.
 */
@RestController
@RequestMapping("api/")
public class ProgrammingExerciseSolutionEntryResource {

    private final Logger log = LoggerFactory.getLogger(ExerciseHintResource.class);

    private static final String ENTITY_NAME = "programmingExerciseSolutionEntry";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final ProgrammingExerciseSolutionEntryRepository programmingExerciseSolutionEntryRepository;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final CodeHintRepository codeHintRepository;

    private final ProgrammingExerciseTestCaseRepository programmingExerciseTestCaseRepository;

    private final AuthorizationCheckService authCheckService;

    private final StructuralTestCaseService structuralTestCaseService;

    private final BehavioralTestCaseService behavioralTestCaseService;

    public ProgrammingExerciseSolutionEntryResource(ProgrammingExerciseSolutionEntryRepository programmingExerciseSolutionEntryRepository,
            ProgrammingExerciseRepository programmingExerciseRepository, CodeHintRepository codeHintRepository,
            ProgrammingExerciseTestCaseRepository programmingExerciseTestCaseRepository, AuthorizationCheckService authCheckService,
            StructuralTestCaseService structuralTestCaseService, BehavioralTestCaseService behavioralTestCaseService) {
        this.programmingExerciseSolutionEntryRepository = programmingExerciseSolutionEntryRepository;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.codeHintRepository = codeHintRepository;
        this.programmingExerciseTestCaseRepository = programmingExerciseTestCaseRepository;
        this.authCheckService = authCheckService;
        this.structuralTestCaseService = structuralTestCaseService;
        this.behavioralTestCaseService = behavioralTestCaseService;
    }

    /**
     * GET programming-exercises/:exerciseId/solution-entries/:solutionEntryId : Get the solution entry with test cases and programming exercise
     * @param exerciseId of the exercise
     * @param solutionEntryId of the solution entry
     * @return the {@link ResponseEntity} with status {@code 200} and with body the solution entries with test cases and exercise,
     * or with status {@code 409 (Conflict)} if the exerciseId or solutionEntryId are not valid.
     */
    @GetMapping("programming-exercises/{exerciseId}/solution-entries/{solutionEntryId}")
    @PreAuthorize("hasRole('TA')")
    public ResponseEntity<ProgrammingExerciseSolutionEntry> getSolutionEntry(@PathVariable Long exerciseId, @PathVariable Long solutionEntryId) {
        log.debug("REST request to retrieve SolutionEntry : {}", solutionEntryId);
        // Reload the exercise from the database as we can't trust data from the client
        ProgrammingExercise exercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.TEACHING_ASSISTANT, exercise, null);

        ProgrammingExerciseSolutionEntry solutionEntry = programmingExerciseSolutionEntryRepository.findByIdWithTestCaseAndProgrammingExerciseElseThrow(solutionEntryId);

        if (!exerciseId.equals(solutionEntry.getTestCase().getExercise().getId())) {
            throw new ConflictException("A solution entry can only be retrieved if the exercise match", ENTITY_NAME, "exerciseIdsMismatch");
        }
        return ResponseEntity.ok(solutionEntry);
    }

    /**
     * GET programming-exercises/{exerciseId}/solution-entries: Get all solution entries with test cases for a programming exercise
     * @param exerciseId of the exercise
     * @return the {@link ResponseEntity} with status {@code 200} and with body the solution entries with test cases.
     */
    @GetMapping("programming-exercises/{exerciseId}/solution-entries")
    @PreAuthorize("hasRole('EDITOR')")
    public ResponseEntity<Set<ProgrammingExerciseSolutionEntry>> getAllSolutionEntries(@PathVariable Long exerciseId) {
        ProgrammingExercise exercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, exercise, null);

        var result = programmingExerciseSolutionEntryRepository.findByExerciseIdWithTestCases(exercise.getId());
        return ResponseEntity.ok(result);
    }

    /**
     * GET programming-exercises/:exerciseId/code-hints/:codeHintId/solution-entries : Get all solution entries for a given code hint
     * @param exerciseId of the exercise
     * @param codeHintId of the code hint
     * @return the {@link ResponseEntity} with status {@code 200} and with body the solution entries,
     * or with status {@code 409 (Conflict)} if the exerciseId or codeHintId are not valid.
     */
    @GetMapping("programming-exercises/{exerciseId}/code-hints/{codeHintId}/solution-entries")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Set<ProgrammingExerciseSolutionEntry>> getSolutionEntriesForCodeHint(@PathVariable Long exerciseId, @PathVariable Long codeHintId) {
        log.debug("REST request to retrieve SolutionEntry for CodeHint with id : {}", codeHintId);
        // Reload the exercise from the database as we can't trust data from the client
        ProgrammingExercise exercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.STUDENT, exercise, null);

        CodeHint codeHint = codeHintRepository.findByIdElseThrow(codeHintId);
        if (!exercise.getId().equals(codeHint.getExercise().getId())) {
            throw new ConflictException("A solution entry can only be retrieved if the code hint belongs to the exercise", ENTITY_NAME, "exerciseIdsMismatch");
        }

        Set<ProgrammingExerciseSolutionEntry> solutionEntries = programmingExerciseSolutionEntryRepository.findByCodeHintId(codeHintId);
        return ResponseEntity.ok(solutionEntries);
    }

    /**
     * GET programming-exercises/:exerciseId/test-cases/:testCaseId/solution-entries : Get all solution entries for a given test case
     * @param exerciseId of the exercise
     * @param testCaseId of the test case
     * @return the {@link ResponseEntity} with status {@code 200} and with body the solution entries,
     * or with status {@code 409 (Conflict)} if the exerciseId or testCaseId are not valid.
     */
    @GetMapping("programming-exercises/{exerciseId}/test-cases/{testCaseId}/solution-entries")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Set<ProgrammingExerciseSolutionEntry>> getSolutionEntriesForTestCase(@PathVariable Long exerciseId, @PathVariable Long testCaseId) {
        log.debug("REST request to retrieve SolutionEntry for ProgrammingExerciseTestCase with id : {}", testCaseId);
        // Reload the exercise from the database as we can't trust data from the client
        ProgrammingExercise exercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.STUDENT, exercise, null);

        ProgrammingExerciseTestCase testCase = programmingExerciseTestCaseRepository.findByIdWithExerciseElseThrow(testCaseId);
        if (!exercise.getId().equals(testCase.getExercise().getId())) {
            throw new ConflictException("A solution entry can only be retrieved if the test case belongs to the exercise", ENTITY_NAME, "exerciseIdsMismatch");
        }

        Set<ProgrammingExerciseSolutionEntry> solutionEntries = programmingExerciseSolutionEntryRepository.findByTestCaseId(testCaseId);
        return ResponseEntity.ok(solutionEntries);
    }

    /**
     * POST programming-exercises/:exerciseId/test-cases/:testCaseId/solution-entries : Create a solution entry for a test case
     * @param exerciseId of the exercise
     * @param testCaseId of the test case
     * @param programmingExerciseSolutionEntry the solution entry to be created
     * @return the {@link ResponseEntity} with status {@code 201} and with body the created solution entry,
     * or with status {@code 409 (Conflict)} if the exerciseId, testcaseId, or solution entry are not valid.
     */
    @PostMapping("programming-exercises/{exerciseId}/test-cases/{testCaseId}/solution-entries")
    @PreAuthorize("hasRole('EDITOR')")
    public ResponseEntity<ProgrammingExerciseSolutionEntry> createSolutionEntryForTestCase(@PathVariable Long exerciseId, @PathVariable Long testCaseId,
            @RequestBody ProgrammingExerciseSolutionEntry programmingExerciseSolutionEntry) throws URISyntaxException {
        log.debug("REST request to create SolutionEntry : {}", programmingExerciseSolutionEntry);
        ProgrammingExercise exercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, exercise, null);

        ProgrammingExerciseTestCase testCase = programmingExerciseTestCaseRepository.findByIdWithExerciseElseThrow(testCaseId);
        checkExerciseContainsTestCaseElseThrow(exercise, testCase);

        ProgrammingExerciseSolutionEntry result = programmingExerciseSolutionEntryRepository.save(programmingExerciseSolutionEntry);
        return ResponseEntity.created(new URI("/api/programming-exercises/" + exerciseId + "/solution-entries/" + result.getId()))
                .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, result.getId().toString())).body(result);
    }

    /**
     * PUT programming-exercises/:exerciseId/test-cases/:testCaseId/solution-entries/:solutionEntryId : Update a solution entry
     * @param exerciseId of the exercise
     * @param testCaseId of the test case
     * @param solutionEntryId of the solution entry
     * @param solutionEntry the updated solution entry
     * @return the {@link ResponseEntity} with status {@code 200} and with body the updated solution entry,
     * or with status {@code 409 (Conflict)} if the exerciseId, testcaseId, solutionEntryId, or solution entry are not valid.
     */
    @PutMapping("programming-exercises/{exerciseId}/test-cases/{testCaseId}/solution-entries/{solutionEntryId}")
    @PreAuthorize("hasRole('EDITOR')")
    public ResponseEntity<ProgrammingExerciseSolutionEntry> updateSolutionEntry(@PathVariable Long exerciseId, @PathVariable Long testCaseId, @PathVariable Long solutionEntryId,
            @RequestBody ProgrammingExerciseSolutionEntry solutionEntry) {
        log.debug("REST request to update SolutionEntry : {}", solutionEntry);
        ProgrammingExercise exercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, exercise, null);

        ProgrammingExerciseTestCase testCase = programmingExerciseTestCaseRepository.findByIdWithExerciseElseThrow(testCaseId);
        ProgrammingExerciseSolutionEntry solutionEntryBeforeSaving = programmingExerciseSolutionEntryRepository
                .findByIdWithTestCaseAndProgrammingExerciseElseThrow(solutionEntryId);

        checkExerciseContainsTestCaseElseThrow(exercise, testCase);
        if (!solutionEntryId.equals(solutionEntryBeforeSaving.getId())) {
            throw new ConflictException("A solution entry can only be updated if the solutionEntryIds match", ENTITY_NAME, "solutionEntryError");
        }

        ProgrammingExerciseSolutionEntry solutionEntryAfterSaving = programmingExerciseSolutionEntryRepository.save(solutionEntry);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, solutionEntryAfterSaving.getId().toString()))
                .body(solutionEntryAfterSaving);
    }

    /**
     * DELETE programming-exercises/:exerciseId/test-cases/:testCaseId/solution-entries/:solutionEntryId : Delete a solution entry
     * @param exerciseId of the exercise
     * @param testCaseId of the test case
     * @param solutionEntryId of the solution entry that is to be deleted
     * @return the {@link ResponseEntity} with status {@code 204},
     * or with status {@code 409 (Conflict)} if the exerciseId, testcaseId, or solutionEntryId are not valid.
     */
    @DeleteMapping("programming-exercises/{exerciseId}/test-cases/{testCaseId}/solution-entries/{solutionEntryId}")
    @PreAuthorize("hasRole('EDITOR')")
    public ResponseEntity<Void> deleteSolutionEntry(@PathVariable Long exerciseId, @PathVariable Long testCaseId, @PathVariable Long solutionEntryId) {
        log.debug("REST request to delete SolutionEntry with id : {}", solutionEntryId);
        ProgrammingExercise exercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, exercise, null);

        ProgrammingExerciseTestCase testCase = programmingExerciseTestCaseRepository.findByIdWithExerciseElseThrow(testCaseId);
        ProgrammingExerciseSolutionEntry solutionEntry = programmingExerciseSolutionEntryRepository.findByIdWithTestCaseAndProgrammingExerciseElseThrow(solutionEntryId);

        checkExerciseContainsTestCaseElseThrow(exercise, testCase);
        checkTestCaseContainsSolutionEntryElseThrow(testCase, solutionEntry);

        programmingExerciseSolutionEntryRepository.deleteById(solutionEntryId);
        return ResponseEntity.noContent().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, solutionEntry.getId().toString())).build();
    }

    /**
     * DELETE programming-exercises/:exerciseId/solution-entries: Delete all solution entries for a programming exercise
     * @param exerciseId of the exercise
     * @return the {@link ResponseEntity} with status {@code 204},
     * or with status {@code 404} if the exerciseId is not valid.
     */
    @DeleteMapping("programming-exercises/{exerciseId}/solution-entries")
    @PreAuthorize("hasRole('EDITOR')")
    public ResponseEntity<Void> deleteAllSolutionEntriesForExercise(@PathVariable Long exerciseId) {
        log.debug("REST request to delete all SolutionEntries for exercise with id: {}", exerciseId);
        ProgrammingExercise exercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, exercise, null);

        var entriesToDelete = programmingExerciseSolutionEntryRepository.findByExerciseIdWithTestCases(exercise.getId());

        programmingExerciseSolutionEntryRepository.deleteAll(entriesToDelete);
        return ResponseEntity.noContent().build();
    }

    /**
     * POST programming-exercises/:exerciseId/structural-solution-entries : Create the structural solution entries for a programming exercise
     *
     * @param exerciseId of the exercise
     * @return the {@link ResponseEntity} with status {@code 200} and with body the created solution entries,
     */
    @PostMapping("programming-exercises/{exerciseId}/structural-solution-entries")
    @PreAuthorize("hasRole('EDITOR')")
    public ResponseEntity<List<ProgrammingExerciseSolutionEntry>> createStructuralSolutionEntries(@PathVariable Long exerciseId) {
        log.debug("REST request to create structural solution entries");
        ProgrammingExercise exercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, exercise, null);

        try {
            var solutionEntries = structuralTestCaseService.generateStructuralSolutionEntries(exercise);
            return ResponseEntity.ok(solutionEntries);
        }
        catch (StructuralSolutionEntryGenerationException e) {
            log.error("Unable to create structural solution entries", e);
            throw new InternalServerErrorException(e.getMessage());
        }
    }

    /**
     * POST programming-exercises/:exerciseId/behavioral-solution-entries : Create the behavioral solution entries for a programming exercise
     *
     * @param exerciseId of the exercise
     * @return the {@link ResponseEntity} with status {@code 200} and with body the created solution entries,
     */
    @PostMapping("programming-exercises/{exerciseId}/behavioral-solution-entries")
    @PreAuthorize("hasRole('EDITOR')")
    public ResponseEntity<List<ProgrammingExerciseSolutionEntry>> createBehavioralSolutionEntries(@PathVariable Long exerciseId) {
        log.debug("REST request to create behavioral solution entries");
        ProgrammingExercise exercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, exercise, null);

        try {
            var solutionEntries = behavioralTestCaseService.generateBehavioralSolutionEntries(exercise);
            return ResponseEntity.ok(solutionEntries);
        }
        catch (BehavioralSolutionEntryGenerationException e) {
            log.error("Unable to create behavioral solution entries", e);
            throw new InternalServerErrorException(e.getMessage());
        }
    }

    private void checkExerciseContainsTestCaseElseThrow(ProgrammingExercise exercise, ProgrammingExerciseTestCase testCase) {
        if (!exercise.getId().equals(testCase.getExercise().getId())) {
            throw new ConflictException("The test case of the solution entry does not belong to the exercise.", ENTITY_NAME, "exerciseIdsMismatch");
        }
    }

    private void checkTestCaseContainsSolutionEntryElseThrow(ProgrammingExerciseTestCase testCase, ProgrammingExerciseSolutionEntry solutionEntry) {
        if (solutionEntry.getTestCase() == null || !testCase.getId().equals(solutionEntry.getTestCase().getId())) {
            throw new ConflictException("The test case of the solution entry does not belong to the solution entry.", ENTITY_NAME, "solutionEntryError");
        }
    }
}
