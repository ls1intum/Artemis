package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.forbidden;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.service.*;

/**
 * REST controller for managing ProgrammingExerciseTestCase. Test cases are created automatically from build run results which is why there are not endpoints available for POST,
 * PUT or DELETE.
 */
@RestController
@RequestMapping("/api")
public class ProgrammingExerciseGradingResource {

    private final Logger log = LoggerFactory.getLogger(ProgrammingExerciseGradingResource.class);

    private final ProgrammingExerciseGradingService programmingExerciseGradingService;

    private final ProgrammingExerciseTestCaseService programmingExerciseTestCaseService;

    private final ProgrammingExerciseService programmingExerciseService;

    private final AuthorizationCheckService authCheckService;

    private final UserService userService;

    private final ResultRepository resultRepository;

    public ProgrammingExerciseGradingResource(ProgrammingExerciseGradingService programmingExerciseGradingService,
            ProgrammingExerciseTestCaseService programmingExerciseTestCaseService, ProgrammingExerciseService programmingExerciseService,
            AuthorizationCheckService authCheckService, UserService userService, ResultRepository resultRepository) {
        this.programmingExerciseGradingService = programmingExerciseGradingService;
        this.programmingExerciseTestCaseService = programmingExerciseTestCaseService;
        this.programmingExerciseService = programmingExerciseService;
        this.authCheckService = authCheckService;
        this.userService = userService;
        this.resultRepository = resultRepository;
    }

    /**
     * Use with care: Set the weight of all test cases of an exercise to 1.
     *
     * @param exerciseId the id of the exercise to reset the test case weights of.
     * @return the updated set of test cases for the programming exercise.
     */
    @PatchMapping(Endpoints.RESET)
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<List<ProgrammingExerciseTestCase>> resetGradingConfiguration(@PathVariable Long exerciseId) {
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
    public ResponseEntity<Integer> reEvaluateGradedResults(@PathVariable Long exerciseId) {
        log.debug("REST request to reset the weights of exercise {}", exerciseId);
        ProgrammingExercise programmingExercise = programmingExerciseService.findWithTemplateAndSolutionParticipationWithResultsById(exerciseId);
        Course course = programmingExercise.getCourseViaExerciseGroupOrCourseMember();
        User user = userService.getUserWithGroupsAndAuthorities();

        if (!authCheckService.isAtLeastInstructorInCourse(course, user)) {
            return forbidden();
        }

        List<Result> updatedResults = programmingExerciseGradingService.updateAllResults(programmingExercise);
        resultRepository.saveAll(updatedResults);
        return ResponseEntity.ok(updatedResults.size());
    }

    public static final class Endpoints {

        private static final String GRADING = "/programming-exercise/{exerciseId}/grading";

        public static final String RESET = GRADING + "/reset";

        public static final String RE_EVALUATE = GRADING + "/re-evaluate";

        private Endpoints() {
        }
    }
}
