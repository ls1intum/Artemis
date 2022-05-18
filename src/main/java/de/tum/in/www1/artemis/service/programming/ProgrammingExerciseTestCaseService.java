package de.tum.in.www1.artemis.service.programming;

import java.util.*;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.audit.AuditEvent;
import org.springframework.boot.actuate.audit.AuditEventRepository;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.domain.enumeration.Visibility;
import de.tum.in.www1.artemis.domain.hestia.ProgrammingExerciseTestCaseType;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseTestCaseRepository;
import de.tum.in.www1.artemis.service.hestia.ProgrammingExerciseTaskService;
import de.tum.in.www1.artemis.web.rest.dto.ProgrammingExerciseTestCaseDTO;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;
import de.tum.in.www1.artemis.web.rest.errors.ErrorConstants;

@Service
public class ProgrammingExerciseTestCaseService {

    private final Logger log = LoggerFactory.getLogger(ProgrammingExerciseTestCaseService.class);

    private final ProgrammingExerciseTestCaseRepository testCaseRepository;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final ProgrammingSubmissionService programmingSubmissionService;

    private final AuditEventRepository auditEventRepository;

    /**
     * Regex for structural test case names in Java. The names of classes, attributes, methods and constructors have not
     * to be checked since the oracle would not create structural tests for invalid names.
     */
    private static final String METHOD_TEST_REGEX = "testMethods\\[.+]";

    private static final String ATTRIBUTES_TEST_REGEX = "testAttributes\\[.+]";

    private static final String CONSTRUCTORS_TEST_REGEX = "testConstructors\\[.+]";

    private static final String CLASS_TEST_REGEX = "testClass\\[.+]";

    private final ProgrammingExerciseTaskService programmingExerciseTaskService;

    public ProgrammingExerciseTestCaseService(ProgrammingExerciseTestCaseRepository testCaseRepository, ProgrammingExerciseRepository programmingExerciseRepository,
            ProgrammingSubmissionService programmingSubmissionService, AuditEventRepository auditEventRepository, ProgrammingExerciseTaskService programmingExerciseTaskService) {
        this.testCaseRepository = testCaseRepository;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.programmingSubmissionService = programmingSubmissionService;
        this.auditEventRepository = auditEventRepository;
        this.programmingExerciseTaskService = programmingExerciseTaskService;
    }

    /**
     * Returns all test cases for a programming exercise.
     *
     * @param exerciseId of a programming exercise.
     * @return test cases of a programming exercise.
     */
    public Set<ProgrammingExerciseTestCase> findByExerciseId(Long exerciseId) {
        return this.testCaseRepository.findByExerciseId(exerciseId);
    }

    /**
     * Returns all active test cases for a programming exercise. Only active test cases are evaluated on build runs.
     *
     * @param exerciseId of a programming exercise.
     * @return active test cases of a programming exercise.
     */
    public Set<ProgrammingExerciseTestCase> findActiveByExerciseId(Long exerciseId) {
        return this.testCaseRepository.findByExerciseIdAndActive(exerciseId, true);
    }

