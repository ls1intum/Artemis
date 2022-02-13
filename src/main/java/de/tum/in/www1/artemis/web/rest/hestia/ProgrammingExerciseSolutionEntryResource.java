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
import de.tum.in.www1.artemis.web.rest.errors.ConflictException;
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

    public ProgrammingExerciseSolutionEntryResource(ProgrammingExerciseSolutionEntryRepository programmingExerciseSolutionEntryRepository,
            ProgrammingExerciseRepository programmingExerciseRepository, CodeHintRepository codeHintRepository,
            ProgrammingExerciseTestCaseRepository programmingExerciseTestCaseRepository, AuthorizationCheckService authCheckService) {
        this.programmingExerciseSolutionEntryRepository = programmingExerciseSolutionEntryRepository;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.codeHintRepository = codeHintRepository;
        this.programmingExerciseTestCaseRepository = programmingExerciseTestCaseRepository;
        this.authCheckService = authCheckService;
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
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, exercise, null);

        ProgrammingExerciseSolutionEntry solutionEntry = programmingExerciseSolutionEntryRepository.findByIdWithTestCaseAndProgrammingExerciseElseThrow(solutionEntryId);

        if (!exerciseId.equals(solutionEntry.getTestCase().getExercise().getId())) {
            throw new ConflictException("A solution entry can only be retrieved if the exercise match", ENTITY_NAME, "exerciseIdsMismatch");
        }
        return ResponseEntity.ok(solutionEntry);
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
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, exercise, null);

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
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, exercise, null);

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
            throw new ConflictException("A solution entry can only be updated if the solutionEntryIds match", ENTITY_NAME, "solutionEntryIdsMismatch");
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
        return ResponseEntity.noContent().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, solutionEntry.toString())).build();
    }

    private void checkExerciseContainsTestCaseElseThrow(ProgrammingExercise exercise, ProgrammingExerciseTestCase testCase) {
        if (!exercise.getId().equals(testCase.getExercise().getId())) {
            throw new ConflictException("The test case of the solution entry does not belong to the exercise.", ENTITY_NAME, "exerciseIdsMismatch");
        }
    }

    private void checkTestCaseContainsSolutionEntryElseThrow(ProgrammingExerciseTestCase testCase, ProgrammingExerciseSolutionEntry solutionEntry) {
        if (solutionEntry.getTestCase() == null || !testCase.getId().equals(solutionEntry.getTestCase().getId())) {
            throw new ConflictException("The test case of the solution entry does not belong to the solution entry.", ENTITY_NAME, "solutionEntryTestCaseMismatch");
        }
    }
}
