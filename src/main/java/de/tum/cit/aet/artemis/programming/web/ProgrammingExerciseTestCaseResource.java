package de.tum.cit.aet.artemis.programming.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastEditor;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastTutor;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseTestCase;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseTestCaseRepository;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseTestCaseService;
import de.tum.cit.aet.artemis.web.rest.dto.ProgrammingExerciseTestCaseDTO;

/**
 * REST controller for managing ProgrammingExerciseTestCase. Test cases are created automatically from build run results which is why there are no endpoints available for POST,
 * PUT or DELETE.
 */
@Profile(PROFILE_CORE)
@RestController
@RequestMapping("api/")
public class ProgrammingExerciseTestCaseResource {

    private static final Logger log = LoggerFactory.getLogger(ProgrammingExerciseTestCaseResource.class);

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
    @GetMapping("programming-exercises/{exerciseId}/test-cases")
    @EnforceAtLeastTutor
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
     * @param exerciseId                              of exercise the test cases belong to.
     * @param testCaseProgrammingExerciseTestCaseDTOS of the test cases to update the weights and visibility of.
     * @return the set of test cases for the given programming exercise.
     */
    @PatchMapping("programming-exercises/{exerciseId}/update-test-cases")
    @EnforceAtLeastEditor
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
     * Use with care: Resets all test cases of an exercise to their initial configuration
     * Set all test case weights to 1, all bonus multipliers to 1, all bonus points to 0 and visibility to always.
     *
     * @param exerciseId the id of the exercise to reset the test case weights of.
     * @return the updated set of test cases for the programming exercise.
     */
    @PatchMapping("programming-exercises/{exerciseId}/test-cases/reset")
    @EnforceAtLeastEditor
    public ResponseEntity<List<ProgrammingExerciseTestCase>> resetTestCases(@PathVariable Long exerciseId) {
        log.debug("REST request to reset the test case weights of exercise {}", exerciseId);
        ProgrammingExercise programmingExercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
        User user = userRepository.getUserWithGroupsAndAuthorities();

        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, programmingExercise, user);
        programmingExerciseTestCaseService.logTestCaseReset(user, programmingExercise, programmingExercise.getCourseViaExerciseGroupOrCourseMember());

        List<ProgrammingExerciseTestCase> testCases = programmingExerciseTestCaseService.reset(programmingExercise);
        return ResponseEntity.ok(testCases);
    }
}
