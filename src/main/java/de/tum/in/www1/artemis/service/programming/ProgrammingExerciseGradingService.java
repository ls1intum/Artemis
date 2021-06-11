package de.tum.in.www1.artemis.service.programming;

import static de.tum.in.www1.artemis.config.Constants.TEST_CASES_DUPLICATE_NOTIFICATION;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.validation.constraints.NotNull;

import org.apache.commons.math3.util.Precision;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.audit.AuditEvent;
import org.springframework.boot.actuate.audit.AuditEventRepository;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.CategoryState;
import de.tum.in.www1.artemis.domain.enumeration.FeedbackType;
import de.tum.in.www1.artemis.domain.enumeration.SubmissionType;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.domain.participation.SolutionProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.participation.TemplateProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.exception.ContinuousIntegrationException;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.GroupNotificationService;
import de.tum.in.www1.artemis.service.ResultService;
import de.tum.in.www1.artemis.service.StaticCodeAnalysisService;
import de.tum.in.www1.artemis.service.connectors.ContinuousIntegrationService;
import de.tum.in.www1.artemis.web.rest.dto.ProgrammingExerciseGradingStatisticsDTO;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

@Service
public class ProgrammingExerciseGradingService {

    private final Logger log = LoggerFactory.getLogger(ProgrammingExerciseGradingService.class);

    private final Optional<ContinuousIntegrationService> continuousIntegrationService;

    private final ProgrammingExerciseTestCaseService testCaseService;

    private final ProgrammingSubmissionService programmingSubmissionService;

    private final SimpMessageSendingOperations messagingTemplate;

    private final ResultRepository resultRepository;

    private final StudentParticipationRepository studentParticipationRepository;

    private final StaticCodeAnalysisService staticCodeAnalysisService;

    private final ProgrammingAssessmentService programmingAssessmentService;

    private final ProgrammingSubmissionRepository programmingSubmissionRepository;

    private final TemplateProgrammingExerciseParticipationRepository templateProgrammingExerciseParticipationRepository;

    private final SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository;

    private final AuditEventRepository auditEventRepository;

    private final GroupNotificationService groupNotificationService;

    private final ResultService resultService;

    public ProgrammingExerciseGradingService(ProgrammingExerciseTestCaseService testCaseService, ProgrammingSubmissionService programmingSubmissionService,
            StudentParticipationRepository studentParticipationRepository, ResultRepository resultRepository, Optional<ContinuousIntegrationService> continuousIntegrationService,
            SimpMessageSendingOperations messagingTemplate, StaticCodeAnalysisService staticCodeAnalysisService, ProgrammingAssessmentService programmingAssessmentService,
            TemplateProgrammingExerciseParticipationRepository templateProgrammingExerciseParticipationRepository,
            SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository, ProgrammingSubmissionRepository programmingSubmissionRepository,
            AuditEventRepository auditEventRepository, GroupNotificationService groupNotificationService, ResultService resultService) {
        this.testCaseService = testCaseService;
        this.programmingSubmissionService = programmingSubmissionService;
        this.studentParticipationRepository = studentParticipationRepository;
        this.continuousIntegrationService = continuousIntegrationService;
        this.resultRepository = resultRepository;
        this.messagingTemplate = messagingTemplate;
        this.staticCodeAnalysisService = staticCodeAnalysisService;
        this.programmingAssessmentService = programmingAssessmentService;
        this.templateProgrammingExerciseParticipationRepository = templateProgrammingExerciseParticipationRepository;
        this.solutionProgrammingExerciseParticipationRepository = solutionProgrammingExerciseParticipationRepository;
        this.programmingSubmissionRepository = programmingSubmissionRepository;
        this.auditEventRepository = auditEventRepository;
        this.groupNotificationService = groupNotificationService;
        this.resultService = resultService;
    }

