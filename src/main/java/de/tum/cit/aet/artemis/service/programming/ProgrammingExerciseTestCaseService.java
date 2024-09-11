package de.tum.cit.aet.artemis.service.programming;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.audit.AuditEvent;
import org.springframework.boot.actuate.audit.AuditEventRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.domain.Course;
import de.tum.cit.aet.artemis.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.domain.ProgrammingExerciseTestCase;
import de.tum.cit.aet.artemis.domain.User;
import de.tum.cit.aet.artemis.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.repository.ProgrammingExerciseTestCaseRepository;
import de.tum.cit.aet.artemis.service.hestia.ProgrammingExerciseTaskService;
import de.tum.cit.aet.artemis.web.rest.dto.ProgrammingExerciseTestCaseDTO;
import de.tum.cit.aet.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.cit.aet.artemis.web.rest.errors.EntityNotFoundException;
import de.tum.cit.aet.artemis.web.rest.errors.ErrorConstants;

@Profile(PROFILE_CORE)
@Service
public class ProgrammingExerciseTestCaseService {

    private static final Logger log = LoggerFactory.getLogger(ProgrammingExerciseTestCaseService.class);

    private final ProgrammingExerciseTestCaseRepository testCaseRepository;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final ProgrammingTriggerService programmingTriggerService;

    private final AuditEventRepository auditEventRepository;

    private final ProgrammingExerciseTaskService programmingExerciseTaskService;

    public ProgrammingExerciseTestCaseService(ProgrammingExerciseTestCaseRepository testCaseRepository, ProgrammingExerciseRepository programmingExerciseRepository,
            ProgrammingTriggerService programmingTriggerService, AuditEventRepository auditEventRepository, ProgrammingExerciseTaskService programmingExerciseTaskService) {
        this.testCaseRepository = testCaseRepository;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.programmingTriggerService = programmingTriggerService;
        this.auditEventRepository = auditEventRepository;
        this.programmingExerciseTaskService = programmingExerciseTaskService;
    }

    /**
     * Update the updatable attributes of the provided test case dtos. Returns an entry in the set for each test case that could be updated.
     *
     * @param exerciseId                              of exercise the test cases belong to.
     * @param testCaseProgrammingExerciseTestCaseDTOS of the test cases to update the weights and visibility of.
     * @return the updated test cases.
     * @throws EntityNotFoundException if the programming exercise could not be found.
     */
    public Set<ProgrammingExerciseTestCase> update(Long exerciseId, Set<ProgrammingExerciseTestCaseDTO> testCaseProgrammingExerciseTestCaseDTOS) throws EntityNotFoundException {
        ProgrammingExercise programmingExercise = programmingExerciseRepository.findWithTestCasesById(exerciseId)
                .orElseThrow(() -> new EntityNotFoundException("Programming Exercise", exerciseId));

        Set<ProgrammingExerciseTestCase> existingTestCases = programmingExercise.getTestCases();
        Set<ProgrammingExerciseTestCase> updatedTests = new HashSet<>();
        for (ProgrammingExerciseTestCaseDTO programmingExerciseTestCaseDTO : testCaseProgrammingExerciseTestCaseDTOS) {
            Optional<ProgrammingExerciseTestCase> matchingTestCaseOpt = existingTestCases.stream()
                    .filter(testCase -> testCase.getId().equals(programmingExerciseTestCaseDTO.getId())).findFirst();
            if (matchingTestCaseOpt.isEmpty()) {
                continue;
            }

            ProgrammingExerciseTestCase matchingTestCase = matchingTestCaseOpt.get();
            matchingTestCase.setWeight(programmingExerciseTestCaseDTO.getWeight());
            matchingTestCase.setVisibility(programmingExerciseTestCaseDTO.getVisibility());
            matchingTestCase.setBonusMultiplier(programmingExerciseTestCaseDTO.getBonusMultiplier());
            matchingTestCase.setBonusPoints(programmingExerciseTestCaseDTO.getBonusPoints());

            validateTestCase(matchingTestCase);
            updatedTests.add(matchingTestCase);
        }

        if (!isTestCaseWeightSumValid(existingTestCases)) {
            throw new BadRequestAlertException("The sum of all test case weights is 0 or below.", "TestCaseGrading", "weightSumError", true);
        }

        testCaseRepository.saveAll(updatedTests);
        programmingExerciseTaskService.updateTasksFromProblemStatement(programmingExercise);
        // At least one test was updated with a new weight or runAfterDueDate flag. We use this flag to inform the instructor about outdated student results.
        programmingTriggerService.setTestCasesChangedAndTriggerTestCaseUpdate(exerciseId);
        return updatedTests;
    }

    /**
     * Checks if the sum of test case weights is valid.
     *
     * @param testCases of the exercise.
     * @return true, if the sum of weights is not negative.
     */
    public static boolean isTestCaseWeightSumValid(Set<ProgrammingExerciseTestCase> testCases) {
        if (testCases.isEmpty()) {
            return true;
        }
        double testWeightsSum = testCases.stream().mapToDouble(ProgrammingExerciseTestCase::getWeight).filter(Objects::nonNull).sum();
        return testWeightsSum >= 0;
    }

    private static void validateTestCase(ProgrammingExerciseTestCase testCase) {
        if (testCase.getWeight() == null || testCase.getBonusMultiplier() == null || testCase.getBonusPoints() == null || testCase.getVisibility() == null) {
            throw new BadRequestAlertException(ErrorConstants.PARAMETERIZED_TYPE, "Test case " + testCase.getTestName() + " must not have settings that are null.",
                    "TestCaseGrading", "settingNull", Map.of("testCase", testCase.getTestName(), "skipAlert", true));
        }
        if (testCase.getWeight() < 0 || testCase.getBonusMultiplier() < 0 || testCase.getBonusPoints() < 0) {
            throw new BadRequestAlertException(ErrorConstants.PARAMETERIZED_TYPE, "Test case " + testCase.getTestName() + " must not have settings set to negative values.",
                    "TestCaseGrading", "settingNegative", Map.of("testCase", testCase.getTestName(), "skipAlert", true));
        }
    }

    /**
     * Reset all tests to their initial configuration
     *
     * @param programmingExercise that shall be reset
     * @return test cases that have been reset
     */
    public List<ProgrammingExerciseTestCase> reset(ProgrammingExercise programmingExercise) {
        Set<ProgrammingExerciseTestCase> testCases = this.testCaseRepository.findByExerciseId(programmingExercise.getId());
        for (ProgrammingExerciseTestCase testCase : testCases) {
            testCase.setWeight(1.0);
            testCase.setBonusMultiplier(1.0);
            testCase.setBonusPoints(0.0);
            testCase.setVisibility(programmingExercise.getDefaultTestCaseVisibility());
        }
        List<ProgrammingExerciseTestCase> updatedTestCases = testCaseRepository.saveAll(testCases);

        // The tests' weights were updated. We use this flag to inform the instructor about outdated student results.
        programmingTriggerService.setTestCasesChangedAndTriggerTestCaseUpdate(programmingExercise.getId());
        return updatedTestCases;
    }

    public void logTestCaseReset(User user, ProgrammingExercise exercise, Course course) {
        var auditEvent = new AuditEvent(user.getLogin(), Constants.RESET_GRADING, "exercise=" + exercise.getTitle(), "course=" + course.getTitle());
        auditEventRepository.add(auditEvent);
        log.info("User {} requested to reset the grading configuration for exercise {} with id {}", user.getLogin(), exercise.getTitle(), exercise.getId());
    }
}
