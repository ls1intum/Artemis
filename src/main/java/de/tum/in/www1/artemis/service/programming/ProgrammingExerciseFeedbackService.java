package de.tum.in.www1.artemis.service.programming;

import java.util.*;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.Visibility;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseTestCaseRepository;
import de.tum.in.www1.artemis.service.hestia.ProgrammingExerciseTaskService;

@Service
public class ProgrammingExerciseFeedbackService {

    private final ProgrammingExerciseTestCaseRepository testCaseRepository;

    private final ProgrammingExerciseTaskService programmingExerciseTaskService;

    public ProgrammingExerciseFeedbackService(ProgrammingExerciseTestCaseRepository testCaseRepository, ProgrammingExerciseTaskService programmingExerciseTaskService) {
        this.testCaseRepository = testCaseRepository;
        this.programmingExerciseTaskService = programmingExerciseTaskService;
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

        testCaseRepository.setTestCaseType(testCasesToSave, exercise.getProgrammingLanguage());

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
}
