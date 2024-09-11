package de.tum.cit.aet.artemis.service.programming;

import static de.tum.cit.aet.artemis.core.config.Constants.FEEDBACK_DETAIL_TEXT_DATABASE_MAX_LENGTH;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.config.StaticCodeAnalysisConfigurer;
import de.tum.cit.aet.artemis.domain.Feedback;
import de.tum.cit.aet.artemis.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.domain.ProgrammingExerciseTestCase;
import de.tum.cit.aet.artemis.domain.Result;
import de.tum.cit.aet.artemis.domain.StaticCodeAnalysisCategory;
import de.tum.cit.aet.artemis.domain.StaticCodeAnalysisDefaultCategory;
import de.tum.cit.aet.artemis.domain.enumeration.CategoryState;
import de.tum.cit.aet.artemis.domain.enumeration.FeedbackType;
import de.tum.cit.aet.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.cit.aet.artemis.domain.enumeration.StaticCodeAnalysisTool;
import de.tum.cit.aet.artemis.domain.enumeration.Visibility;
import de.tum.cit.aet.artemis.domain.hestia.ProgrammingExerciseTestCaseType;
import de.tum.cit.aet.artemis.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.repository.ProgrammingExerciseTestCaseRepository;
import de.tum.cit.aet.artemis.repository.StaticCodeAnalysisCategoryRepository;
import de.tum.cit.aet.artemis.service.WebsocketMessagingService;
import de.tum.cit.aet.artemis.service.dto.AbstractBuildResultNotificationDTO;
import de.tum.cit.aet.artemis.service.dto.StaticCodeAnalysisIssue;
import de.tum.cit.aet.artemis.service.dto.StaticCodeAnalysisReportDTO;
import de.tum.cit.aet.artemis.service.hestia.ProgrammingExerciseTaskService;

/**
 * Service for creating feedback for programming exercises.
 */
@Profile(PROFILE_CORE)
@Service
public class ProgrammingExerciseFeedbackCreationService {

    private static final Logger log = LoggerFactory.getLogger(ProgrammingExerciseFeedbackCreationService.class);

    private static final String DEFAULT_FILEPATH = "notAvailable";

    private static final String PYTHON_EXCEPTION_LINE_PREFIX = "E       ";

    private static final Pattern JVM_RESULT_MESSAGE_MATCHER = prepareJVMResultMessageMatcher(
            List.of("java.lang.AssertionError", "org.opentest4j.AssertionFailedError", "de.tum.in.test.api.util.UnexpectedExceptionError"));

    private static final Predicate<String> IS_NOT_STACK_TRACE_LINE = line -> !line.startsWith("\tat ");

    private static final Predicate<String> IS_PYTHON_EXCEPTION_LINE = line -> line.startsWith(PYTHON_EXCEPTION_LINE_PREFIX);

    private static final List<String> TIMEOUT_EXCEPTIONS = Arrays.asList("org.junit.runners.model.TestTimedOutException", "java.util.concurrent.TimeoutException",
            "org.awaitility.core.ConditionTimeoutException", "Timed?OutException");

    /**
     * Regex for structural test case names in Java. The names of classes, attributes, methods and constructors have not
     * to be checked since the oracle would not create structural tests for invalid names.
     */
    private static final Pattern STRUCTURAL_TEST_PATTERN = Pattern.compile("test(Methods|Attributes|Constructors|Class)\\[.+]");

    private static final ObjectMapper mapper = new ObjectMapper();

    private final ProgrammingExerciseTestCaseRepository testCaseRepository;

    private final WebsocketMessagingService websocketMessagingService;

    private final ProgrammingExerciseTaskService programmingExerciseTaskService;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final StaticCodeAnalysisCategoryRepository staticCodeAnalysisCategoryRepository;

    public ProgrammingExerciseFeedbackCreationService(ProgrammingExerciseTestCaseRepository testCaseRepository, WebsocketMessagingService websocketMessagingService,
            ProgrammingExerciseTaskService programmingExerciseTaskService, ProgrammingExerciseRepository programmingExerciseRepository,
            StaticCodeAnalysisCategoryRepository staticCodeAnalysisCategoryRepository) {
        this.testCaseRepository = testCaseRepository;
        this.websocketMessagingService = websocketMessagingService;
        this.programmingExerciseTaskService = programmingExerciseTaskService;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.staticCodeAnalysisCategoryRepository = staticCodeAnalysisCategoryRepository;
    }

