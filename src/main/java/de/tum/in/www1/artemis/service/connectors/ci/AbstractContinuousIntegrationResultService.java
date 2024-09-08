package de.tum.in.www1.artemis.service.connectors.ci;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.function.Predicate;

import de.tum.in.www1.artemis.domain.BuildLogEntry;
import de.tum.in.www1.artemis.domain.Feedback;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.ProgrammingSubmission;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.repository.BuildLogStatisticsEntryRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseBuildConfigRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseTestCaseRepository;
import de.tum.in.www1.artemis.service.dto.AbstractBuildResultNotificationDTO;
import de.tum.in.www1.artemis.service.dto.BuildJobDTOInterface;
import de.tum.in.www1.artemis.service.hestia.TestwiseCoverageService;
import de.tum.in.www1.artemis.service.programming.ProgrammingExerciseFeedbackCreationService;

public abstract class AbstractContinuousIntegrationResultService implements ContinuousIntegrationResultService {

    protected final ProgrammingExerciseTestCaseRepository testCaseRepository;

    protected final BuildLogStatisticsEntryRepository buildLogStatisticsEntryRepository;

    protected final TestwiseCoverageService testwiseCoverageService;

    protected final ProgrammingExerciseFeedbackCreationService feedbackCreationService;

    protected final ProgrammingExerciseBuildConfigRepository programmingExerciseBuildConfigRepository;

    protected AbstractContinuousIntegrationResultService(ProgrammingExerciseTestCaseRepository testCaseRepository,
            BuildLogStatisticsEntryRepository buildLogStatisticsEntryRepository, TestwiseCoverageService testwiseCoverageService,
            ProgrammingExerciseFeedbackCreationService feedbackCreationService, ProgrammingExerciseBuildConfigRepository programmingExerciseBuildConfigRepository) {
        this.testCaseRepository = testCaseRepository;
        this.buildLogStatisticsEntryRepository = buildLogStatisticsEntryRepository;
        this.testwiseCoverageService = testwiseCoverageService;
        this.feedbackCreationService = feedbackCreationService;
        this.programmingExerciseBuildConfigRepository = programmingExerciseBuildConfigRepository;
    }

    @Override
    public Result createResultFromBuildResult(AbstractBuildResultNotificationDTO buildResult, ProgrammingSubmission submission) {
        ProgrammingExercise exercise = (ProgrammingExercise) submission.getParticipation().getExercise();

        final var result = new Result();
        result.setAssessmentType(AssessmentType.AUTOMATIC);
        result.setSuccessful(buildResult.isBuildSuccessful());
        result.setCompletionDate(buildResult.getBuildRunDate());
        // this only sets the score to a temporary value, the real score is calculated in the grading service
        result.setScore(buildResult.getBuildScore(), exercise.getCourseViaExerciseGroupOrCourseMember());

        // Note: we only set one side of the relationship because we don't know yet whether the result will actually be saved
        result.setSubmission(submission);

        addFeedbackToResult(result, buildResult);
        return result;
    }

    /**
     * Converts build result details into feedback and stores it in the result object
     *
     * @param result      the result for which the feedback should be added
     * @param buildResult The build result
     */
    private void addFeedbackToResult(Result result, AbstractBuildResultNotificationDTO buildResult) {
        final var jobs = buildResult.getBuildJobs();
        final var programmingExercise = (ProgrammingExercise) result.getSubmission().getParticipation().getExercise();

        // 1) add feedback for failed and passed test cases
        addTestCaseFeedbacksToResult(result, jobs, programmingExercise);

        // 2) process static code analysis feedback
        addStaticCodeAnalysisFeedbackToResult(result, buildResult, programmingExercise);

        // 3) process testwise coverage analysis report
        addTestwiseCoverageReportToResult(result, buildResult, programmingExercise);
    }

    private void addTestCaseFeedbacksToResult(Result result, List<? extends BuildJobDTOInterface> jobs, ProgrammingExercise programmingExercise) {
        var activeTestCases = testCaseRepository.findByExerciseIdAndActive(programmingExercise.getId(), true);
        for (final var job : jobs) {
            for (final var failedTest : job.getFailedTests()) {
                result.addFeedback(
                        feedbackCreationService.createFeedbackFromTestCase(failedTest.getName(), failedTest.getTestMessages(), false, programmingExercise, activeTestCases));
            }
            result.setTestCaseCount(result.getTestCaseCount() + job.getFailedTests().size());

            for (final var successfulTest : job.getSuccessfulTests()) {
                result.addFeedback(
                        feedbackCreationService.createFeedbackFromTestCase(successfulTest.getName(), successfulTest.getTestMessages(), true, programmingExercise, activeTestCases));
            }

            result.setTestCaseCount(result.getTestCaseCount() + job.getSuccessfulTests().size());
            result.setPassedTestCaseCount(result.getPassedTestCaseCount() + job.getSuccessfulTests().size());
        }
    }

