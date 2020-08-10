package de.tum.in.www1.artemis.service;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.validation.constraints.NotNull;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.FeedbackType;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseTestCaseRepository;
import de.tum.in.www1.artemis.web.rest.dto.ProgrammingExerciseTestCaseDTO;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

@Service
public class ProgrammingExerciseTestCaseService {

    private final ProgrammingExerciseTestCaseRepository testCaseRepository;

    private final ProgrammingExerciseService programmingExerciseService;

    private final ProgrammingSubmissionService programmingSubmissionService;

    private final ParticipationService participationService;

    public ProgrammingExerciseTestCaseService(ProgrammingExerciseTestCaseRepository testCaseRepository, ProgrammingExerciseService programmingExerciseService,
            ProgrammingSubmissionService programmingSubmissionService, ParticipationService participationService) {
        this.testCaseRepository = testCaseRepository;
        this.programmingExerciseService = programmingExerciseService;
        this.programmingSubmissionService = programmingSubmissionService;
        this.participationService = participationService;
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
     * Update the updatable attributes of the provided test case dtos. Returns an entry in the set for each test case that could be updated.
     *
     * @param exerciseId            of exercise the test cases belong to.
     * @param testCaseProgrammingExerciseTestCaseDTOS of the test cases to update the weights and afterDueDate flag of.
     * @return the updated test cases.
     * @throws EntityNotFoundException if the programming exercise could not be found.
     * @throws IllegalAccessException if the retriever does not have the permissions to fetch information related to the programming exercise.
     */
    public Set<ProgrammingExerciseTestCase> update(Long exerciseId, Set<ProgrammingExerciseTestCaseDTO> testCaseProgrammingExerciseTestCaseDTOS)
            throws EntityNotFoundException, IllegalAccessException {
        ProgrammingExercise programmingExercise = programmingExerciseService.findWithTestCasesById(exerciseId);
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
            matchingTestCase.setAfterDueDate(programmingExerciseTestCaseDTO.isAfterDueDate());
            matchingTestCase.setBonusMultiplier(programmingExerciseTestCaseDTO.getBonusMultiplier());
            matchingTestCase.setBonusPoints(programmingExerciseTestCaseDTO.getBonusPoints());
            updatedTests.add(matchingTestCase);
        }
        testCaseRepository.saveAll(updatedTests);
        // At least one test was updated with a new weight or runAfterDueDate flag. We use this flag to inform the instructor about outdated student results.
        programmingSubmissionService.setTestCasesChangedAndTriggerTestCaseUpdate(exerciseId);
        return updatedTests;
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
        Set<ProgrammingExerciseTestCase> testCasesFromFeedbacks = feedbacks.stream().filter(feedback -> !feedback.isStaticCodeAnalysisFeedback())
                .map(feedback -> new ProgrammingExerciseTestCase().testName(feedback.getText()).weight(1.0).bonusMultiplier(1.0).bonusPoints(0.0).exercise(exercise).active(true))
                .collect(Collectors.toSet());
        // Get test cases that are not already in database - those will be added as new entries.
        Set<ProgrammingExerciseTestCase> newTestCases = testCasesFromFeedbacks.stream().filter(testCase -> existingTestCases.stream().noneMatch(testCase::equals))
                .collect(Collectors.toSet());
        // Get test cases which activate state flag changed.
        Set<ProgrammingExerciseTestCase> testCasesWithUpdatedActivation = existingTestCases.stream().filter(existing -> {
            Optional<ProgrammingExerciseTestCase> matchingText = testCasesFromFeedbacks.stream().filter(existing::equals).findFirst();
            // Either the test case was active and is not part of the feedback anymore OR was not active before and is now part of the feedback again.
            return matchingText.isEmpty() && existing.isActive() || matchingText.isPresent() && matchingText.get().isActive() && !existing.isActive();
        }).map(existing -> existing.clone().active(!existing.isActive())).collect(Collectors.toSet());

        Set<ProgrammingExerciseTestCase> testCasesToSave = new HashSet<>();
        testCasesToSave.addAll(newTestCases);
        testCasesToSave.addAll(testCasesWithUpdatedActivation);

        if (testCasesToSave.size() > 0) {
            testCaseRepository.saveAll(testCasesToSave);
            return true;
        }
        return false;
    }