    /**
     * Use the given requestBody to extract the relevant information from it. Fetch and attach the result's feedback items to it. For programming exercises the test cases are
     * extracted from the feedbacks & the result is updated with the information from the test cases.
     *
     * @param participation the participation for which the build was finished
     * @param requestBody   RequestBody containing the build result and its feedback items
     * @return result after compilation
     */
    public Optional<Result> processNewProgrammingExerciseResult(@NotNull ProgrammingExerciseParticipation participation, @NotNull Object requestBody) {
        log.debug("Received new build result (NEW) for participation {}", participation.getId());

        Result newResult;
        try {
            newResult = continuousIntegrationService.get().onBuildCompleted(participation, requestBody);
            // NOTE: the result is not saved yet, but is connected to the submission, the submission is not completely saved yet
        }
        catch (ContinuousIntegrationException ex) {
            log.error("Result for participation " + participation.getId() + " could not be created", ex);
            return Optional.empty();
        }

        if (newResult != null) {
            ProgrammingExercise programmingExercise = participation.getProgrammingExercise();
            boolean isSolutionParticipation = participation instanceof SolutionProgrammingExerciseParticipation;
            boolean isTemplateParticipation = participation instanceof TemplateProgrammingExerciseParticipation;
            // Find out which test cases were executed and calculate the score according to their status and weight.
            // This needs to be done as some test cases might not have been executed.
            // When the result is from a solution participation, extract the feedback items (= test cases) and store them in our database.
            if (isSolutionParticipation) {
                extractTestCasesFromResult(programmingExercise, newResult);
            }
            newResult = calculateScoreForResult(newResult, programmingExercise, !isSolutionParticipation && !isTemplateParticipation);

            // Note: This programming submission might already have multiple results, however they do not contain the assessor or the feedback
            var programmingSubmission = (ProgrammingSubmission) newResult.getSubmission();

            // If the solution participation was updated, also trigger the template participation build.
            if (isSolutionParticipation) {
                // This method will return without triggering the build if the submission is not of type TEST.
                triggerTemplateBuildIfTestCasesChanged(programmingExercise.getId(), programmingSubmission);
            }

            if (!isSolutionParticipation && !isTemplateParticipation && programmingSubmission.getLatestResult() != null && programmingSubmission.getLatestResult().isManual()) {
                // Note: in this case, we do not want to save the newResult, but we only want to update the latest semi-automatic one
                Result updatedLatestSemiAutomaticResult = updateLatestSemiAutomaticResultWithNewAutomaticFeedback(programmingSubmission.getLatestResult().getId(), newResult,
                        programmingExercise);
                // Adding back dropped submission
                updatedLatestSemiAutomaticResult.setSubmission(programmingSubmission);
                programmingSubmissionRepository.save(programmingSubmission);
                return Optional.of(updatedLatestSemiAutomaticResult);
            }

            // Finally save the new result once and make sure the order column between submission and result is maintained
            newResult.setSubmission(null); // workaround to avoid org.hibernate.HibernateException: null index column for collection:
                                           // de.tum.in.www1.artemis.domain.Submission.results
            newResult = resultRepository.save(newResult);
            newResult.setSubmission(programmingSubmission);
            programmingSubmission.addResult(newResult);
            programmingSubmissionRepository.save(programmingSubmission);
        }

        return Optional.ofNullable(newResult);
    }

    /**
     * Updates an existing semi-automatic result with automatic feedback from another result.
     *
     * Note: for the second correction it is important that we do not create additional semi automatic results
     *
     * @param lastSemiAutomaticResultId The latest manual result for the same submission (which must exist in the database)
     * @param newAutomaticResult The new automatic result
     * @param programmingExercise The programming exercise
     * @return The updated semi-automatic result
     */
    private Result updateLatestSemiAutomaticResultWithNewAutomaticFeedback(long lastSemiAutomaticResultId, Result newAutomaticResult, ProgrammingExercise programmingExercise) {
        // Note: refetch the semi automatic result with feedback and assessor
        var latestSemiAutomaticResult = resultRepository.findByIdWithEagerFeedbacksAndAssessor(lastSemiAutomaticResultId).get();
        // this makes it the most recent result, but optionally keeps the draft state of an unfinished manual result
        latestSemiAutomaticResult.setCompletionDate(latestSemiAutomaticResult.getCompletionDate() != null ? newAutomaticResult.getCompletionDate() : null);

        // remove old automatic feedback
        latestSemiAutomaticResult.getFeedbacks().removeIf(feedback -> feedback != null && feedback.getType() == FeedbackType.AUTOMATIC);

        // copy all feedback from the automatic result
        List<Feedback> copiedFeedbacks = newAutomaticResult.getFeedbacks().stream().map(Feedback::copyFeedback).collect(Collectors.toList());
        latestSemiAutomaticResult = resultService.addFeedbackToResult(latestSemiAutomaticResult, copiedFeedbacks, false);

        String resultString = updateManualResultString(newAutomaticResult.getResultString(), latestSemiAutomaticResult, programmingExercise);
        latestSemiAutomaticResult.setResultString(resultString);

        return resultRepository.save(latestSemiAutomaticResult);
    }

    /**
     * Trigger the build of the template repository, if the submission of the provided result is of type TEST.
     * Will use the commitHash of the submission for triggering the template build.
     *
     * If the submission of the provided result is not of type TEST, the method will return without triggering the build.
     *
     * @param programmingExerciseId ProgrammingExercise id that belongs to the result.
     * @param submission            ProgrammingSubmission
     */
    private void triggerTemplateBuildIfTestCasesChanged(long programmingExerciseId, ProgrammingSubmission submission) {
        // We only trigger the template build when the test repository was changed.
        // If the submission is from type TEST but already has a result, this build was not triggered by a test repository change
        if (!submission.belongsToTestRepository() || (submission.belongsToTestRepository() && submission.getResults() != null && !submission.getResults().isEmpty())) {
            return;
        }
        try {
            programmingSubmissionService.triggerTemplateBuildAndNotifyUser(programmingExerciseId, submission.getCommitHash(), SubmissionType.TEST);
        }
        catch (EntityNotFoundException ex) {
            // If for some reason the programming exercise does not have a template participation, we can only log and abort.
            log.error(
                    "Could not trigger the build of the template repository for the programming exercise id {} because no template participation could be found for the given exercise",
                    programmingExerciseId);
        }
    }

