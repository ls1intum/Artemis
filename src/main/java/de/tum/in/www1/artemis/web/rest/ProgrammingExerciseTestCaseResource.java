package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.forbidden;
import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.notFound;

import java.util.Set;
import javassist.NotFoundException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.ProgrammingExerciseTestCase;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseTestCaseRepository;
import de.tum.in.www1.artemis.service.ProgrammingExerciseService;

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

    private final ProgrammingExerciseService programmingExerciseService;

    public ProgrammingExerciseTestCaseResource(ProgrammingExerciseTestCaseRepository programmingExerciseTestCaseRepository, ProgrammingExerciseService programmingExerciseService) {
        this.programmingExerciseTestCaseRepository = programmingExerciseTestCaseRepository;
        this.programmingExerciseService = programmingExerciseService;
    }

    @GetMapping(value = "/programming-exercises-test-cases/{exerciseId}")
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
        catch (NotFoundException ex) {
            return notFound();
        }
    }
}
