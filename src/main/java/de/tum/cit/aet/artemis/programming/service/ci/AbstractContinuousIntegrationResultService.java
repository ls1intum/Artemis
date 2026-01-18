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

    private void addTestCaseFeedbacksToResult(Result result, List<? extends BuildJobInterface> jobs, ProgrammingExercise programmingExercise) {
        var activeTestCases = testCaseRepository.findByExerciseIdAndActive(programmingExercise.getId(), true);
        jobs.forEach(job -> {
            job.failedTests().forEach(failedTest -> result.addFeedback(
                    feedbackCreationService.createFeedbackFromTestCase(getTestName(failedTest), failedTest.testMessages(), false, programmingExercise, activeTestCases)));
            result.setTestCaseCount(result.getTestCaseCount() + job.failedTests().size());

            for (final var successfulTest : job.successfulTests()) {
                result.addFeedback(
                        feedbackCreationService.createFeedbackFromTestCase(getTestName(successfulTest), successfulTest.testMessages(), true, programmingExercise, activeTestCases));
            }

            result.setTestCaseCount(result.getTestCaseCount() + job.successfulTests().size());
            result.setPassedTestCaseCount(result.getPassedTestCaseCount() + job.successfulTests().size());
        });
    }

    /**
     * Gets the test name for a test case. For initialization errors (which occur when @BeforeAll or class loading fails),
     * the class name is prepended to make the error unique and help identify which test class failed.
     *
     * @param testCase the test case to get the name for
     * @return the test name, potentially qualified with the class name for initialization errors
     */
    private String getTestName(TestCaseBase testCase) {
        String testName = testCase.name();
        String className = testCase.classname();

        // For initialization errors, prepend the class name to make it unique and informative
        if (ProgrammingExerciseGradingService.TESTCASE_INITIALIZATION_ERROR_NAME.equals(testName) && className != null && !className.isBlank()) {
            // Use simple class name (without package) for readability
            String simpleClassName = className.contains(".") ? className.substring(className.lastIndexOf('.') + 1) : className;
            return simpleClassName + "." + testName;
        }

        return testName;
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