    /**
     * Generates test cases from the given result's feedbacks & notifies the subscribing users about the test cases if they have changed. Has the side effect of sending a message
     * through the websocket!
     *
     * @param exercise the programming exercise for which the test cases should be extracted from the new result
     * @param result   from which to extract the test cases.
     */
    private void extractTestCasesFromResult(ProgrammingExercise exercise, Result result) {
        boolean haveTestCasesChanged = testCaseService.generateTestCasesFromFeedbacks(result.getFeedbacks(), exercise);
        if (haveTestCasesChanged) {
            // Notify the client about the updated testCases
            Set<ProgrammingExerciseTestCase> testCases = testCaseService.findByExerciseId(exercise.getId());
            messagingTemplate.convertAndSend("/topic/programming-exercise/" + exercise.getId() + "/test-cases", testCases);
        }
    }

    /**
     * Updates an incoming result with the information of the exercises test cases. This update includes:
     * - Checking which test cases were not executed as this is not part of the bamboo build (not all test cases are executed in an exercise with sequential test runs)
     * - Checking the due date and the visibility.
     * - Recalculating the score based based on the successful test cases weight vs the total weight of all test cases.
     *
     * If there are no test cases stored in the database for the given exercise (i.e. we have a legacy exercise) or the weight has not been changed, then the result will not change
     *
     * @param result   to modify with new score, result string & added feedbacks (not executed tests)
     * @param exercise the result belongs to.
     * @param isStudentParticipation boolean flag indicating weather the participation of the result is not a solution/template participation.
     * @return Result with updated feedbacks, score and result string.
     */
    public Result calculateScoreForResult(Result result, ProgrammingExercise exercise, boolean isStudentParticipation) {
        Set<ProgrammingExerciseTestCase> testCases = testCaseService.findActiveByExerciseId(exercise.getId());
        Set<ProgrammingExerciseTestCase> testCasesForCurrentDate = testCases;
        // We don't filter the test cases for the solution/template participation's results as they are used as indicators for the instructor!
        if (isStudentParticipation) {
            testCasesForCurrentDate = filterTestCasesForCurrentDate(exercise, testCases);
        }
        return calculateScoreForResult(testCases, testCasesForCurrentDate, result, exercise);
    }

    /**
     * Updates <b>all</b> latest automatic results of the given exercise with the information of the exercises test cases. This update includes:
     * - Checking which test cases were not executed as this is not part of the bamboo build (not all test cases are executed in an exercise with sequential test runs)
     * - Checking the due date and the visibility.
     * - Recalculating the score based based on the successful test cases weight vs the total weight of all test cases.
     *
     * If there are no test cases stored in the database for the given exercise (i.e. we have a legacy exercise) or the weight has not been changed, then the result will not change
     *
     * @param exercise the exercise whose results should be updated
     * @return the results of the exercise that have been updated
     */
    public List<Result> updateAllResults(ProgrammingExercise exercise) {
        Set<ProgrammingExerciseTestCase> testCases = testCaseService.findActiveByExerciseId(exercise.getId());

        ArrayList<Result> updatedResults = new ArrayList<>();

        templateProgrammingExerciseParticipationRepository.findWithEagerResultsAndFeedbacksAndSubmissionsByProgrammingExerciseId(exercise.getId())
                .flatMap(p -> Optional.ofNullable(p.findLatestLegalResult())).ifPresent(result -> {
                    calculateScoreForResult(testCases, testCases, result, exercise);
                    updatedResults.add(result);
                });
        solutionProgrammingExerciseParticipationRepository.findWithEagerResultsAndFeedbacksAndSubmissionsByProgrammingExerciseId(exercise.getId())
                .flatMap(p -> Optional.ofNullable(p.findLatestLegalResult())).ifPresent(result -> {
                    calculateScoreForResult(testCases, testCases, result, exercise);
                    updatedResults.add(result);
                });

        // filter the test cases for the student results if necessary
        Set<ProgrammingExerciseTestCase> testCasesForCurrentDate = filterTestCasesForCurrentDate(exercise, testCases);
        // We only update the latest automatic results here, later manual assessments are not affected
        List<StudentParticipation> participations = studentParticipationRepository.findByExerciseIdWithLatestAutomaticResultAndFeedbacks(exercise.getId());

        for (StudentParticipation studentParticipation : participations) {
            Result result = studentParticipation.findLatestLegalResult();
            if (result != null) {
                calculateScoreForResult(testCases, testCasesForCurrentDate, result, exercise);
                updatedResults.add(result);
            }
        }

        // Update also manual results
        List<StudentParticipation> participationsWithManualResult = studentParticipationRepository.findByExerciseIdWithManualResultAndFeedbacks(exercise.getId());
        for (StudentParticipation studentParticipation : participationsWithManualResult) {
            Result result = studentParticipation.findLatestLegalResult();
            if (result != null) {
                calculateScoreForResult(testCases, testCasesForCurrentDate, result, exercise);
                updatedResults.add(result);
            }
        }

        return updatedResults;
    }