    /**
     * Updates an incoming result with the information of the exercises test cases. This update includes:
     * - Checking which test cases were not executed as this is not part of the bamboo build (not all test cases are executed in an exercise with sequential test runs)
     * - Checking the due date and the afterDueDate flag
     * - Recalculating the score based based on the successful test cases weight vs the total weight of all test cases.
     *
     * If there are no test cases stored in the database for the given exercise (i.e. we have a legacy exercise) or the weight has not been changed, then the result will not change
     *
     * @param result   to modify with new score, result string & added feedbacks (not executed tests)
     * @param exercise the result belongs to.
     * @param isStudentParticipation boolean flag indicating weather the participation of the result is not a solution/template participation.
     * @return Result with updated feedbacks, score and result string.
     */
    public Result updateResultFromTestCases(Result result, ProgrammingExercise exercise, boolean isStudentParticipation) {
        Set<ProgrammingExerciseTestCase> testCases = findActiveByExerciseId(exercise.getId());
        Set<ProgrammingExerciseTestCase> testCasesForCurrentDate = testCases;
        // We don't filter the test cases for the solution/template participation's results as they are used as indicators for the instructor!
        if (isStudentParticipation) {
            testCasesForCurrentDate = filterTestCasesForCurrentDate(exercise, testCases);
        }
        return updateResultFromTestCases(testCases, testCasesForCurrentDate, result, exercise);
    }

    /**
     * Updates <b>all</b> latest automatic results of the given exercise with the information of the exercises test cases. This update includes:
     * - Checking which test cases were not executed as this is not part of the bamboo build (not all test cases are executed in an exercise with sequential test runs)
     * - Checking the due date and the afterDueDate flag
     * - Recalculating the score based based on the successful test cases weight vs the total weight of all test cases.
     *
     * If there are no test cases stored in the database for the given exercise (i.e. we have a legacy exercise) or the weight has not been changed, then the result will not change
     *
     * @param exercise the exercise whose results should be updated
     * @return the results of the exercise that have been updated
     */
    public List<Result> updateAllResultsFromTestCases(ProgrammingExercise exercise) {
        Set<ProgrammingExerciseTestCase> testCases = findActiveByExerciseId(exercise.getId());

        ArrayList<Result> updatedResults = new ArrayList<>();

        Result templateResult = exercise.getTemplateParticipation().findLatestResult();
        Result solutionResult = exercise.getSolutionParticipation().findLatestResult();
        // template and solution are always updated using ALL test cases
        if (templateResult != null) {
            updateResultFromTestCases(testCases, testCases, templateResult, exercise);
            updatedResults.add(templateResult);
        }
        if (solutionResult != null) {
            updateResultFromTestCases(testCases, testCases, solutionResult, exercise);
            updatedResults.add(solutionResult);
        }
        // filter the test cases for the student results if necessary
        Set<ProgrammingExerciseTestCase> testCasesForCurrentDate = filterTestCasesForCurrentDate(exercise, testCases);
        // We only update the latest automatic results here, later manual assessments are not affected
        List<StudentParticipation> participations = participationService.findByExerciseIdWithLatestAutomaticResultAndFeedbacks(exercise.getId());

        for (StudentParticipation studentParticipation : participations) {
            Result result = studentParticipation.findLatestResult();
            if (result != null) {
                updateResultFromTestCases(testCases, testCasesForCurrentDate, result, exercise);
                updatedResults.add(result);
            }
        }
        return updatedResults;
    }

    private Set<ProgrammingExerciseTestCase> filterTestCasesForCurrentDate(ProgrammingExercise exercise, Set<ProgrammingExerciseTestCase> testCases) {
        boolean shouldTestsWithAfterDueDateFlagBeRemoved = exercise.getBuildAndTestStudentSubmissionsAfterDueDate() != null
                && ZonedDateTime.now().isBefore(exercise.getBuildAndTestStudentSubmissionsAfterDueDate());
        // Filter all test cases from the score calculation that are only executed after due date if the due date has not yet passed.
        return testCases.stream().filter(testCase -> !shouldTestsWithAfterDueDateFlagBeRemoved || !testCase.isAfterDueDate()).collect(Collectors.toSet());
    }