    /**
     * Update the updatable attributes of the provided test case dtos. Returns an entry in the set for each test case that could be updated.
     *
     * @param exerciseId            of exercise the test cases belong to.
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

        if (!isTestCaseWeightSumValid(programmingExercise, existingTestCases)) {
            throw new BadRequestAlertException("The sum of all test case weights is 0 or below.", "TestCaseGrading", "weightSumError", true);
        }

        testCaseRepository.saveAll(updatedTests);
        programmingExerciseTaskService.updateTasksFromProblemStatement(programmingExercise);
        // At least one test was updated with a new weight or runAfterDueDate flag. We use this flag to inform the instructor about outdated student results.
        programmingSubmissionService.setTestCasesChangedAndTriggerTestCaseUpdate(exerciseId);
        return updatedTests;
    }

    /**
     * Checks if the sum of test case weights is valid.
     *
     * The test case weights are valid if at least one test has a weight >0 for purely automatic feedback so that students can still achieve 100% score.
     * If manual feedback is given, then a test case weight of zero is okay, as students can still receive points via manual feedbacks.
     * @param exercise the test cases belong to.
     * @param testCases of the exercise.
     * @return true, if the sum of weights is valid as specified above.
     */
    public static boolean isTestCaseWeightSumValid(ProgrammingExercise exercise, Set<ProgrammingExerciseTestCase> testCases) {
        if (testCases.isEmpty()) {
            return true;
        }
        double testWeightsSum = testCases.stream().mapToDouble(ProgrammingExerciseTestCase::getWeight).filter(Objects::nonNull).sum();
        if (exercise.getAssessmentType() == AssessmentType.AUTOMATIC) {
            return testWeightsSum > 0;
        }
        else {
            return testWeightsSum >= 0;
        }
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
     * Reset the weights of all test cases to 1.
     *
     * @param exerciseId to find exercise test cases
     * @return test cases that have been reset
     */
    public List<ProgrammingExerciseTestCase> reset(Long exerciseId) {
        Set<ProgrammingExerciseTestCase> testCases = this.testCaseRepository.findByExerciseId(exerciseId);
        for (ProgrammingExerciseTestCase testCase : testCases) {
            testCase.setWeight(1.0);
            testCase.setBonusMultiplier(1.0);
            testCase.setBonusPoints(0.0);
        }
        List<ProgrammingExerciseTestCase> updatedTestCases = testCaseRepository.saveAll(testCases);
        // The tests' weights were updated. We use this flag to inform the instructor about outdated student results.
        programmingSubmissionService.setTestCasesChangedAndTriggerTestCaseUpdate(exerciseId);
        return updatedTestCases;
    }

    /**
     * From a list of build run feedback, extract all test cases. If an already stored test case is not found anymore in the build result, it will not be deleted, but set inactive.
     * This way old test cases are not lost, some interfaces in the client might need this information to e.g. show warnings.
     *
     * @param feedbacks list of build log output.
     * @param exercise  programming exercise.
     * @return Returns true if the test cases have changed, false if they haven't.
     */
    public boolean generateTestCasesFromFeedbacks(List<Feedback> feedbacks, ProgrammingExercise exercise) {
        Set<ProgrammingExerciseTestCase> existingTestCases = testCaseRepository.findByExerciseId(exercise.getId());
        // Do not generate test cases for static code analysis feedback
        Set<ProgrammingExerciseTestCase> testCasesFromFeedbacks = getTestCasesFromFeedbacks(feedbacks, exercise);
        // Get test cases that are not already in database - those will be added as new entries.
        Set<ProgrammingExerciseTestCase> newTestCases = testCasesFromFeedbacks.stream().filter(testCase -> existingTestCases.stream().noneMatch(testCase::isSameTestCase))
                .collect(Collectors.toSet());
        // Get test cases which activate state flag changed.
        Set<ProgrammingExerciseTestCase> testCasesWithUpdatedActivation = getTestCasesWithUpdatedActivation(existingTestCases, testCasesFromFeedbacks);

        Set<ProgrammingExerciseTestCase> testCasesToSave = new HashSet<>();
        testCasesToSave.addAll(newTestCases);
        testCasesToSave.addAll(testCasesWithUpdatedActivation);

        setTestCaseType(testCasesToSave, exercise.getProgrammingLanguage());

        // Ensure no duplicate TestCase is present: TestCases have to have a unique name per exercise.
        // Just using the uniqueness property of the set is not enough, as the equals/hash functions
        // consider more attributes of the TestCase rather than only the testName.
        testCasesToSave.removeIf(candidate -> testCasesToSave.stream().filter(testCase -> testCase.getTestName().equalsIgnoreCase(candidate.getTestName())).count() > 1);

        if (!testCasesToSave.isEmpty()) {
            testCaseRepository.saveAll(testCasesToSave);
            programmingExerciseTaskService.updateTasksFromProblemStatement(exercise);
            return true;
        }
        return false;
    }

    private Set<ProgrammingExerciseTestCase> getTestCasesFromFeedbacks(List<Feedback> feedbacks, ProgrammingExercise exercise) {
        // Filter out sca feedback and create test cases out of the feedbacks
        return feedbacks.stream().filter(feedback -> !feedback.isStaticCodeAnalysisFeedback())
                // we use default values for weight, bonus multiplier and bonus points
                .map(feedback -> new ProgrammingExerciseTestCase().testName(feedback.getText()).weight(1.0).bonusMultiplier(1.0).bonusPoints(0.0).exercise(exercise).active(true)
                        .visibility(Visibility.ALWAYS))
                .collect(Collectors.toSet());
    }

    private Set<ProgrammingExerciseTestCase> getTestCasesWithUpdatedActivation(Set<ProgrammingExerciseTestCase> existingTestCases,
            Set<ProgrammingExerciseTestCase> testCasesFromFeedbacks) {
        // We compare the new generated test cases from feedback with the existing test cases from the database
        return existingTestCases.stream().filter(existing -> {
            Optional<ProgrammingExerciseTestCase> matchingTestCase = testCasesFromFeedbacks.stream().filter(existing::isSameTestCase).findFirst();
            // Either the test case was active and is not part of the feedback anymore OR was not active before and is now part of the feedback again.
            return matchingTestCase.isEmpty() && existing.isActive() || matchingTestCase.isPresent() && matchingTestCase.get().isActive() && !existing.isActive();
        }).map(existing -> existing.clone().active(!existing.isActive())).collect(Collectors.toSet());
    }

    public void logTestCaseReset(User user, ProgrammingExercise exercise, Course course) {
        var auditEvent = new AuditEvent(user.getLogin(), Constants.RESET_GRADING, "exercise=" + exercise.getTitle(), "course=" + course.getTitle());
        auditEventRepository.add(auditEvent);
        log.info("User {} requested to reset the grading configuration for exercise {} with id {}", user.getLogin(), exercise.getTitle(), exercise.getId());
    }

    /**
     * Sets the enum value test case type for every test case and saves to the database. Implicitly, all tests are of the same programming language.
     * If the test cases belong to a non-JAVA programming exercise, the type is set to DEFAULT.
     * If the test case belong to a JAVA programming exercise, the type is set to:
     * STRUCTURAL: test case has been generated by the structure oracle, and it's name therefore follow a certain pattern.
     * BEHAVIORAL: all other test cases (that have been written by the instructor).
     *
     * @param testCases the test cases
     * @param programmingLanguage the programming language of the exercise
     */
    public void setTestCaseType(Set<ProgrammingExerciseTestCase> testCases, ProgrammingLanguage programmingLanguage) {
        if (programmingLanguage != ProgrammingLanguage.JAVA) {
            testCases.forEach(testCase -> testCase.setType(ProgrammingExerciseTestCaseType.DEFAULT));
            return;
        }

        // will only be applied for programming exercises in Java
        testCases.forEach(testCase -> {
            String testCaseName = testCase.getTestName();
            // set type depending on the test case name
            if (testCaseName.matches(METHOD_TEST_REGEX) || testCaseName.matches(ATTRIBUTES_TEST_REGEX) || testCaseName.matches(CONSTRUCTORS_TEST_REGEX)
                    || testCaseName.matches(CLASS_TEST_REGEX)) {
                testCase.setType(ProgrammingExerciseTestCaseType.STRUCTURAL);
            }
            else {
                testCase.setType(ProgrammingExerciseTestCaseType.BEHAVIORAL);
            }
        });
    }
}
