package de.tum.cit.aet.artemis.programming.service.ci;

import java.util.List;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Feedback;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.dto.BuildJobInterface;
import de.tum.cit.aet.artemis.programming.dto.BuildResultNotification;
import de.tum.cit.aet.artemis.programming.dto.TestCaseBase;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseBuildConfigRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseTestCaseRepository;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseFeedbackCreationService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseGradingService;

public abstract class AbstractContinuousIntegrationResultService implements ContinuousIntegrationResultService {

    protected final ProgrammingExerciseTestCaseRepository testCaseRepository;

    protected final ProgrammingExerciseFeedbackCreationService feedbackCreationService;

    protected final ProgrammingExerciseBuildConfigRepository programmingExerciseBuildConfigRepository;

    protected AbstractContinuousIntegrationResultService(ProgrammingExerciseTestCaseRepository testCaseRepository,
            ProgrammingExerciseFeedbackCreationService feedbackCreationService, ProgrammingExerciseBuildConfigRepository programmingExerciseBuildConfigRepository) {
        this.testCaseRepository = testCaseRepository;
        this.feedbackCreationService = feedbackCreationService;
        this.programmingExerciseBuildConfigRepository = programmingExerciseBuildConfigRepository;
    }

    @Override
    public Result createResultFromBuildResult(BuildResultNotification buildResult, ProgrammingExerciseParticipation participation) {
        ProgrammingExercise exercise = participation.getProgrammingExercise();

        final var result = new Result();
        result.setAssessmentType(AssessmentType.AUTOMATIC);
        result.setSuccessful(buildResult.isBuildSuccessful());
        result.setCompletionDate(buildResult.buildRunDate());
        // this only sets the score to a temporary value, the real score is calculated in the grading service
        result.setScore(buildResult.buildScore(), exercise.getCourseViaExerciseGroupOrCourseMember());

        addFeedbackToResult(result, buildResult, exercise);
        return result;
    }

    /**
     * Converts build result details into feedback and stores it in the result object
     *
     * @param result              the result for which the feedback should be added
     * @param buildResult         The build result
     * @param programmingExercise the programming exercise related to the result
     *
     */
    private void addFeedbackToResult(Result result, BuildResultNotification buildResult, ProgrammingExercise programmingExercise) {
        final var jobs = buildResult.jobs();

        // 1) add feedback for failed and passed test cases
        addTestCaseFeedbacksToResult(result, jobs, programmingExercise);

        // 2) process static code analysis feedback
        addStaticCodeAnalysisFeedbackToResult(result, buildResult, programmingExercise);
    }

    /**
     * Processes all test results from the build jobs and creates corresponding feedback entries.
     * <p>
     * This method iterates through all build jobs and their test results (both failed and successful),
     * creating feedback entries for each test. It also updates the result's test case counts.
     * <p>
     * For initialization errors (tests named "initializationError"), the display name is qualified
     * with the test class name to uniquely identify which class had the setup failure.
     *
     * @param result              the result object to which feedback will be added
     * @param buildJobs           the list of build jobs containing test results
     * @param programmingExercise the programming exercise for context (used for error message processing)
     */
    private void addTestCaseFeedbacksToResult(Result result, List<? extends BuildJobInterface> buildJobs, ProgrammingExercise programmingExercise) {
        var registeredActiveTestCases = testCaseRepository.findByExerciseIdAndActive(programmingExercise.getId(), true);

        for (BuildJobInterface buildJob : buildJobs) {
            // Process failed tests - these need error messages extracted and formatted
            for (var failedTest : buildJob.failedTests()) {
                String testDisplayName = resolveTestCaseDisplayName(failedTest);
                Feedback failedTestFeedback = feedbackCreationService.createFeedbackFromTestCase(testDisplayName, failedTest.testMessages(), false, programmingExercise,
                        registeredActiveTestCases);
                result.addFeedback(failedTestFeedback);
            }
            result.setTestCaseCount(result.getTestCaseCount() + buildJob.failedTests().size());

            // Process successful tests
            for (var successfulTest : buildJob.successfulTests()) {
                String testDisplayName = resolveTestCaseDisplayName(successfulTest);
                Feedback successfulTestFeedback = feedbackCreationService.createFeedbackFromTestCase(testDisplayName, successfulTest.testMessages(), true, programmingExercise,
                        registeredActiveTestCases);
                result.addFeedback(successfulTestFeedback);
            }
            result.setTestCaseCount(result.getTestCaseCount() + buildJob.successfulTests().size());
            result.setPassedTestCaseCount(result.getPassedTestCaseCount() + buildJob.successfulTests().size());
        }
    }

