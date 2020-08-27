package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.*;

import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseTestCaseRepository;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.service.*;
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

    private final AuthorizationCheckService authCheckService;

    private final UserService userService;

    private final ResultRepository resultRepository;

    public ProgrammingExerciseTestCaseResource(ProgrammingExerciseTestCaseRepository programmingExerciseTestCaseRepository,
            ProgrammingExerciseTestCaseService programmingExerciseTestCaseService, ProgrammingExerciseService programmingExerciseService,
            AuthorizationCheckService authCheckService, UserService userService, ResultRepository resultRepository) {
        this.programmingExerciseTestCaseRepository = programmingExerciseTestCaseRepository;
        this.programmingExerciseTestCaseService = programmingExerciseTestCaseService;
        this.programmingExerciseService = programmingExerciseService;
        this.authCheckService = authCheckService;
        this.userService = userService;
        this.resultRepository = resultRepository;
    }

    /**
     * Get the exercise's test cases for the the given exercise id.
     *
     * @param exerciseId of the the exercise.
     * @return the found test cases or an empty list if no test cases were found.
     */
    @GetMapping(Endpoints.TEST_CASES)
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Set<ProgrammingExerciseTestCase>> getTestCases(@PathVariable Long exerciseId) {
        log.debug("REST request to get test cases for programming exercise {}", exerciseId);
        ProgrammingExercise programmingExercise = programmingExerciseService.findWithTemplateParticipationAndSolutionParticipationById(exerciseId);

        Course course = programmingExercise.getCourseViaExerciseGroupOrCourseMember();
        User user = userService.getUserWithGroupsAndAuthorities();

        if (!authCheckService.isAtLeastTeachingAssistantInCourse(course, user)) {
            return forbidden();
        }

        Set<ProgrammingExerciseTestCase> testCases = programmingExerciseTestCaseRepository.findByExerciseId(exerciseId);
        return ResponseEntity.ok(testCases);
    }

    /**
     * Update the changable fields of the provided test case dtos. We don't transfer the whole test case object here, because we need to make sure that only weights can be updated! Will
     * only return test case objects in the response that could be updated.
     *
     * @param exerciseId            of exercise the test cases belong to.
     * @param testCaseProgrammingExerciseTestCaseDTOS of the test cases to update the weights and afterDueDate flag of.
     * @return the set of test cases for the given programming exercise.
     */
    @PatchMapping(Endpoints.UPDATE_TEST_CASES)
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Set<ProgrammingExerciseTestCase>> updateTestCases(@PathVariable Long exerciseId,
            @RequestBody Set<ProgrammingExerciseTestCaseDTO> testCaseProgrammingExerciseTestCaseDTOS) {
        log.debug("REST request to update the weights {} of the exercise {}", testCaseProgrammingExerciseTestCaseDTOS, exerciseId);
        ProgrammingExercise programmingExercise = programmingExerciseService.findWithTemplateParticipationAndSolutionParticipationById(exerciseId);
        Course course = programmingExercise.getCourseViaExerciseGroupOrCourseMember();
        User user = userService.getUserWithGroupsAndAuthorities();

        if (!authCheckService.isAtLeastInstructorInCourse(course, user)) {
            return forbidden();
        }

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
     * @param exerciseId the id of the exercise to reset the test case weights of.
     * @return the updated set of test cases for the programming exercise.
     */
    @PatchMapping(Endpoints.RESET)
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<List<ProgrammingExerciseTestCase>> reset(@PathVariable Long exerciseId) {
        log.debug("REST request to reset the weights of exercise {}", exerciseId);
        ProgrammingExercise programmingExercise = programmingExerciseService.findWithTemplateParticipationAndSolutionParticipationById(exerciseId);
        Course course = programmingExercise.getCourseViaExerciseGroupOrCourseMember();
        User user = userService.getUserWithGroupsAndAuthorities();

        if (!authCheckService.isAtLeastInstructorInCourse(course, user)) {
            return forbidden();
        }
        List<ProgrammingExerciseTestCase> testCases = programmingExerciseTestCaseService.reset(exerciseId);
        return ResponseEntity.ok(testCases);
    }

    /**
     * Use with care: Re-evaluates all latest automatic results for the given programming exercise.
     *
     * @param exerciseId the id of the exercise to re-evaluate the test case weights of.
     * @return the number of results that were updated.
     */
    @PutMapping(Endpoints.RE_EVALUATE)
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Integer> reEvaluateProgrammingExerciseFromTestCases(@PathVariable Long exerciseId) {
        log.debug("REST request to reset the weights of exercise {}", exerciseId);
        ProgrammingExercise programmingExercise = programmingExerciseService.findWithTemplateAndSolutionParticipationWithResultsById(exerciseId);
        Course course = programmingExercise.getCourseViaExerciseGroupOrCourseMember();
        User user = userService.getUserWithGroupsAndAuthorities();

        if (!authCheckService.isAtLeastInstructorInCourse(course, user)) {
            return forbidden();
        }

        List<Result> updatedResults = programmingExerciseTestCaseService.updateAllResultsFromTestCases(programmingExercise);
        resultRepository.saveAll(updatedResults);
        return ResponseEntity.ok(updatedResults.size());
    }

    public static final class Endpoints {

        private static final String PROGRAMMING_EXERCISE = "/programming-exercise/{exerciseId}";

        public static final String TEST_CASES = PROGRAMMING_EXERCISE + "/test-cases";

        public static final String RESET = PROGRAMMING_EXERCISE + "/test-cases/reset";

        public static final String UPDATE_TEST_CASES = PROGRAMMING_EXERCISE + "/update-test-cases";

        public static final String RE_EVALUATE = PROGRAMMING_EXERCISE + "/re-evaluate";

        private Endpoints() {
        }
    }
}