    public void logReEvaluate(User user, ProgrammingExercise exercise, Course course, List<Result> results) {
        var auditEvent = new AuditEvent(user.getLogin(), Constants.RE_EVALUATE_RESULTS, "exercise=" + exercise.getTitle(), "course=" + course.getTitle(),
                "results=" + results.size());
        auditEventRepository.add(auditEvent);
        log.info("User {} triggered a re-evaluation of {} results for exercise {} with id {}", user.getLogin(), results.size(), exercise.getTitle(), exercise.getId());
    }

    /**
     * Filter all test cases from the score calculation that are never visible or ones with visibility "after due date" if the due date has not yet passed.
     * @param exercise to which the test cases belong to.
     * @param testCases which should be filtered.
     * @return testCases, but the ones based on the described visibility criterion removed.
     */
    private Set<ProgrammingExerciseTestCase> filterTestCasesForCurrentDate(ProgrammingExercise exercise, Set<ProgrammingExerciseTestCase> testCases) {
        boolean isBeforeDueDate = exercise.isBeforeDueDate();
        return testCases.stream().filter(testCase -> !testCase.isInvisible()).filter(testCase -> !(isBeforeDueDate && testCase.isAfterDueDate())).collect(Collectors.toSet());
    }

    /**
     * Calculates the grading for a result and updates the feedbacks
     * @param testCases All test cases for the exercise
     * @param testCasesForCurrentDate Test cases for the exercise for the current date
     * @param result The result to be updated
     * @param exercise The current exercise
     * @return The updated result
     */
    private Result calculateScoreForResult(Set<ProgrammingExerciseTestCase> testCases, Set<ProgrammingExerciseTestCase> testCasesForCurrentDate, @NotNull Result result,
            ProgrammingExercise exercise) {
        // Distinguish between static code analysis feedback, test case feedback and manual feedback
        List<Feedback> testCaseFeedback = new ArrayList<>();
        List<Feedback> staticCodeAnalysisFeedback = new ArrayList<>();
        for (var feedback : result.getFeedbacks()) {
            if (feedback.getType() != FeedbackType.AUTOMATIC) {
                continue;
            }
            if (feedback.isStaticCodeAnalysisFeedback()) {
                staticCodeAnalysisFeedback.add(feedback);
            }
            else {
                testCaseFeedback.add(feedback);
            }
        }

        // Remove feedback that is in an invisible sca category
        staticCodeAnalysisFeedback = staticCodeAnalysisService.categorizeScaFeedback(result, staticCodeAnalysisFeedback, exercise);

        // Case 1: There are tests and test case feedback, find out which tests were not executed or should only count to the score after the due date.
        if (testCasesForCurrentDate.size() > 0 && testCaseFeedback.size() > 0 && result.getFeedbacks().size() > 0) {
            retainAutomaticFeedbacksWithTestCase(result, testCases);

            // Copy the visibility from test case to corresponding feedback
            setVisibilityForFeedbacksWithTestCase(result, testCases);

            Set<ProgrammingExerciseTestCase> successfulTestCases = testCasesForCurrentDate.stream().filter(isSuccessful(result)).collect(Collectors.toSet());

            // Add feedbacks for tests that were not executed ("test was not executed").
            createFeedbackForNotExecutedTests(result, testCasesForCurrentDate);

            // Add feedbacks for all duplicate test cases
            boolean hasDuplicateTestCases = createFeedbackForDuplicateTests(result, exercise);

            // Recalculate the achieved score by including the test cases individual weight.
            // The score is always calculated from ALL (except visibility=never) test cases, regardless of the current date!
            updateScore(result, successfulTestCases, testCases, staticCodeAnalysisFeedback, exercise, hasDuplicateTestCases);

            // Create a new result string that reflects passed, failed & not executed test cases.
            updateResultString(result, successfulTestCases, testCasesForCurrentDate, staticCodeAnalysisFeedback, exercise, hasDuplicateTestCases);
        }
        // Case 2: There are no test cases that are executed before the due date has passed. We need to do this to differentiate this case from a build error.
        else if (testCases.size() > 0 && result.getFeedbacks().size() > 0 && testCaseFeedback.size() > 0) {
            removeAllTestCaseFeedbackAndSetScoreToZero(result, staticCodeAnalysisFeedback);

            // Add feedbacks for all duplicate test cases
            boolean hasDuplicateTestCases = createFeedbackForDuplicateTests(result, exercise);

            // In this case, test cases won't be displayed but static code analysis feedback must be shown in the result string.
            updateResultString(result, Set.of(), Set.of(), staticCodeAnalysisFeedback, exercise, hasDuplicateTestCases);
        }
        // Case 3: If there is no test case feedback, the build has failed or it has previously fallen under case 2. In this case we just return the original result without
        // changing it.
        return result;
    }

