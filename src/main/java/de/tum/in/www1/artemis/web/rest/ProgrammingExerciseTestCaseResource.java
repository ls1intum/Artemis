package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.forbidden;
import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.notFound;

import java.util.NoSuchElementException;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.ProgrammingExerciseTestCase;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseTestCaseRepository;
import de.tum.in.www1.artemis.service.ProgrammingExerciseService;
import de.tum.in.www1.artemis.service.ProgrammingExerciseTestCaseService;
import de.tum.in.www1.artemis.web.rest.dto.ProgrammingExerciseTestCaseWeightUpdate;

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

    @PostMapping(value = "programming-exercise/{exerciseId}/test-cases/{testCaseId}/update-weight")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Void> updateWeight(@PathVariable Long exerciseId, @PathVariable Long testCaseId,
            @RequestBody ProgrammingExerciseTestCaseWeightUpdate testCaseWeightUpdate) {
        log.debug("REST request to update the weight of test case {} to {}", testCaseId, testCaseWeightUpdate.getWeight());
        try {
            // Retrieve programming exercise to check availability & permissions.
            ProgrammingExercise programmingExercise = programmingExerciseService.findByIdWithTestCases(exerciseId);
            programmingExercise.getTestCases().stream().filter(testCase -> testCase.getId().equals(testCaseId)).findFirst().orElseThrow(NoSuchElementException::new);
            programmingExerciseTestCaseService.updateWeight(testCaseId, testCaseWeightUpdate.getWeight());
            return new ResponseEntity<>(HttpStatus.OK);
        }
        catch (IllegalAccessException ex) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }
        catch (NoSuchElementException ex) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }
}
