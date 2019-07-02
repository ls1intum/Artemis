package de.tum.in.www1.artemis.service;

import java.util.*;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Feedback;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.ProgrammingExerciseTestCase;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.enumeration.FeedbackType;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseTestCaseRepository;

@Service
public class ProgrammingExerciseTestCaseService {

    private final ProgrammingExerciseTestCaseRepository testCaseRepository;

    public ProgrammingExerciseTestCaseService(ProgrammingExerciseTestCaseRepository testCaseRepository) {
        this.testCaseRepository = testCaseRepository;
    }

    /**
     * Returns all test cases for a programming exercise.
     * 
     * @param id of a programming exercise.
     * @return test cases of a programming exercise.
     */
    public Set<ProgrammingExerciseTestCase> findByExerciseId(Long id) {
        return this.testCaseRepository.findByExerciseId(id);
    }

    /**
     * Returns all active test cases for a programming exercise. Only active test cases are evaluated on build runs.
     * 
     * @param id of a programming exercise.
     * @return active test cases of a programming exercise.
     */
    public Set<ProgrammingExerciseTestCase> findActiveByExerciseId(Long id) {
        return this.testCaseRepository.findByExerciseIdAndActive(id, true);
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
        Set<ProgrammingExerciseTestCase> testCasesFromFeedbacks = feedbacks.stream()
                .map(feedback -> new ProgrammingExerciseTestCase().testName(feedback.getText()).weight(1).exercise(exercise).active(true)).collect(Collectors.toSet());
        // Get test cases that are not already in database - those will be added as new entries.
        Set<ProgrammingExerciseTestCase> newTestCases = testCasesFromFeedbacks.stream().filter(testCase -> existingTestCases.stream().noneMatch(testCase::equals))
                .collect(Collectors.toSet());
        // Get test cases which activate state flag changed.
        Set<ProgrammingExerciseTestCase> activationStateChanges = existingTestCases.stream().filter(existing -> {
            Optional<ProgrammingExerciseTestCase> matchingText = testCasesFromFeedbacks.stream().filter(existing::equals).findFirst();
            // Either the test case was active and is not part of the feedback anymore OR was not active before and is now part of the feedback again.
            return !matchingText.isPresent() && existing.isActive() || matchingText.isPresent() && matchingText.get().isActive() && !existing.isActive();
        }).map(existing -> existing.clone().active(!existing.isActive())).collect(Collectors.toSet());

        Set<ProgrammingExerciseTestCase> toSave = new HashSet<>();
        toSave.addAll(newTestCases);
        toSave.addAll(activationStateChanges);

        if (toSave.size() > 0) {
            testCaseRepository.saveAll(toSave);
            return true;
        }
        return false;
    }

    public Result updateResultFromTestCases(Result result, ProgrammingExercise exercise) {
        Set<ProgrammingExerciseTestCase> testCases = findActiveByExerciseId(exercise.getId());
        // If there are no feedbacks, the build has failed.
        // If the build has failed, we don't alter the result string, as we will show the build logs in the client.
        if (testCases.size() > 0 && result.getFeedbacks().size() > 0) {
            Set<ProgrammingExerciseTestCase> successfulTestCases = testCases.stream()
                    .filter(testCase -> result.getFeedbacks().stream().anyMatch(feedback -> feedback.getText().equals(testCase.getTestName()) && feedback.isPositive()))
                    .collect(Collectors.toSet());
            Set<ProgrammingExerciseTestCase> notExecutedTestCases = testCases.stream()
                    .filter(testCase -> result.getFeedbacks().stream().noneMatch(feedback -> feedback.getText().equals(testCase.getTestName()))).collect(Collectors.toSet());
            List<Feedback> feedbacksForNotExecutedTestCases = notExecutedTestCases.stream()
                    .map(testCase -> new Feedback().type(FeedbackType.AUTOMATIC).text(testCase.getTestName()).detailText("Test was not executed.")).collect(Collectors.toList());
            result.addFeedbacks(feedbacksForNotExecutedTestCases);

            // Recalculate the achieved score by including the test cases individual weight.
            if (successfulTestCases.size() > 0) {
                long successfulTestScore = successfulTestCases.stream().map(ProgrammingExerciseTestCase::getWeight).mapToLong(w -> w).sum();
                long maxTestScore = testCases.stream().map(ProgrammingExerciseTestCase::getWeight).mapToLong(w -> w).sum();
                long score = maxTestScore > 0 ? (long) ((float) successfulTestScore / maxTestScore * 100.) : 0L;
                result.setScore(score);
            }

            // Create a new result string that reflects passed, failed & not executed test cases.
            result.setResultString(successfulTestCases.size() + " of " + testCases.size() + " passed");
        }
        return result;
    }
}
