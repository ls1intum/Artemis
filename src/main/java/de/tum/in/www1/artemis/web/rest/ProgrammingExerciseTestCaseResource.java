package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.forbidden;
import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.notFound;

import java.util.NoSuchElementException;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.ProgrammingExerciseTestCase;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseTestCaseRepository;
import de.tum.in.www1.artemis.service.ProgrammingExerciseService;
import de.tum.in.www1.artemis.service.ProgrammingExerciseTestCaseService;
import de.tum.in.www1.artemis.web.rest.dto.ProgrammingExerciseTestCaseDTO;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * REST controller for managing ProgrammingExerciseTestCase. Test cases are created automatically from build run results which is why there are not endpoints available for POST,
 * PUT or DELETE.
 */
@RestController
@RequestMapping("/api")
public class ProgrammingExerciseTestCaseResource {

    private final Logger log = LoggerFactory.getLogger(ProgrammingExerciseTestCaseResource.class);

    private static final String ENTITY_NAME = "programmingExerciseTestCase";

    private final ProgrammingExerciseTestCaseRepository programmingExerciseTestCaseRepository;

    private final ProgrammingExerciseTestCaseService programmingExerciseTestCaseService;

    private final ProgrammingExerciseService programmingExerciseService;

    public ProgrammingExerciseTestCaseResource(ProgrammingExerciseTestCaseRepository programmingExerciseTestCaseRepository,
            ProgrammingExerciseTestCaseService programmingExerciseTestCaseService, ProgrammingExerciseService programmingExerciseService) {
        this.programmingExerciseTestCaseRepository = programmingExerciseTestCaseRepository;
        this.programmingExerciseTestCaseService = programmingExerciseTestCaseService;
        this.programmingExerciseService = programmingExerciseService;
    }

    @GetMapping(value = "programming-exercise/{exerciseId}/test-cases")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Set<ProgrammingExerciseTestCase>> getTestCases(@PathVariable Long exerciseId) {
        log.debug("REST request to get test cases for programming exercise {}", exerciseId);
        try {
            // Retrieve programming exercise to check availability & permissions.
            programmingExerciseService.findById(exerciseId);
            Set<ProgrammingExerciseTestCase> testCases = programmingExerciseTestCaseRepository.findByExerciseId(exerciseId);
            return ResponseEntity.ok(testCases);
        }
        catch (IllegalAccessException ex) {
            return forbidden();
        }
        catch (NoSuchElementException ex) {
            return notFound();
        }
    }

    /**
     * Update the changable fields of the provided test case dtos. We don't transfer the whole test case object here, because we need to make sure that only weights can be updated! Will
     * only return test case objects in the response that could be updated.
     *
     * @param exerciseId            of exercise the test cases belong to.
     * @param testCaseProgrammingExerciseTestCaseDTOS of the test cases to update the weights and afterDueDate flag of.
     * @return
     */
    @PatchMapping(value = "programming-exercise/{exerciseId}/update-test-cases")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Set<ProgrammingExerciseTestCase>> updateWeights(@PathVariable Long exerciseId,
            @RequestBody Set<ProgrammingExerciseTestCaseDTO> testCaseProgrammingExerciseTestCaseDTOS) {
        log.debug("REST request to update the weights {} of the exercise {}", testCaseProgrammingExerciseTestCaseDTOS, exerciseId);
        try {
            Set<ProgrammingExerciseTestCase> updatedTests = programmingExerciseTestCaseService.update(exerciseId, testCaseProgrammingExerciseTestCaseDTOS);
            // We don't need the linked exercise here.
            for (ProgrammingExerciseTestCase testCase : updatedTests) {
                testCase.setExercise(null);
            }
            return ResponseEntity.ok(updatedTests);
        }
        catch (IllegalAccessException ex) {
            return forbidden();
        }
        catch (EntityNotFoundException ex) {
            return notFound();
        }
    }

    /**
     * Use with care: Set the weight of all test cases of an exercise to 1.
     *
     * @param exerciseId
     * @return
     */
    @PatchMapping(value = "programming-exercise/{exerciseId}/test-cases/reset-weights")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Set<ProgrammingExerciseTestCase>> resetWeights(@PathVariable Long exerciseId) {
        log.debug("REST request to reset the weights of exercise {}", exerciseId);
        try {
            // Retrieve programming exercise to check availability & permissions.
            programmingExerciseService.findById(exerciseId);
            Set<ProgrammingExerciseTestCase> testCases = programmingExerciseTestCaseService.resetWeights(exerciseId);
            return ResponseEntity.ok(testCases);
        }
        catch (IllegalAccessException ex) {
            return forbidden();
        }
        catch (NoSuchElementException ex) {
            return notFound();
        }
    }

}