    /**
     * Resolves the display name for a test case from build results.
     * <p>
     * For regular test cases, this simply returns the test name as-is.
     * <p>
     * For initialization errors (JUnit pseudo-tests named "initializationError" that occur when
     * {@code @BeforeAll} methods fail or class loading fails), this method qualifies the name
     * with the test class to make each error uniquely identifiable. Without this qualification,
     * multiple test classes with initialization failures would all report the same generic
     * "initializationError" name, making it impossible to identify which class failed.
     * <p>
     * Example transformations:
     * <ul>
     * <li>{@code "testAddition"} → {@code "testAddition"} (regular test, unchanged)</li>
     * <li>{@code "initializationError"} with classname {@code "com.example.MathTest"}
     * → {@code "MathTest.initializationError"}</li>
     * </ul>
     *
     * @param testCase the test case from the build result containing name and optional classname
     * @return the resolved test name; for initialization errors this is qualified with the simple class name,
     *         for regular tests this is the original test name
     * @see ProgrammingExerciseGradingService#TESTCASE_INITIALIZATION_ERROR_NAME
     */
    private String resolveTestCaseDisplayName(TestCaseBase testCase) {
        String originalTestName = testCase.name();
        String fullyQualifiedClassName = testCase.classname();

        // Check if this is an initialization error that needs to be qualified with the class name
        boolean isInitializationError = ProgrammingExerciseGradingService.TESTCASE_INITIALIZATION_ERROR_NAME.equals(originalTestName);
        boolean hasValidClassName = fullyQualifiedClassName != null && !fullyQualifiedClassName.isBlank();

        if (isInitializationError && hasValidClassName) {
            // Extract simple class name from fully qualified name (e.g., "com.example.MathTest" -> "MathTest")
            // This improves readability while still uniquely identifying the failing test class
            String simpleClassName = extractSimpleClassName(fullyQualifiedClassName);
            return simpleClassName + "." + originalTestName;
        }

        return originalTestName;
    }

    /**
     * Extracts the simple class name from a fully qualified class name.
     * <p>
     * Examples:
     * <ul>
     * <li>{@code "com.example.MathTest"} → {@code "MathTest"}</li>
     * <li>{@code "MathTest"} → {@code "MathTest"} (already simple)</li>
     * </ul>
     *
     * @param fullyQualifiedClassName the fully qualified class name (may or may not contain package)
     * @return the simple class name without package prefix
     */
    private String extractSimpleClassName(String fullyQualifiedClassName) {
        int lastDotIndex = fullyQualifiedClassName.lastIndexOf('.');
        if (lastDotIndex >= 0 && lastDotIndex < fullyQualifiedClassName.length() - 1) {
            return fullyQualifiedClassName.substring(lastDotIndex + 1);
        }
        return fullyQualifiedClassName;
    }

    private void addStaticCodeAnalysisFeedbackToResult(Result result, BuildResultNotification buildResult, ProgrammingExercise programmingExercise) {
        final var staticCodeAnalysisReports = buildResult.staticCodeAnalysisReports();
        if (Boolean.TRUE.equals(programmingExercise.isStaticCodeAnalysisEnabled()) && staticCodeAnalysisReports != null && !staticCodeAnalysisReports.isEmpty()) {
            List<Feedback> scaFeedbackList = feedbackCreationService.createFeedbackFromStaticCodeAnalysisReports(staticCodeAnalysisReports);
            result.addFeedbacks(scaFeedbackList);
            result.setCodeIssueCount(scaFeedbackList.size());
        }
    }
}