    /**
     * Filters and processes a feedback error message, thereby removing any unwanted strings depending on
     * the programming language, or just reformatting it to only show the most important details.
     *
     * @param programmingLanguage The programming language for which the feedback was generated
     * @param errorMessage        The raw error message in the feedback
     * @return A filtered and better formatted error message
     */
    private String processResultErrorMessage(final ProgrammingLanguage programmingLanguage, final String errorMessage) {
        final String timeoutDetailText = "The test case execution timed out. This indicates issues in your code such as endless loops, issues with recursion or really slow performance. Please carefully review your code to avoid such issues. In case you are absolutely sure that there are no issues like this, please contact your instructor to check the setup of the test.";
        final String exceptionPrefix = "Exception message: ";
        // Overwrite timeout exception messages for Junit4, Junit5 and other
        // Defining two pattern groups, (1) the exception name and (2) the exception text
        Pattern findTimeoutPattern = Pattern.compile("^.*(" + String.join("|", TIMEOUT_EXCEPTIONS) + "):?(.*)");
        Matcher matcher = findTimeoutPattern.matcher(errorMessage);
        if (matcher.find()) {
            String exceptionText = matcher.group(2);
            return timeoutDetailText + "\n" + exceptionPrefix + exceptionText.trim();
        }
        // Defining one pattern group, (1) the exception text
        Pattern findGeneralTimeoutPattern = Pattern.compile("^.*:(.*timed out after.*)", Pattern.CASE_INSENSITIVE);
        matcher = findGeneralTimeoutPattern.matcher(errorMessage);
        if (matcher.find()) {
            // overwrite Ares: TimeoutException
            String generalTimeOutExceptionText = matcher.group(1);
            return timeoutDetailText + "\n" + exceptionPrefix + generalTimeOutExceptionText.trim();
        }

        // Filter out unneeded Exception classnames
        if (programmingLanguage == ProgrammingLanguage.JAVA || programmingLanguage == ProgrammingLanguage.KOTLIN) {
            var messageWithoutStackTrace = errorMessage.lines().takeWhile(IS_NOT_STACK_TRACE_LINE).collect(Collectors.joining("\n")).trim();
            return JVM_RESULT_MESSAGE_MATCHER.matcher(messageWithoutStackTrace).replaceAll("");
        }

        if (programmingLanguage == ProgrammingLanguage.PYTHON) {
            Optional<String> firstExceptionMessage = errorMessage.lines().filter(IS_PYTHON_EXCEPTION_LINE).findFirst();
            if (firstExceptionMessage.isPresent()) {
                return firstExceptionMessage.get().replace(PYTHON_EXCEPTION_LINE_PREFIX, "") + "\n\n" + errorMessage;
            }
        }

        return errorMessage;
    }

    /**
     * Builds the regex used in {@link #processResultErrorMessage(ProgrammingLanguage, String)} on results from JVM languages.
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

    /**
     * Removes CI specific path segments. Uses the assignment directory to decide where to cut the path.
     *
     * @param sourcePath Path to be shortened
     * @return Shortened path if it contains an assignment directory, otherwise the full path
     */
    private String removeCIDirectoriesFromPath(String sourcePath) {
        if (sourcePath == null || sourcePath.isEmpty()) {
            return DEFAULT_FILEPATH;
        }
        int workingDirectoryStart = sourcePath.indexOf(Constants.ASSIGNMENT_DIRECTORY);
        if (workingDirectoryStart == -1) {
            return sourcePath;
        }
        return sourcePath.substring(workingDirectoryStart + Constants.ASSIGNMENT_DIRECTORY.length());
    }

