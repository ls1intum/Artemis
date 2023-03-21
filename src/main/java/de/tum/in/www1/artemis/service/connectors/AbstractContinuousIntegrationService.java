package de.tum.in.www1.artemis.service.connectors;

import java.net.URL;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.RestTemplate;

import de.tum.in.www1.artemis.domain.BuildLogEntry;
import de.tum.in.www1.artemis.domain.Feedback;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.FeedbackType;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.domain.enumeration.ProjectType;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.repository.BuildLogStatisticsEntryRepository;
import de.tum.in.www1.artemis.repository.FeedbackRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseTestCaseRepository;
import de.tum.in.www1.artemis.repository.ProgrammingSubmissionRepository;
import de.tum.in.www1.artemis.service.BuildLogEntryService;
import de.tum.in.www1.artemis.service.dto.AbstractBuildResultNotificationDTO;
import de.tum.in.www1.artemis.service.dto.BuildJobDTOInterface;
import de.tum.in.www1.artemis.service.hestia.TestwiseCoverageService;

public abstract class AbstractContinuousIntegrationService implements ContinuousIntegrationService {

    private static final Pattern JVM_RESULT_MESSAGE_MATCHER = prepareJVMResultMessageMatcher(
            List.of("java.lang.AssertionError", "org.opentest4j.AssertionFailedError", "de.tum.in.test.api.util.UnexpectedExceptionError"));

    private static final Predicate<String> IS_NOT_STACK_TRACE_LINE = line -> !line.startsWith("\tat ");

    private static final String PYTHON_EXCEPTION_LINE_PREFIX = "E       ";

    private static final Predicate<String> IS_PYTHON_EXCEPTION_LINE = line -> line.startsWith(PYTHON_EXCEPTION_LINE_PREFIX);

    @Value("${artemis.continuous-integration.url}")
    protected URL serverUrl;

    protected final ProgrammingSubmissionRepository programmingSubmissionRepository;

    protected final FeedbackRepository feedbackRepository;

    protected final BuildLogEntryService buildLogService;

    protected final BuildLogStatisticsEntryRepository buildLogStatisticsEntryRepository;

    protected final RestTemplate restTemplate;

    protected final RestTemplate shortTimeoutRestTemplate;

    protected final TestwiseCoverageService testwiseCoverageService;

    protected final ProgrammingExerciseTestCaseRepository testCaseRepository;

    public AbstractContinuousIntegrationService(ProgrammingSubmissionRepository programmingSubmissionRepository, FeedbackRepository feedbackRepository,
            BuildLogEntryService buildLogService, BuildLogStatisticsEntryRepository buildLogStatisticsEntryRepository, RestTemplate restTemplate,
            RestTemplate shortTimeoutRestTemplate, TestwiseCoverageService testwiseCoverageService, ProgrammingExerciseTestCaseRepository testCaseRepository) {
        this.programmingSubmissionRepository = programmingSubmissionRepository;
        this.feedbackRepository = feedbackRepository;
        this.restTemplate = restTemplate;
        this.shortTimeoutRestTemplate = shortTimeoutRestTemplate;
        this.buildLogService = buildLogService;
        this.buildLogStatisticsEntryRepository = buildLogStatisticsEntryRepository;
        this.testwiseCoverageService = testwiseCoverageService;
        this.testCaseRepository = testCaseRepository;
    }

    @Override
    public Result createResultFromBuildResult(AbstractBuildResultNotificationDTO buildResult, ProgrammingExerciseParticipation participation) {
        final var result = new Result();
        result.setAssessmentType(AssessmentType.AUTOMATIC);
        result.setSuccessful(buildResult.isBuildSuccessful());
        result.setCompletionDate(buildResult.getBuildRunDate());
        result.setScore(buildResult.getBuildScore(), participation.getProgrammingExercise().getCourseViaExerciseGroupOrCourseMember());
        result.setParticipation((Participation) participation);
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
        final var programmingExercise = (ProgrammingExercise) result.getParticipation().getExercise();

        // 1) add feedback for failed and passed test cases
        addTestCaseFeedbacksToResult(result, jobs, programmingExercise);

        // 2) process static code analysis feedback
        addStaticCodeAnalysisFeedbackToResult(result, buildResult, programmingExercise);

        // 3) process testwise coverage analysis report
        addTestwiseCoverageReportToResult(result, buildResult, programmingExercise);
    }

