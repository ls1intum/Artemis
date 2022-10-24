package de.tum.in.www1.artemis.web.rest;

import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.ProgrammingExerciseTestCase;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.programming.ProgrammingExerciseService;
import de.tum.in.www1.artemis.service.programming.ProgrammingExerciseTestCaseService;
import de.tum.in.www1.artemis.web.rest.dto.ProgrammingExerciseTestCaseDTO;

/**
 * REST controller for managing ProgrammingExerciseTestCase. Test cases are created automatically from build run results which is why there are no endpoints available for POST,
 * PUT or DELETE.
 */
@RestController
@RequestMapping("/api")
public class ProgrammingExerciseTestCaseResource {

    private final Logger log = LoggerFactory.getLogger(ProgrammingExerciseTestCaseResource.class);

    private final ProgrammingExerciseTestCaseRepository programmingExerciseTestCaseRepository;

    private final ProgrammingExerciseTestCaseService programmingExerciseTestCaseService;

    private final ProgrammingExerciseService programmingExerciseService;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final AuthorizationCheckService authCheckService;

    private final UserRepository userRepository;

    public ProgrammingExerciseTestCaseResource(ProgrammingExerciseTestCaseRepository programmingExerciseTestCaseRepository,
            ProgrammingExerciseTestCaseService programmingExerciseTestCaseService, ProgrammingExerciseService programmingExerciseService,
            ProgrammingExerciseRepository programmingExerciseRepository, AuthorizationCheckService authCheckService, UserRepository userRepository) {
        this.programmingExerciseTestCaseRepository = programmingExerciseTestCaseRepository;
        this.programmingExerciseTestCaseService = programmingExerciseTestCaseService;
        this.programmingExerciseService = programmingExerciseService;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.authCheckService = authCheckService;
        this.userRepository = userRepository;
    }

    /**
     * Get the exercise's test cases for the given exercise id.
     *
     * @param exerciseId of the exercise.
     * @return the found test cases or an empty list if no test cases were found.
     */
    @GetMapping(Endpoints.TEST_CASES)
    @PreAuthorize("hasRole('TA')")
    public ResponseEntity<Set<ProgrammingExerciseTestCase>> getTestCases(@PathVariable Long exerciseId) {
        log.debug("REST request to get test cases for programming exercise {}", exerciseId);
        var programmingExercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.TEACHING_ASSISTANT, programmingExercise, null);

        Set<ProgrammingExerciseTestCase> testCases = programmingExerciseTestCaseRepository.findByExerciseId(exerciseId);
        return ResponseEntity.ok(testCases);
    }

    /**
     * Update the changeable fields of the provided test case dtos.
     * We don't transfer the whole test case object here, because we need to make sure that only weights and visibility can be updated!
     * Will only return test case objects in the response that could be updated.
     *
     * @param exerciseId            of exercise the test cases belong to.
     * @param testCaseProgrammingExerciseTestCaseDTOS of the test cases to update the weights and visibility of.
     * @return the set of test cases for the given programming exercise.
     */
    @PatchMapping(Endpoints.UPDATE_TEST_CASES)
    @PreAuthorize("hasRole('EDITOR')")
    public ResponseEntity<Set<ProgrammingExerciseTestCase>> updateTestCases(@PathVariable Long exerciseId,
            @RequestBody Set<ProgrammingExerciseTestCaseDTO> testCaseProgrammingExerciseTestCaseDTOS) {
        log.debug("REST request to update the weights {} of the exercise {}", testCaseProgrammingExerciseTestCaseDTOS, exerciseId);
        var programmingExercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, programmingExercise, null);

        Set<ProgrammingExerciseTestCase> updatedTests = programmingExerciseTestCaseService.update(exerciseId, testCaseProgrammingExerciseTestCaseDTOS);
        // A test case is now marked as AFTER_DUE_DATE: a scheduled score update might be needed.
        if (updatedTests.stream().anyMatch(ProgrammingExerciseTestCase::isAfterDueDate)) {
            programmingExerciseService.scheduleOperations(programmingExercise.getId());
        }

        // We don't need the linked exercise here.
        for (ProgrammingExerciseTestCase testCase : updatedTests) {
            testCase.setExercise(null);
        }
        return ResponseEntity.ok(updatedTests);
    }

    /**
     * Use with care: Set the weight of all test cases of an exercise to 1.
     *
     * @param exerciseId the id of the exercise to reset the test case weights of.
     * @return the updated set of test cases for the programming exercise.
     */
    @PatchMapping(Endpoints.RESET)
    @PreAuthorize("hasRole('EDITOR')")
    public ResponseEntity<List<ProgrammingExerciseTestCase>> resetTestCases(@PathVariable Long exerciseId) {
        log.debug("REST request to reset the test case weights of exercise {}", exerciseId);
        var programmingExercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
        var user = userRepository.getUserWithGroupsAndAuthorities();
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, programmingExercise, user);
        programmingExerciseTestCaseService.logTestCaseReset(user, programmingExercise, programmingExercise.getCourseViaExerciseGroupOrCourseMember());
        List<ProgrammingExerciseTestCase> testCases = programmingExerciseTestCaseService.reset(exerciseId);
        return ResponseEntity.ok(testCases);
    }

    public static final class Endpoints {

        private static final String PROGRAMMING_EXERCISE = "/programming-exercises/{exerciseId}";

        public static final String TEST_CASES = PROGRAMMING_EXERCISE + "/test-cases";

        public static final String UPDATE_TEST_CASES = PROGRAMMING_EXERCISE + "/update-test-cases";

        public static final String RESET = PROGRAMMING_EXERCISE + "/test-cases/reset";

        private Endpoints() {
        }
    }
}