    private void addStaticCodeAnalysisFeedbackToResult(Result result, AbstractBuildResultNotificationDTO buildResult, ProgrammingExercise programmingExercise) {
        final var staticCodeAnalysisReports = buildResult.getStaticCodeAnalysisReports();
        if (Boolean.TRUE.equals(programmingExercise.isStaticCodeAnalysisEnabled()) && staticCodeAnalysisReports != null && !staticCodeAnalysisReports.isEmpty()) {
            List<Feedback> scaFeedbackList = feedbackCreationService.createFeedbackFromStaticCodeAnalysisReports(staticCodeAnalysisReports);
            result.addFeedbacks(scaFeedbackList);
            result.setCodeIssueCount(scaFeedbackList.size());
        }
    }

    private void addTestwiseCoverageReportToResult(Result result, AbstractBuildResultNotificationDTO buildResult, ProgrammingExercise programmingExercise) {
        programmingExercise.setBuildConfig(programmingExerciseBuildConfigRepository.getProgrammingExerciseBuildConfigElseThrow(programmingExercise));
        if (Boolean.TRUE.equals(programmingExercise.getBuildConfig().isTestwiseCoverageEnabled())) {
            var report = buildResult.getTestwiseCoverageReports();
            if (report != null) {
                // since the test cases are not saved to the database yet, the test case is null for the entries
                var coverageFileReportsWithoutTestsByTestCaseName = testwiseCoverageService.createTestwiseCoverageFileReportsWithoutTestsByTestCaseName(report);
                result.setCoverageFileReportsByTestCaseName(coverageFileReportsWithoutTestsByTestCaseName);
            }
        }
    }

    /**
     * Find the ZonedDateTime of the first BuildLogEntry that contains the searchString in the log message.
     *
     * @param buildLogEntries the BuildLogEntries that should be searched
     * @param searchString    the text that must be contained in the log message
     * @return the ZonedDateTime of the found BuildLogEntry, or null if none was found
     */
    protected ZonedDateTime getTimestampForLogEntry(List<BuildLogEntry> buildLogEntries, String searchString) {
        return getTimestampForLogEntry(buildLogEntries, searchString, 0);
    }

    /**
     * Find the ZonedDateTime of the nth BuildLogEntry that contains the searchString in the log message.
     * This method does not return the first entry that matches the searchString but skips skipEntries matching BuildLogEntries.
     *
     * @param buildLogEntries the BuildLogEntries that should be searched
     * @param searchString    the text that must be contained in the log message
     * @param skipEntries     the number of matching BuildLogEntries that should be skipped
     * @return the ZonedDateTime of the found BuildLogEntry, or null if none was found
     */
    protected ZonedDateTime getTimestampForLogEntry(List<BuildLogEntry> buildLogEntries, String searchString, int skipEntries) {
        return getTimestampForLogEntry(buildLogEntries, buildLogEntry -> buildLogEntry.getLog().contains(searchString), skipEntries);
    }

    /**
     * Find the ZonedDateTime of the first BuildLogEntry that matches the given predicate.
     *
     * @param buildLogEntries   the BuildLogEntries that should be searched
     * @param matchingPredicate the predicate that must be true for the BuildLogEntry
     * @return the ZonedDateTime of the found BuildLogEntry, or null if none was found
     */
    protected ZonedDateTime getTimestampForLogEntry(List<BuildLogEntry> buildLogEntries, Predicate<BuildLogEntry> matchingPredicate) {
        return getTimestampForLogEntry(buildLogEntries, matchingPredicate, 0);
    }

    /**
     * Find the ZonedDateTime of the nth BuildLogEntry that matches the given predicate.
     * This method does not return the first entry that matches the given predicate but skips skipEntries matching BuildLogEntries.
     *
     * @param buildLogEntries   the BuildLogEntries that should be searched
     * @param matchingPredicate the predicate that must be true for the BuildLogEntry
     * @param skipEntries       the number of matching BuildLogEntries that should be skipped
     * @return the ZonedDateTime of the found BuildLogEntry, or null if none was found
     */
    protected ZonedDateTime getTimestampForLogEntry(List<BuildLogEntry> buildLogEntries, Predicate<BuildLogEntry> matchingPredicate, int skipEntries) {
        return buildLogEntries.stream().filter(matchingPredicate).skip(skipEntries).findFirst().map(BuildLogEntry::getTime).orElse(null);
    }

    /**
     * Count the number of log entries that contain the searchString in the log message.
     *
     * @param buildLogEntries the BuildLogEntries that should be searched
     * @param searchString    the text that must be contained in the log message
     * @return the number of matching log entries
     */
    protected Integer countMatchingLogs(List<BuildLogEntry> buildLogEntries, String searchString) {
        return Math.toIntExact(buildLogEntries.stream().filter(buildLogEntry -> buildLogEntry.getLog().contains(searchString)).count());
    }
}