    private void addTestCaseFeedbacksToResult(Result result, List<? extends BuildJobDTOInterface> jobs, ProgrammingExercise programmingExercise) {
        for (final var job : jobs) {
            for (final var failedTest : job.getFailedTests()) {
                result.addFeedback(createFeedbackFromTestCase(failedTest.getName(), failedTest.getMessage(), false, programmingExercise));
            }
            result.setTestCaseCount(result.getTestCaseCount() + job.getFailedTests().size());

            for (final var successfulTest : job.getSuccessfulTests()) {
                result.addFeedback(createFeedbackFromTestCase(successfulTest.getName(), successfulTest.getMessage(), true, programmingExercise));
            }

            result.setTestCaseCount(result.getTestCaseCount() + job.getSuccessfulTests().size());
            result.setPassedTestCaseCount(result.getPassedTestCaseCount() + job.getSuccessfulTests().size());
        }
    }

    /**
     * Create an automatic feedback object from a test job.
     *
     * @param testName     the test case name.
     * @param testMessages a list of informational messages generated by the test job
     * @param successful   if the test case was successful.
     * @param exercise     the connected programming exercise
     * @return Feedback object for the test job
     */
    public Feedback createFeedbackFromTestCase(String testName, List<String> testMessages, boolean successful, final ProgrammingExercise exercise) {
        Feedback feedback = new Feedback();
        var testCase = testCaseRepository.findByExerciseIdAndTestName(exercise.getId(), testName);
        testCase.ifPresent(feedback::setTestCase);

        if (!successful) {
            String errorMessageString = testMessages.stream()
                    .map(errorString -> processResultErrorMessage(exercise.getProgrammingLanguage(), exercise.getProjectType(), errorString)).collect(Collectors.joining("\n\n"));
            feedback.setDetailText(errorMessageString);
        }
        else if (!testMessages.isEmpty()) {
            feedback.setDetailText(String.join("\n\n", testMessages));
        }
        else {
            feedback.setDetailText(null);
        }

        feedback.setType(FeedbackType.AUTOMATIC);
        feedback.setPositive(successful);

        return feedback;
    }

