package de.tum.in.www1.artemis.service;

import java.util.*;
import java.util.stream.Collectors;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
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

    // TODO: Workaround for known bug, should be removed once fixed: https://jira.spring.io/browse/DATAJPA-1357
    // The issue is that these methods are called from the build result notification of bamboo, so there is no authentication object available.
    // This doesn't cause problems for out-of-the-box repository methods, but does for custom ones.
    private Authentication getAuthDummy() {
        return new Authentication() {

            @Override
            public Collection<? extends GrantedAuthority> getAuthorities() {
                return null;
            }

            @Override
            public Object getCredentials() {
                return null;
            }

            @Override
            public Object getDetails() {
                return null;
            }

            @Override
            public Object getPrincipal() {
                return null;
            }

            @Override
            public boolean isAuthenticated() {
                return false;
            }

            @Override
            public void setAuthenticated(boolean isAuthenticated) throws IllegalArgumentException {

            }

            @Override
            public String getName() {
                return null;
            }
        };
    }

    /**
     * Returns all test cases for a programming exercise.
     * 
     * @param id of a programming exercise.
     * @return test cases of a programming exercise.
     */
    public Set<ProgrammingExerciseTestCase> findByExerciseId(Long id) {
        SecurityContext context = SecurityContextHolder.getContext();
        context.setAuthentication(getAuthDummy());
        return this.testCaseRepository.findByExerciseId(id);
    }

    /**
     * Returns all active test cases for a programming exercise. Only active test cases are evaluated on build runs.
     * 
     * @param id of a programming exercise.
     * @return active test cases of a programming exercise.
     */
    public Set<ProgrammingExerciseTestCase> findActiveByExerciseId(Long id) {
        SecurityContext context = SecurityContextHolder.getContext();
        context.setAuthentication(getAuthDummy());
        return this.testCaseRepository.findActiveByExerciseId(id);
    }

    /**
     * From a list of build run feedback, extract all test cases. If an already stored test case is not found anymore in the build result, it will not be deleted, but set inactive.
     * This way old test cases are not lost, some interfaces in the client might need this information to e.g. show warnings.
     * 
     * @param feedbacks list of build log output.
     * @param exercise  programming exercise.
     */
    public void generateFromFeedbacks(List<Feedback> feedbacks, ProgrammingExercise exercise) {
        SecurityContext context = SecurityContextHolder.getContext();
        context.setAuthentication(getAuthDummy());

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
        }
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