    /**
     * Transforms static code analysis reports to feedback objects.
     * As we reuse the Feedback entity to store static code analysis findings, a mapping to those attributes
     * has to be defined, violating the first normal form.
     * <br>
     * Mapping:
     * - text: STATIC_CODE_ANALYSIS_FEEDBACK_IDENTIFIER
     * - reference: Tool
     * - detailText: Issue object as JSON
     *
     * @param reports Static code analysis reports to be transformed
     * @return Feedback objects representing the static code analysis findings
     */
    public List<Feedback> createFeedbackFromStaticCodeAnalysisReports(List<StaticCodeAnalysisReportDTO> reports) {
        ObjectMapper mapper = new ObjectMapper();
        List<Feedback> feedbackList = new ArrayList<>();
        for (final StaticCodeAnalysisReportDTO report : reports) {
            StaticCodeAnalysisTool tool = report.tool();

            for (final StaticCodeAnalysisIssue issue : report.issues()) {
                String truncatedMessage = truncateSCADetailMessage(issue.message());
                String cleanedPath = removeCIDirectoriesFromPath(issue.filePath());

                // Create a new issue record with the modified message and file path
                StaticCodeAnalysisIssue updatedIssue = new StaticCodeAnalysisIssue(cleanedPath, issue.startLine(), issue.endLine(), issue.startColumn(), issue.endColumn(),
                        issue.rule(), issue.category(), truncatedMessage, issue.priority(), issue.penalty());

                Feedback feedback = new Feedback();
                feedback.setText(Feedback.STATIC_CODE_ANALYSIS_FEEDBACK_IDENTIFIER);
                feedback.setReference(tool.name());
                feedback.setType(FeedbackType.AUTOMATIC);
                feedback.setPositive(false);

                // Store static code analysis in JSON format
                try {
                    feedback.setDetailTextTruncated(mapper.writeValueAsString(updatedIssue));
                }
                catch (JsonProcessingException e) {
                    log.warn("Skipping feedback creation for static code analysis issue due to JSON processing error:", e);
                    continue;  // Skip this feedback if JSON processing fails
                }
                feedbackList.add(feedback);
            }
        }
        return feedbackList;
    }

    private String truncateSCADetailMessage(String message) {
        // Leave some space for the json structure that will be saved in the database
        return StringUtils.truncate(message, FEEDBACK_DETAIL_TEXT_DATABASE_MAX_LENGTH - 500);
    }