    /**
     * Filters and processes a feedback error message, thereby removing any unwanted strings depending on
     * the programming language, or just reformatting it to only show the most important details.
     *
     * @param programmingLanguage The programming language for which the feedback was generated
     * @param projectType         The project type for which the feedback was generated
     * @param message             The raw error message in the feedback
     * @return A filtered and better formatted error message
     */
    private static String processResultErrorMessage(final ProgrammingLanguage programmingLanguage, final ProjectType projectType, final String message) {
        final String timeoutDetailText = "The test case execution timed out. This indicates issues in your code such as endless loops, issues with recursion or really slow performance. Please carefully review your code to avoid such issues. In case you are absolutely sure that there are no issues like this, please contact your instructor to check the setup of the test.";
        final String exceptionPrefix = "Exception message: ";
        // Overwrite timeout exception messages for Junit4, Junit5 and other
        List<String> exceptions = Arrays.asList("org.junit.runners.model.TestTimedOutException", "java.util.concurrent.TimeoutException",
                "org.awaitility.core.ConditionTimeoutException", "Timed?OutException");
        // Defining two pattern groups, (1) the exception name and (2) the exception text
        Pattern findTimeoutPattern = Pattern.compile("^.*(" + String.join("|", exceptions) + "):?(.*)");
        Matcher matcher = findTimeoutPattern.matcher(message);
        if (matcher.find()) {
            String exceptionText = matcher.group(2);
            return timeoutDetailText + "\n" + exceptionPrefix + exceptionText.trim();
        }
        // Defining one pattern group, (1) the exception text
        Pattern findGeneralTimeoutPattern = Pattern.compile("^.*:(.*timed out after.*)", Pattern.CASE_INSENSITIVE);
        matcher = findGeneralTimeoutPattern.matcher(message);
        if (matcher.find()) {
            // overwrite Ares: TimeoutException
            String generalTimeOutExceptionText = matcher.group(1);
            return timeoutDetailText + "\n" + exceptionPrefix + generalTimeOutExceptionText.trim();
        }

        // Filter out unneeded Exception classnames
        if (programmingLanguage == ProgrammingLanguage.JAVA || programmingLanguage == ProgrammingLanguage.KOTLIN) {
            var messageWithoutStackTrace = message.lines().takeWhile(IS_NOT_STACK_TRACE_LINE).collect(Collectors.joining("\n")).trim();

            // the feedback from gradle test result is duplicated therefore it's cut in half
            if (projectType != null && projectType.isGradle()) {
                long numberOfLines = messageWithoutStackTrace.lines().count();
                messageWithoutStackTrace = messageWithoutStackTrace.lines().skip(numberOfLines / 2).collect(Collectors.joining("\n")).trim();
            }
            return JVM_RESULT_MESSAGE_MATCHER.matcher(messageWithoutStackTrace).replaceAll("");
        }

        if (programmingLanguage == ProgrammingLanguage.PYTHON) {
            Optional<String> firstExceptionMessage = message.lines().filter(IS_PYTHON_EXCEPTION_LINE).findFirst();
            if (firstExceptionMessage.isPresent()) {
                return firstExceptionMessage.get().replace(PYTHON_EXCEPTION_LINE_PREFIX, "") + "\n\n" + message;
            }
        }

        return message;
    }

    /**
     * Builds the regex used in {@link #processResultErrorMessage(ProgrammingLanguage, ProjectType, String)} on results from JVM languages.
     *
     * @param jvmExceptionsToFilter Exceptions at the start of lines that should be filtered out in the processing step
     * @return A regex that can be used to process result messages
     */
    private static Pattern prepareJVMResultMessageMatcher(List<String> jvmExceptionsToFilter) {
        // Replace all "." with "\\." and join with regex alternative symbol "|"
        String assertionRegex = jvmExceptionsToFilter.stream().map(s -> s.replaceAll("\\.", "\\\\.")).reduce("", (a, b) -> String.join("|", a, b));
        // Match any of the exceptions at the start of the line and with ": " after it
        String pattern = String.format("^(?:%s): \n*", assertionRegex);

        return Pattern.compile(pattern, Pattern.MULTILINE);
    }

    private void addStaticCodeAnalysisFeedbackToResult(Result result, AbstractBuildResultNotificationDTO buildResult, ProgrammingExercise programmingExercise) {
        final var staticCodeAnalysisReports = buildResult.getStaticCodeAnalysisReports();
        if (Boolean.TRUE.equals(programmingExercise.isStaticCodeAnalysisEnabled()) && staticCodeAnalysisReports != null && !staticCodeAnalysisReports.isEmpty()) {
            var scaFeedbackList = feedbackRepository.createFeedbackFromStaticCodeAnalysisReports(staticCodeAnalysisReports);
            result.addFeedbacks(scaFeedbackList);
            result.setCodeIssueCount(scaFeedbackList.size());
        }
    }

    private void addTestwiseCoverageReportToResult(Result result, AbstractBuildResultNotificationDTO buildResult, ProgrammingExercise programmingExercise) {
        if (Boolean.TRUE.equals(programmingExercise.isTestwiseCoverageEnabled())) {
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
