package de.tum.cit.aet.artemis.programming.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.repository.ResultRepository;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastEditor;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastInstructor;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.dto.ProgrammingExerciseGradingStatisticsDTO;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseGradingService;

/**
 * REST controller for managing ProgrammingExerciseTestCase. Test cases are created automatically from build run results which is why there are no endpoints available for POST,
 * PUT or DELETE.
 */
@Profile(PROFILE_CORE)
@RestController
@RequestMapping("api/")
public class ProgrammingExerciseGradingResource {

    private static final Logger log = LoggerFactory.getLogger(ProgrammingExerciseGradingResource.class);

    private final ProgrammingExerciseGradingService programmingExerciseGradingService;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final AuthorizationCheckService authCheckService;

    private final UserRepository userRepository;

    private final ResultRepository resultRepository;

    public ProgrammingExerciseGradingResource(ProgrammingExerciseGradingService programmingExerciseGradingService, ProgrammingExerciseRepository programmingExerciseRepository,
            AuthorizationCheckService authCheckService, UserRepository userRepository, ResultRepository resultRepository) {
        this.programmingExerciseGradingService = programmingExerciseGradingService;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.authCheckService = authCheckService;
        this.userRepository = userRepository;
        this.resultRepository = resultRepository;
    }

    /**
     * Use with care: Re-evaluates all latest automatic results for the given programming exercise.
     *
     * @param exerciseId the id of the exercise to re-evaluate the test case weights of.
     * @return the number of results that were updated.
     */
    @PutMapping("programming-exercises/{exerciseId}/grading/re-evaluate")
    @EnforceAtLeastInstructor
    public ResponseEntity<Integer> reEvaluateGradedResults(@PathVariable Long exerciseId) {
        log.debug("REST request to re-evaluate the graded results of exercise {}", exerciseId);
        ProgrammingExercise programmingExercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
        Course course = programmingExercise.getCourseViaExerciseGroupOrCourseMember();
        User user = userRepository.getUserWithGroupsAndAuthorities();

        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.INSTRUCTOR, programmingExercise, user);

        List<Result> updatedResults = programmingExerciseGradingService.updateAllResults(programmingExercise);

        programmingExerciseGradingService.logReEvaluate(user, programmingExercise, course, updatedResults);
        resultRepository.saveAll(updatedResults);
        return ResponseEntity.ok(updatedResults.size());
    }

    /**
     * Get the exercise's test case statistics for the given exercise id.
     *
     * @param exerciseId of the exercise.
     * @return the test case statistics for the exercise.
     */
    @GetMapping("programming-exercises/{exerciseId}/grading/statistics")
    @EnforceAtLeastEditor
    public ResponseEntity<ProgrammingExerciseGradingStatisticsDTO> getGradingStatistics(@PathVariable Long exerciseId) {
        log.debug("REST request to get test case statistics for programming exercise {}", exerciseId);
        ProgrammingExercise programmingExercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(exerciseId);

        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, programmingExercise, null);

        var statistics = programmingExerciseGradingService.generateGradingStatistics(exerciseId);
        return ResponseEntity.ok(statistics);
    }

}