    /**
     * Create an automatic feedback object from a test job.
     *
     * @param testName        the test case name.
     * @param testMessages    a list of informational messages generated by the test job
     * @param successful      true if the test case was successful.
     * @param exercise        the connected programming exercise
     * @param activeTestCases all active test cases of the exercise.
     *                            They are passed as a parameter to avoid redundant database calls when calling this method multiple times.
     * @return Feedback object for the test job
     */
    public Feedback createFeedbackFromTestCase(String testName, List<String> testMessages, boolean successful, final ProgrammingExercise exercise,
            Set<ProgrammingExerciseTestCase> activeTestCases) {
        Feedback feedback = new Feedback();
        var testCase = activeTestCases.stream().filter(test -> testName.equals(test.getTestName())).findAny();
        if (testCase.isPresent()) {
            feedback.setTestCase(testCase.get());
        }
        else {
            // This feedback was created by a test which is not known to Artemis (not part of the solution result)
            // Feedback like this does not get displayed to students. (see ProgrammingExerciseGradingService#filterAutomaticFeedbacksWithoutTestCase
            feedback.setText(testName);
        }

        if (!successful) {
            String errorMessageString = testMessages.stream().map(errorString -> processResultErrorMessage(exercise.getProgrammingLanguage(), errorString))
                    .collect(Collectors.joining("\n\n"));
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
     * Generates test cases from the given result's feedbacks & notifies the subscribing users about the test cases if they have changed. Has the side effect of sending a message
     * through the websocket!
     *
     * @param buildResult from which to extract the test cases.
     * @param exercise    the programming exercise for which the test cases should be extracted from the new result
     */
    public void extractTestCasesFromResultAndBroadcastUpdates(AbstractBuildResultNotificationDTO buildResult, ProgrammingExercise exercise) {
        boolean haveTestCasesChanged = generateTestCasesFromBuildResult(buildResult, exercise);
        if (haveTestCasesChanged) {
            // Notify the client about the updated testCases
            Set<ProgrammingExerciseTestCase> testCases = testCaseRepository.findByExerciseId(exercise.getId());
            websocketMessagingService.sendMessage("/topic/programming-exercises/" + exercise.getId() + "/test-cases", testCases);
        }
    }

    /**
     * From a list of build run feedback, extract all test cases. If an already stored test case is not found anymore in the build result, it will not be deleted, but set inactive.
     * This way old test cases are not lost, some interfaces in the client might need this information, e.g., to show warnings.
     * This also allows saving a grading configuration when testcases get temporaily removed, e.g., during an exam.
     *
     * @param buildResult the build result with all the test cases.
     * @param exercise    programming exercise.
     * @return Returns true if the test cases have changed, false if they haven't.
     */
    public boolean generateTestCasesFromBuildResult(AbstractBuildResultNotificationDTO buildResult, ProgrammingExercise exercise) {
        Set<ProgrammingExerciseTestCase> existingTestCases = testCaseRepository.findByExerciseId(exercise.getId());
        // Do not generate test cases for static code analysis feedback
        Set<ProgrammingExerciseTestCase> testCasesFromFeedbacks = getTestCasesFromBuildResult(buildResult, exercise);
        // Get test cases that are not already in database - those will be added as new entries.
        Set<ProgrammingExerciseTestCase> newTestCases = testCasesFromFeedbacks.stream().filter(testCase -> existingTestCases.stream().noneMatch(testCase::isSameTestCase))
                .collect(Collectors.toSet());
        // Get test cases where the activate state has changed (re-added or removed tests).
        Set<ProgrammingExerciseTestCase> testCasesWithUpdatedActivation = getTestCasesWithUpdatedActivation(existingTestCases, testCasesFromFeedbacks);

        Set<ProgrammingExerciseTestCase> testCasesToSave = new HashSet<>();
        testCasesToSave.addAll(newTestCases);
        testCasesToSave.addAll(testCasesWithUpdatedActivation);

        setTestCaseType(testCasesToSave, exercise.getProgrammingLanguage());

        // Ensure no duplicate TestCase is present: TestCases have to have a unique name per exercise.
        // Just using the uniqueness property of the set is not enough, as the equals/hash functions
        // compares test cases by their id.
        testCasesToSave.removeIf(candidate -> testCasesToSave.stream().filter(testCase -> testCase.getTestName().equalsIgnoreCase(candidate.getTestName())).count() > 1);

        if (!testCasesToSave.isEmpty()) {
            testCaseRepository.saveAll(testCasesToSave);
            programmingExerciseTaskService.updateTasksFromProblemStatement(exercise);
            // Replace the test case names by ids in the problem statement.
            // This handles the case if the problem statement already contains the name of a test case
            // that got later pushed into the test repository. Since this test case now exists,
            // the problem statement should now refer to its id.
            programmingExerciseTaskService.replaceTestNamesWithIds(exercise);
            programmingExerciseRepository.save(exercise);
            return true;
        }
        return false;
    }

    private Set<ProgrammingExerciseTestCase> getTestCasesWithUpdatedActivation(Set<ProgrammingExerciseTestCase> existingTestCases,
            Set<ProgrammingExerciseTestCase> testCasesFromFeedbacks) {
        // We compare the new generated test cases from feedback with the existing test cases from the database
        return existingTestCases.stream().filter(existing -> {
            Optional<ProgrammingExerciseTestCase> matchingTestCase = testCasesFromFeedbacks.stream().filter(existing::isSameTestCase).findFirst();
            // Either the test case was active and is not part of the feedback anymore
            boolean existingTestCaseRemoved = matchingTestCase.isEmpty() && existing.isActive();
            // OR was not active before and is now part of the feedback again.
            boolean inactiveTestReactivated = matchingTestCase.isPresent() && !existing.isActive();
            return existingTestCaseRemoved || inactiveTestReactivated;
        }).map(existing -> existing.clone().active(!existing.isActive())).collect(Collectors.toSet());
        // If an existing test gets reactivated, we reuse its grading settings (weight, visibility, etc.).
        // The user should not need to enter these settings again.
    }

    /**
     * Sets the enum value test case type for every test case and saves to the database. Implicitly, all tests are of the same programming language.
     * If the test cases belong to a non-JAVA programming exercise, the type is set to DEFAULT.
     * If the test case belong to a JAVA programming exercise, the type is set to:
     * STRUCTURAL: test case has been generated by the structure oracle, therefore its name follows a certain pattern.
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
            if (STRUCTURAL_TEST_PATTERN.matcher(testCaseName).matches()) {
                testCase.setType(ProgrammingExerciseTestCaseType.STRUCTURAL);
            }
            else {
                testCase.setType(ProgrammingExerciseTestCaseType.BEHAVIORAL);
            }
        });
    }

    private Set<ProgrammingExerciseTestCase> getTestCasesFromBuildResult(AbstractBuildResultNotificationDTO buildResult, ProgrammingExercise exercise) {
        Visibility defaultVisibility = exercise.getDefaultTestCaseVisibility();

        return buildResult.getBuildJobs().stream().flatMap(job -> Stream.concat(job.getFailedTests().stream(), job.getSuccessfulTests().stream()))
                // we use default values for weight, bonus multiplier and bonus points
                .map(testCase -> new ProgrammingExerciseTestCase().testName(testCase.getName()).weight(1.0).bonusMultiplier(1.0).bonusPoints(0.0).exercise(exercise).active(true)
                        .visibility(defaultVisibility))
                .collect(Collectors.toSet());
    }

    /**
     * Sets the category for each feedback and removes feedback with no category or an inactive one.
     * The feedback is removed permanently, which has the advantage that the server or client doesn't have to filter out
     * invisible feedback every time it is requested. The drawback is that the re-evaluate functionality can't take
     * the removed feedback into account.
     *
     * @param result                     of the build run
     * @param staticCodeAnalysisFeedback modifiable list of static code analysis feedback objects that will get filtered
     * @param programmingExercise        The current exercise
     */
    public void categorizeScaFeedback(Result result, List<Feedback> staticCodeAnalysisFeedback, ProgrammingExercise programmingExercise) {
        var categoryPairs = getCategoriesWithMappingForExercise(programmingExercise);

        for (Iterator<Feedback> iterator = staticCodeAnalysisFeedback.iterator(); iterator.hasNext();) {
            var scaFeedback = iterator.next();
            try {
                // Extract the sca issue
                StaticCodeAnalysisIssue issue = mapper.readValue(scaFeedback.getDetailText(), StaticCodeAnalysisIssue.class);
                // Determine the category for this issue
                Optional<StaticCodeAnalysisCategory> category = findCategoryForIssue(issue, scaFeedback, categoryPairs);

                if (category.isPresent() && category.get().getState() == CategoryState.GRADED) {
                    // Create a new issue with updated penalty
                    StaticCodeAnalysisIssue updatedIssue = new StaticCodeAnalysisIssue(issue.filePath(), issue.startLine(), issue.endLine(), issue.startColumn(), issue.endColumn(),
                            issue.rule(), issue.category(), issue.message(), issue.priority(), category.get().getPenalty());
                    // Update detail text with new issue data
                    scaFeedback.setDetailTextTruncated(mapper.writeValueAsString(updatedIssue));
                }
                else if (category.isPresent()) {
                    // Create a new issue with null penalty
                    StaticCodeAnalysisIssue updatedIssue = new StaticCodeAnalysisIssue(issue.filePath(), issue.startLine(), issue.endLine(), issue.startColumn(), issue.endColumn(),
                            issue.rule(), issue.category(), issue.message(), issue.priority(), null);
                    scaFeedback.setDetailTextTruncated(mapper.writeValueAsString(updatedIssue));
                }

                // Determine feedback visibility based on category state
                if (category.isEmpty() || category.get().getState() == CategoryState.INACTIVE) {
                    // Remove feedback
                    result.removeFeedback(scaFeedback);
                    iterator.remove();
                }
                else {
                    scaFeedback.setText(Feedback.STATIC_CODE_ANALYSIS_FEEDBACK_IDENTIFIER + category.get().getName());
                    // Keep feedback
                }
            }
            catch (JsonProcessingException exception) {
                log.debug("Error occurred parsing feedback {} to static code analysis issue: {}", scaFeedback, exception.getMessage());
                // Remove invalid feedback
                result.removeFeedback(scaFeedback);
                iterator.remove();
            }
        }
    }

    private Optional<StaticCodeAnalysisCategory> findCategoryForIssue(StaticCodeAnalysisIssue issue, Feedback scaFeedback,
            Map<StaticCodeAnalysisCategory, List<StaticCodeAnalysisDefaultCategory.CategoryMapping>> categoryPairs) {
        return categoryPairs.entrySet().stream().filter(
                pair -> pair.getValue().stream().anyMatch(mapping -> mapping.tool().name().equals(scaFeedback.getReference()) && mapping.category().equals(issue.category())))
                .map(Map.Entry::getKey).findFirst();
    }

    /**
     * Links the categories of an exercise with the default category mappings.
     *
     * @param programmingExercise The programming exercise
     * @return A list of pairs of categories and their mappings.
     */
    private Map<StaticCodeAnalysisCategory, List<StaticCodeAnalysisDefaultCategory.CategoryMapping>> getCategoriesWithMappingForExercise(ProgrammingExercise programmingExercise) {
        var categories = staticCodeAnalysisCategoryRepository.findByExerciseId(programmingExercise.getId());
        var defaultCategories = StaticCodeAnalysisConfigurer.staticCodeAnalysisConfiguration().get(programmingExercise.getProgrammingLanguage());

        Map<StaticCodeAnalysisCategory, List<StaticCodeAnalysisDefaultCategory.CategoryMapping>> categoryPairsWithMapping = new HashMap<>();

        for (var category : categories) {
            var defaultCategoryMatch = defaultCategories.stream().filter(defaultCategory -> defaultCategory.name().equals(category.getName())).findFirst();
            if (defaultCategoryMatch.isPresent()) {
                var categoryMappings = defaultCategoryMatch.get().categoryMappings();
                categoryPairsWithMapping.put(category, categoryMappings);
            }
        }

        return categoryPairsWithMapping;
    }

}