    /**
     * Only keeps automatic feedbacks that also are associated with a test case.
     *
     * Does not remove static code analysis feedback.
     *
     * @param result of the build run.
     * @param testCases of the programming exercise.
     */
    private void retainAutomaticFeedbacksWithTestCase(Result result, final Set<ProgrammingExerciseTestCase> testCases) {
        // Remove automatic feedbacks not associated with test cases
        result.getFeedbacks().removeIf(feedback -> feedback.getType() == FeedbackType.AUTOMATIC && !feedback.isStaticCodeAnalysisFeedback()
                && testCases.stream().noneMatch(test -> test.getTestName().equalsIgnoreCase(feedback.getText())));

        // If there are no feedbacks left after filtering those not valid, also setHasFeedback to false.
        if (result.getFeedbacks().stream().noneMatch(feedback -> Boolean.FALSE.equals(feedback.isPositive())
                || feedback.getType() != null && (feedback.getType().equals(FeedbackType.MANUAL) || feedback.getType().equals(FeedbackType.MANUAL_UNREFERENCED)))) {
            result.setHasFeedback(false);
        }
    }

    /**
     * Sets the visibility on all feedbacks associated with a test case with the same name.
     * @param result of the build run.
     * @param allTests of the given programming exercise.
     */
    private void setVisibilityForFeedbacksWithTestCase(Result result, final Set<ProgrammingExerciseTestCase> allTests) {
        for (Feedback feedback : result.getFeedbacks()) {
            allTests.stream().filter(testCase -> testCase.getTestName().equalsIgnoreCase(feedback.getText())).findFirst()
                    .ifPresent(testCase -> feedback.setVisibility(testCase.getVisibility()));
        }
    }

    /**
     * Check which tests were not executed and add a new Feedback for them to the exercise.
     *
     * @param result   of the build run.
     * @param allTests of the given programming exercise.
     */
    private void createFeedbackForNotExecutedTests(Result result, Set<ProgrammingExerciseTestCase> allTests) {
        List<Feedback> feedbacksForNotExecutedTestCases = allTests.stream().filter(wasNotExecuted(result))
                .map(testCase -> new Feedback().type(FeedbackType.AUTOMATIC).text(testCase.getTestName()).detailText("Test was not executed.")).collect(Collectors.toList());
        result.addFeedbacks(feedbacksForNotExecutedTestCases);
    }

    /**
     * Check which feedback entries have the same name and therefore indicate multiple testcases with the same name.
     * These duplicate testcases are added as a feedback element to the result.
     * The instructor and tutors are notified via a group notification.
     *
     * @param result              of the build run.
     * @param programmingExercise the current programming exercise.
     */
    private boolean createFeedbackForDuplicateTests(Result result, ProgrammingExercise programmingExercise) {
        Set<String> uniqueFeedbackNames = new HashSet<>();
        // Find duplicate test cases from feedback which is automatic feedback
        Set<String> duplicateFeedbackNames = result.getFeedbacks().stream()
                .filter(feedback -> !feedback.isStaticCodeAnalysisFeedback() && FeedbackType.AUTOMATIC.equals(feedback.getType())).map(Feedback::getText)
                // Set.add() returns false if the lowerCase element was already in the set, this is how we find all duplicates
                .filter(feedbackName -> !uniqueFeedbackNames.add(feedbackName.toLowerCase())).collect(Collectors.toSet());

        if (!duplicateFeedbackNames.isEmpty()) {
            String duplicateDetailText = "This is a duplicate test case. Please review all your test cases and verify that your test cases have unique names!";
            List<Feedback> feedbacksForDuplicateTestCases = duplicateFeedbackNames.stream()
                    .map(feedbackName -> new Feedback().type(FeedbackType.AUTOMATIC).text(feedbackName + " - Duplicate Test Case!").detailText(duplicateDetailText).positive(false))
                    .collect(Collectors.toList());
            result.addFeedbacks(feedbacksForDuplicateTestCases);
            // Enables to view the result details in case all test cases are positive
            result.setHasFeedback(true);
            String notificationText = TEST_CASES_DUPLICATE_NOTIFICATION + String.join(", ", duplicateFeedbackNames);
            groupNotificationService.notifyEditorAndInstructorGroupAboutDuplicateTestCasesForExercise(programmingExercise, notificationText);
            return true;
        }
        return false;
    }

