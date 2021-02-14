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
import de.tum.in.www1.artemis.service.programming.ProgrammingExerciseGradingService;
import de.tum.in.www1.artemis.service.programming.ProgrammingExerciseRetrievalService;
import de.tum.in.www1.artemis.service.user.UserRetrievalService;
import de.tum.in.www1.artemis.web.rest.dto.ProgrammingExerciseGradingStatisticsDTO;

/**
 * REST controller for managing ProgrammingExerciseTestCase. Test cases are created automatically from build run results which is why there are not endpoints available for POST,
 * PUT or DELETE.
 */
@RestController
@RequestMapping("/api")
public class ProgrammingExerciseGradingResource {

    public static final String RE_EVALUATE = "/programming-exercise/{exerciseId}/grading/re-evaluate";

    public static final String STATISTICS = "/programming-exercise/{exerciseId}/grading/statistics";

    private final Logger log = LoggerFactory.getLogger(ProgrammingExerciseGradingResource.class);

    private final ProgrammingExerciseGradingService programmingExerciseGradingService;

    private final ProgrammingExerciseRetrievalService programmingExerciseRetrievalService;

    private final AuthorizationCheckService authCheckService;

    private final UserRetrievalService userRetrievalService;

    private final ResultRepository resultRepository;

    public ProgrammingExerciseGradingResource(ProgrammingExerciseGradingService programmingExerciseGradingService,
            ProgrammingExerciseRetrievalService programmingExerciseRetrievalService, AuthorizationCheckService authCheckService, UserRetrievalService userRetrievalService,
            ResultRepository resultRepository) {
        this.programmingExerciseGradingService = programmingExerciseGradingService;
        this.programmingExerciseRetrievalService = programmingExerciseRetrievalService;
        this.authCheckService = authCheckService;
        this.userRetrievalService = userRetrievalService;
        this.resultRepository = resultRepository;
    }

    /**
     * Use with care: Re-evaluates all latest automatic results for the given programming exercise.
     *
     * @param exerciseId the id of the exercise to re-evaluate the test case weights of.
     * @return the number of results that were updated.
     */
    @PutMapping(RE_EVALUATE)
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Integer> reEvaluateGradedResults(@PathVariable Long exerciseId) {
        log.debug("REST request to reset the weights of exercise {}", exerciseId);
        ProgrammingExercise programmingExercise = programmingExerciseRetrievalService.findWithTemplateAndSolutionParticipationWithResultsById(exerciseId);
        Course course = programmingExercise.getCourseViaExerciseGroupOrCourseMember();
        User user = userRetrievalService.getUserWithGroupsAndAuthorities();

        if (!authCheckService.isAtLeastInstructorInCourse(course, user)) {
            return forbidden();
        }

        List<Result> updatedResults = programmingExerciseGradingService.updateAllResults(programmingExercise);

        programmingExerciseGradingService.logReEvaluate(user, programmingExercise, course, updatedResults);
        resultRepository.saveAll(updatedResults);
        return ResponseEntity.ok(updatedResults.size());
    }

    /**
     * Get the exercise's test case statistics for the the given exercise id.
     *
     * @param exerciseId of the the exercise.
     * @return the test case statistics for the exercise.
     */
    @GetMapping(STATISTICS)
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<ProgrammingExerciseGradingStatisticsDTO> getGradingStatistics(@PathVariable Long exerciseId) {
        log.debug("REST request to get test case statistics for programming exercise {}", exerciseId);
        ProgrammingExercise programmingExercise = programmingExerciseRetrievalService.findWithTemplateParticipationAndSolutionParticipationById(exerciseId);

        Course course = programmingExercise.getCourseViaExerciseGroupOrCourseMember();
        User user = userRetrievalService.getUserWithGroupsAndAuthorities();

        if (!authCheckService.isAtLeastInstructorInCourse(course, user)) {
            return forbidden();
        }

        var statistics = programmingExerciseGradingService.generateGradingStatistics(exerciseId);
        return ResponseEntity.ok(statistics);
    }

}
