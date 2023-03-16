package de.tum.in.www1.artemis.service.programming;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Feedback;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.ProgrammingExerciseTestCase;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.domain.enumeration.Visibility;
import de.tum.in.www1.artemis.domain.hestia.ProgrammingExerciseTestCaseType;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseTestCaseRepository;
import de.tum.in.www1.artemis.service.hestia.ProgrammingExerciseTaskService;

/**
 * Service for extracting the test cases out of the feedback provided by a build run.
 */
@Service
public class ProgrammingExerciseFeedbackService {

    private final ProgrammingExerciseTestCaseRepository testCaseRepository;

    private final ProgrammingExerciseTaskService programmingExerciseTaskService;

    /**
     * Regex for structural test case names in Java. The names of classes, attributes, methods and constructors have not
     * to be checked since the oracle would not create structural tests for invalid names.
     */
    private static final String METHOD_TEST_REGEX = "testMethods\\[.+]";

    private static final String ATTRIBUTES_TEST_REGEX = "testAttributes\\[.+]";

    private static final String CONSTRUCTORS_TEST_REGEX = "testConstructors\\[.+]";

    private static final String CLASS_TEST_REGEX = "testClass\\[.+]";

    public ProgrammingExerciseFeedbackService(ProgrammingExerciseTestCaseRepository testCaseRepository, ProgrammingExerciseTaskService programmingExerciseTaskService) {
        this.testCaseRepository = testCaseRepository;
        this.programmingExerciseTaskService = programmingExerciseTaskService;
    }

    /**
     * From a list of build run feedbacks, extract all test cases. If an already stored test case is not found anymore in the build result, it will not be deleted, but set
     * inactive.
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

    /**
     * Sets the enum value test case type for every test case and saves to the database. Implicitly, all tests are of the same programming language.
     * If the test cases belong to a non-JAVA programming exercise, the type is set to DEFAULT.
     * If the test case belong to a JAVA programming exercise, the type is set to:
     * STRUCTURAL: test case has been generated by the structure oracle, and it's name therefore follow a certain pattern.
     * BEHAVIORAL: all other test cases (that have been written by the instructor).
     *
     * @param testCases           the test cases
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