    /**
     * Update the score given the positive tests score divided by all tests' score.
     * Takes weight, bonus multiplier and absolute bonus points into account.
     * All tests in this case does not include ones with visibility=never.
     *
     * @param result                     of the build run.
     * @param successfulTestCases        test cases with positive feedback.
     * @param allTests                   of a given programming exercise.
     * @param staticCodeAnalysisFeedback of a given programming exercise.
     * @param programmingExercise        the given programming exercise.
     * @param hasDuplicateTestCases      indicates duplicate test cases.
     */
    private void updateScore(Result result, Set<ProgrammingExerciseTestCase> successfulTestCases, Set<ProgrammingExerciseTestCase> allTests,
            List<Feedback> staticCodeAnalysisFeedback, ProgrammingExercise programmingExercise, boolean hasDuplicateTestCases) {
        if (hasDuplicateTestCases || successfulTestCases.isEmpty()) {
            result.setScore(0D);
        }
        else {
            double weightSum = allTests.stream().filter(testCase -> !testCase.isInvisible()).mapToDouble(ProgrammingExerciseTestCase::getWeight).sum();
            // Checks if weightSum == 0. We can't use == operator since we are comparing doubles
            if (Precision.equals(weightSum, 0, 1E-8)) {
                result.setScore(0D);
                return;
            }

            // calculate the achieved points from the passed test cases
            double successfulTestPoints = successfulTestCases.stream().mapToDouble(test -> {
                double testWeight = test.getWeight() * test.getBonusMultiplier();
                double testPoints = testWeight / weightSum * programmingExercise.getMaxPoints();
                double testPointsWithBonus = testPoints + test.getBonusPoints();
                // Update credits of related feedback
                // We need to compare testcases via lowercase, because the testcaseRepository is case-insensitive
                result.getFeedbacks().stream().filter(fb -> fb.getType() == FeedbackType.AUTOMATIC && fb.getText().equalsIgnoreCase(test.getTestName())).findFirst()
                        .ifPresent(feedback -> feedback.setCredits(testPointsWithBonus));
                return testPointsWithBonus;
            }).sum();

            /*
             * The points are capped by the maximum achievable points. The cap is applied before the static code analysis penalty is subtracted as otherwise the penalty won't have
             * any effect in some cases. For example with maxPoints=20, successfulTestPoints=30 and penalty=10, a student would still receive the full 20 points, if the points are
             * not capped before the penalty is subtracted. With the implemented order in place successfulTestPoints will be capped to 20 points first, then the penalty is
             * subtracted resulting in 10 points.
             */
            double maxPoints = programmingExercise.getMaxPoints() + Optional.ofNullable(programmingExercise.getBonusPoints()).orElse(0.0);

            if (successfulTestPoints > maxPoints) {
                successfulTestPoints = maxPoints;
            }
            if (Double.isNaN(successfulTestPoints)) {
                successfulTestPoints = 0.0;
            }

            // if static code analysis is enabled, reduce the points by the calculated penalty
            if (Boolean.TRUE.equals(programmingExercise.isStaticCodeAnalysisEnabled())
                    && Optional.ofNullable(programmingExercise.getMaxStaticCodeAnalysisPenalty()).orElse(1) > 0) {
                successfulTestPoints -= calculateStaticCodeAnalysisPenalty(staticCodeAnalysisFeedback, programmingExercise);

                if (successfulTestPoints < 0) {
                    successfulTestPoints = 0;
                }
            }

            // The score is calculated as a percentage of the maximum points
            double score = successfulTestPoints / programmingExercise.getMaxPoints() * 100.0;
            result.setScore(score);
        }
    }

    /**
     * Calculates the total penalty over all static code analysis issues
     * @param staticCodeAnalysisFeedback The list of static code analysis feedback
     * @param programmingExercise The current exercise
     * @return The sum of all penalties, capped at the maximum allowed penalty
     */
    private double calculateStaticCodeAnalysisPenalty(List<Feedback> staticCodeAnalysisFeedback, ProgrammingExercise programmingExercise) {
        double codeAnalysisPenaltyPoints = 0;

        var feedbackByCategory = staticCodeAnalysisFeedback.stream().collect(Collectors.groupingBy(Feedback::getStaticCodeAnalysisCategory));

        for (var category : staticCodeAnalysisService.findByExerciseId(programmingExercise.getId())) {

            if (!category.getState().equals(CategoryState.GRADED)) {
                continue;
            }

            // get all feedback in this category
            List<Feedback> categoryFeedback = feedbackByCategory.getOrDefault(category.getName(), List.of());

            // calculate the sum of all per-feedback penalties
            double categoryPenaltyPoints = categoryFeedback.size() * category.getPenalty();

            // cap at the maximum allowed penalty for this category
            if (category.getMaxPenalty() != null && categoryPenaltyPoints > category.getMaxPenalty()) {
                categoryPenaltyPoints = category.getMaxPenalty();
            }

            // update credits of feedbacks in category
            if (!categoryFeedback.isEmpty()) {
                double perFeedbackPenalty = categoryPenaltyPoints / categoryFeedback.size();
                categoryFeedback.forEach(feedback -> feedback.setCredits(-perFeedbackPenalty));
            }

            codeAnalysisPenaltyPoints += categoryPenaltyPoints;
        }

        /*
         * Cap at the maximum allowed penalty for this exercise (maxStaticCodeAnalysisPenalty is in percent) The max penalty is applied to the maxScore. If no max penalty was
         * supplied, the value defaults to 100 percent. If for example maxScore is 6, maxBonus is 4 and the penalty is 50 percent, then a student can only loose 3 (0.5 * maxScore)
         * points due to static code analysis issues.
         */
        final var maxExercisePenaltyPoints = (double) Optional.ofNullable(programmingExercise.getMaxStaticCodeAnalysisPenalty()).orElse(100) / 100.0
                * programmingExercise.getMaxPoints();
        if (codeAnalysisPenaltyPoints > maxExercisePenaltyPoints) {
            codeAnalysisPenaltyPoints = maxExercisePenaltyPoints;
        }

        return codeAnalysisPenaltyPoints;
    }