    private Result updateResultFromTestCases(Set<ProgrammingExerciseTestCase> testCases, Set<ProgrammingExerciseTestCase> testCasesForCurrentDate, @NotNull Result result,
            ProgrammingExercise exercise) {
        // Filter all test cases from the score calculation that are only executed after due date if the due date has not yet passed.
        // We also don't filter the test cases for the solution/template participation's results as they are used as indicators for the instructor!
        // Distinguish between static code analysis feedback and test case feedback
        // TODO: For now we are only concerned with not breaking existing functionality and not losing static code analysis feedback.
        // This method has to be extended/refactored when a grading concept for static code analysis has been created
        List<Feedback> testCaseFeedback = new ArrayList<>();
        List<Feedback> staticCodeAnalysisFeedback = new ArrayList<>();
        for (var feedback : result.getFeedbacks()) {
            if (feedback.isStaticCodeAnalysisFeedback()) {
                staticCodeAnalysisFeedback.add(feedback);
            }
            else {
                testCaseFeedback.add(feedback);
            }
        }

        // Case 1: There are tests and test case feedback, find out which tests were not executed or should only count to the score after the due date.
        if (testCasesForCurrentDate.size() > 0 && testCaseFeedback.size() > 0 && result.getFeedbacks().size() > 0) {
            // Remove feedbacks that the student should not see yet because of the due date.
            removeFeedbacksForAfterDueDateTests(result, testCasesForCurrentDate);

            Set<ProgrammingExerciseTestCase> successfulTestCases = testCasesForCurrentDate.stream().filter(isSuccessful(result)).collect(Collectors.toSet());

            // Add feedbacks for tests that were not executed ("test was not executed").
            createFeedbackForNotExecutedTests(result, testCasesForCurrentDate);

            // Recalculate the achieved score by including the test cases individual weight.
            // The score is always calculated from ALL test cases, regardless of the current date!
            updateScore(result, successfulTestCases, testCases, exercise);

            // Create a new result string that reflects passed, failed & not executed test cases.
            updateResultString(result, successfulTestCases, testCasesForCurrentDate);
        }
        // Case 2: There are no test cases that are executed before the due date has passed. We need to do this to differentiate this case from a build error.
        else if (testCases.size() > 0 && result.getFeedbacks().size() > 0 && testCaseFeedback.size() > 0) {
            removeAllTestCaseFeedbackAndSetScoreToZero(result, staticCodeAnalysisFeedback);
        }
        // Case 3: If there is no test case feedback, the build has failed or it has previously fallen under case 2. In this case we just return the original result without
        // changing it.
        return result;
    }

    /**
     * Check if the provided test was found in the result's feedbacks with positive = true.
     * @param result of the build run.
     * @return true if there is a positive feedback for a given test.
     */
    private Predicate<ProgrammingExerciseTestCase> isSuccessful(Result result) {
        return testCase -> result.getFeedbacks().stream()
                .anyMatch(feedback -> feedback.getText() != null && feedback.getText().equals(testCase.getTestName()) && feedback.isPositive() == Boolean.TRUE);
    }

    /**
     * Check if the provided test was not found in the result's feedbacks.
     * @param result of the build run.
     * @return true if there is no feedback for a given test.
     */
    private Predicate<ProgrammingExerciseTestCase> wasNotExecuted(Result result) {
        return testCase -> result.getFeedbacks().stream().noneMatch(feedback -> feedback.getText().equals(testCase.getTestName()));
    }

    /**
     * Check which tests were not executed and add a new Feedback for them to the exercise.
     * @param result of the build run.
     * @param allTests of the given programming exercise.
     */
    private void createFeedbackForNotExecutedTests(Result result, Set<ProgrammingExerciseTestCase> allTests) {
        List<Feedback> feedbacksForNotExecutedTestCases = allTests.stream().filter(wasNotExecuted(result))
                .map(testCase -> new Feedback().type(FeedbackType.AUTOMATIC).text(testCase.getTestName()).detailText("Test was not executed.")).collect(Collectors.toList());
        result.addFeedbacks(feedbacksForNotExecutedTestCases);
    }