    /**
     * Update the result's result string given the successful tests vs. all tests (x of y passed).
     *
     * @param result                of the build run.
     * @param successfulTestCases   test cases with positive feedback.
     * @param allTests              of the given programming exercise.
     * @param scaFeedback           for the result
     * @param exercise              to which this result and the test cases belong
     * @param hasDuplicateTestCases indicates duplicate test cases
     */
    private void updateResultString(Result result, Set<ProgrammingExerciseTestCase> successfulTestCases, Set<ProgrammingExerciseTestCase> allTests, List<Feedback> scaFeedback,
            ProgrammingExercise exercise, boolean hasDuplicateTestCases) {
        if (hasDuplicateTestCases) {
            result.setResultString("Error: Found duplicated tests!");
        }
        else {
            // Create a new result string that reflects passed, failed & not executed test cases.
            String newResultString = successfulTestCases.size() + " of " + allTests.size() + " passed";

            // Show number of found quality issues if static code analysis is enabled
            if (Boolean.TRUE.equals(exercise.isStaticCodeAnalysisEnabled())) {
                String issueTerm = scaFeedback.size() == 1 ? ", 1 issue" : ", " + scaFeedback.size() + " issues";
                newResultString += issueTerm;
            }
            if (result.isManual()) {
                newResultString = updateManualResultString(newResultString, result, exercise);
            }
            result.setResultString(newResultString);
        }
    }

    /**
     * Update the result string of a manual result with the achieved points.
     * @param resultString The automatic part of the result string
     * @param result The result to add the result string
     * @param exercise The programming exercise
     * @return The updated result string
     */
    private String updateManualResultString(String resultString, Result result, ProgrammingExercise exercise) {
        // Calculate different scores for totalScore calculation and add points and maxScore to result string
        double maxScore = exercise.getMaxPoints();
        double points = resultRepository.calculateTotalPointsForProgrammingExercise(result);
        result.setScore(points, maxScore);
        return resultString + ", " + result.createResultString(points, maxScore);
    }

    /**
     * Remove all test case feedback information from a result and treat it as if it has a score of 0.
     * @param result Result containing all feedback
     * @param staticCodeAnalysisFeedback Static code analysis feedback to keep
     */
    private void removeAllTestCaseFeedbackAndSetScoreToZero(Result result, List<Feedback> staticCodeAnalysisFeedback) {
        result.setFeedbacks(staticCodeAnalysisFeedback);
        result.hasFeedback(staticCodeAnalysisFeedback.size() > 0);
        result.setScore(0D);
    }

    /**
     * Check if the provided test was found in the result's feedbacks with positive = true.
     * @param result of the build run.
     * @return true if there is a positive feedback for a given test.
     */
    private Predicate<ProgrammingExerciseTestCase> isSuccessful(Result result) {
        // We need to compare testcases via lowercase, because the testcaseRepository is case-insensitive
        return testCase -> result.getFeedbacks().stream()
                .anyMatch(feedback -> feedback.getText() != null && feedback.getText().equalsIgnoreCase(testCase.getTestName()) && Boolean.TRUE.equals(feedback.isPositive()));
    }

    /**
     * Check if the provided test was not found in the result's feedbacks.
     * @param result of the build run.
     * @return true if there is no feedback for a given test.
     */
    private Predicate<ProgrammingExerciseTestCase> wasNotExecuted(Result result) {
        // We need to compare testcases via lowercase, because the testcaseRepository is case-insensitive
        return testCase -> result.getFeedbacks().stream()
                .noneMatch(feedback -> feedback.getType() == FeedbackType.AUTOMATIC && feedback.getText().equalsIgnoreCase(testCase.getTestName()));
    }

    /**
     * Calculates the statistics for the grading page.
     * @param exerciseId The current exercise
     * @return The statistics object
     */
    public ProgrammingExerciseGradingStatisticsDTO generateGradingStatistics(Long exerciseId) {
        var statistics = new ProgrammingExerciseGradingStatisticsDTO();

        var results = resultRepository.findLatestAutomaticResultsWithEagerFeedbacksForExercise(exerciseId);

        statistics.setNumParticipations(results.size());

        var testCases = testCaseService.findByExerciseId(exerciseId);
        var categories = staticCodeAnalysisService.findByExerciseId(exerciseId);

        // number of passed and failed tests per test case
        var testCaseStatsMap = new HashMap<String, ProgrammingExerciseGradingStatisticsDTO.TestCaseStats>();

        // number of students per amount of detected issues per category
        var categoryIssuesStudentsMap = new HashMap<String, Map<Integer, Integer>>();

        // init for each test case
        for (var testCase : testCases) {
            testCaseStatsMap.put(testCase.getTestName(), new ProgrammingExerciseGradingStatisticsDTO.TestCaseStats(0, 0));
        }

        // init for each category
        for (var category : categories) {
            categoryIssuesStudentsMap.put(category.getName(), new HashMap<>());
        }

        for (var result : results) {

            // number of detected issues per category for this result
            var categoryIssuesMap = new HashMap<String, Integer>();

            for (var feedback : result.getFeedbacks()) {
                // analyse the feedback and add to the statistics
                addFeedbackToStatistics(feedback, categoryIssuesMap, testCaseStatsMap);
            }

            // merge the student specific issue map with the overall students issue map
            mergeCategoryIssuesMaps(categoryIssuesStudentsMap, categoryIssuesMap);
        }

        statistics.setTestCaseStatsMap(testCaseStatsMap);
        statistics.setCategoryIssuesMap(categoryIssuesStudentsMap);

        return statistics;
    }

    /**
     * Merge the result issues map with the overall issues map
     * @param categoryIssuesStudentsMap The overall issues map for all students
     * @param categoryIssuesMap The issues map for one students
     */
    private void mergeCategoryIssuesMaps(Map<String, Map<Integer, Integer>> categoryIssuesStudentsMap, Map<String, Integer> categoryIssuesMap) {
        for (var entry : categoryIssuesMap.entrySet()) {
            // key: category, value: number of issues

            if (categoryIssuesStudentsMap.containsKey(entry.getKey())) {
                var issuesStudentsMap = categoryIssuesStudentsMap.get(entry.getKey());
                // add 1 to the number of students for the category & issues
                if (issuesStudentsMap.containsKey(entry.getValue())) {
                    issuesStudentsMap.merge(entry.getValue(), 1, Integer::sum);
                }
                else {
                    issuesStudentsMap.put(entry.getValue(), 1);
                }
            }
            else {
                // create a new issues map for this category
                var issuesStudentsMap = new HashMap<Integer, Integer>();
                issuesStudentsMap.put(entry.getValue(), 1);
                categoryIssuesStudentsMap.put(entry.getKey(), issuesStudentsMap);
            }
        }
    }

    /**
     * Analyses the feedback and updates the statistics maps
     * @param feedback The given feedback object
     * @param categoryIssuesMap The issues map for sca statistics
     * @param testCaseStatsMap The map for test case statistics
     */
    private void addFeedbackToStatistics(Feedback feedback, Map<String, Integer> categoryIssuesMap,
            Map<String, ProgrammingExerciseGradingStatisticsDTO.TestCaseStats> testCaseStatsMap) {
        if (feedback.isStaticCodeAnalysisFeedback()) {
            // sca feedback
            var categoryName = feedback.getText().substring(Feedback.STATIC_CODE_ANALYSIS_FEEDBACK_IDENTIFIER.length());
            if ("".equals(categoryName)) {
                return; // this feedback belongs to no category
            }

            // add 1 to the issues for this category
            if (categoryIssuesMap.containsKey(categoryName)) {
                categoryIssuesMap.merge(categoryName, 1, Integer::sum);
            }
            else {
                categoryIssuesMap.put(categoryName, 1);
            }

        }
        else if (feedback.getType().equals(FeedbackType.AUTOMATIC)) {
            // test case feedback
            var testName = feedback.getText();

            // add 1 to the passed or failed amount for this test case
            // dependant on the positive flag of the feedback
            if (testCaseStatsMap.containsKey(testName)) {
                if (Boolean.TRUE.equals(feedback.isPositive())) {
                    testCaseStatsMap.get(testName).increaseNumPassed();
                }
                else {
                    testCaseStatsMap.get(testName).increaseNumFailed();
                }
            }
            else {
                testCaseStatsMap.put(testName, new ProgrammingExerciseGradingStatisticsDTO.TestCaseStats(feedback.isPositive() ? 1 : 0, feedback.isPositive() ? 0 : 1));
            }
        }
    }
}