    /**
     * Check which tests were executed but which result should not be made public to the student yet.
     * @param result of the build run.
     * @param testCasesForCurrentDate of the given programming exercise.
     */
    private void removeFeedbacksForAfterDueDateTests(Result result, Set<ProgrammingExerciseTestCase> testCasesForCurrentDate) {
        // Find feedback which is not associated with test cases for the current date. Does not remove static code analysis feedback
        List<Feedback> feedbacksToFilterForCurrentDate = result.getFeedbacks().stream().filter(
                feedback -> !feedback.isStaticCodeAnalysisFeedback() && testCasesForCurrentDate.stream().noneMatch(testCase -> testCase.getTestName().equals(feedback.getText())))
                .collect(Collectors.toList());
        feedbacksToFilterForCurrentDate.forEach(result::removeFeedback);
        // If there are no feedbacks left after filtering those not valid for the current date, also setHasFeedback to false.
        if (result.getFeedbacks().stream().noneMatch(feedback -> feedback.isPositive() == Boolean.FALSE
                || feedback.getType() != null && (feedback.getType().equals(FeedbackType.MANUAL) || feedback.getType().equals(FeedbackType.MANUAL_UNREFERENCED))))
            result.setHasFeedback(false);
    }

    /**
     * Update the score given the positive tests score divided by all tests's score.
     * Takes weight, bonus multiplier and absolute bonus points into account
     *
     * @param result of the build run.
     * @param successfulTestCases test cases with positive feedback.
     * @param allTests of a given programming exercise.
     */
    private void updateScore(Result result, Set<ProgrammingExerciseTestCase> successfulTestCases, Set<ProgrammingExerciseTestCase> allTests,
            ProgrammingExercise programmingExercise) {
        if (successfulTestCases.size() > 0) {
            double successfulTestScore = successfulTestCases.stream().mapToDouble(test -> test.getWeight() * test.getBonusMultiplier()).sum();
            // the successfulBonusPoints are calculated separately because they should increase the absolute points
            double successfulBonusPoints = successfulTestCases.stream().mapToDouble(ProgrammingExerciseTestCase::getBonusPoints).sum();
            double successfulBonusScore = successfulBonusPoints / programmingExercise.getMaxScore() * 100.0;
            double maxTestScore = allTests.stream().mapToDouble(ProgrammingExerciseTestCase::getWeight).sum();
            long score = maxTestScore > 0 ? (long) (successfulTestScore / maxTestScore * 100.0 + successfulBonusScore) : 0L;
            // The score is capped by the maximum achievable bonus points
            double bonus = programmingExercise.getBonusPoints() != null ? programmingExercise.getBonusPoints() : 0.0;
            double maxScoreWithBonus = ((programmingExercise.getMaxScore() + bonus) / programmingExercise.getMaxScore()) * 100.;
            if (score > maxScoreWithBonus) {
                score = (long) maxScoreWithBonus;
            }
            result.setScore(score);
        }
        else {
            result.setScore(0L);
        }
    }

    /**
     * Update the result's result string given the successful tests vs. all tests (x of y passed).
     * @param result of the build run.
     * @param successfulTestCases test cases with positive feedback.
     * @param allTests of the given programming exercise.
     */
    private void updateResultString(Result result, Set<ProgrammingExerciseTestCase> successfulTestCases, Set<ProgrammingExerciseTestCase> allTests) {
        // Create a new result string that reflects passed, failed & not executed test cases.
        String newResultString = successfulTestCases.size() + " of " + allTests.size() + " passed";
        result.setResultString(newResultString);
    }

    /**
     * Remove all test case feedback information from a result and treat it as if it has a score of 0.
     * @param result Result containing all feedback
     * @param staticCodeAnalysisFeedback Static code analysis feedback to keep
     */
    private void removeAllTestCaseFeedbackAndSetScoreToZero(Result result, List<Feedback> staticCodeAnalysisFeedback) {
        result.setFeedbacks(staticCodeAnalysisFeedback);
        result.hasFeedback(staticCodeAnalysisFeedback.size() > 0);
        result.setScore(0L);
        result.setResultString("0 of 0 passed");
    }
}
