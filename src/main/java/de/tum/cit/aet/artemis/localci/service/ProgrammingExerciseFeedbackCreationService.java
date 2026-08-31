package de.tum.cit.aet.artemis.localci.service;

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
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.assessment.config.FeedbackConfiguration;
import de.tum.cit.aet.artemis.assessment.domain.CategoryState;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.domain.ScaFeedback;
import de.tum.cit.aet.artemis.assessment.domain.TestCaseFeedback;
import de.tum.cit.aet.artemis.assessment.domain.Visibility;
import de.tum.cit.aet.artemis.assessment.service.FeedbackMessageService;
import de.tum.cit.aet.artemis.communication.service.WebsocketMessagingService;
import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.config.StaticCodeAnalysisConfigurer;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseTestCase;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseTestCaseType;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.StaticCodeAnalysisCategory;
import de.tum.cit.aet.artemis.programming.domain.StaticCodeAnalysisDefaultCategory;
import de.tum.cit.aet.artemis.programming.domain.StaticCodeAnalysisTool;
import de.tum.cit.aet.artemis.programming.dto.BuildResultNotification;
import de.tum.cit.aet.artemis.programming.dto.ProgrammingExerciseTestCaseResponseDTO;
import de.tum.cit.aet.artemis.programming.dto.StaticCodeAnalysisIssue;
import de.tum.cit.aet.artemis.programming.dto.StaticCodeAnalysisReportDTO;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseTestCaseRepository;
import de.tum.cit.aet.artemis.programming.repository.StaticCodeAnalysisCategoryRepository;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseTaskService;

/**
 * Service for creating feedback for programming exercises.
 */
@Profile(PROFILE_CORE)
@Lazy
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

    private static final String LONG_MESSAGE_TRUNCATION_MARKER = "\n\n[Feedback truncated: exceeded maximum length]";

    private final ProgrammingExerciseTestCaseRepository testCaseRepository;

    private final WebsocketMessagingService websocketMessagingService;

    private final ProgrammingExerciseTaskService programmingExerciseTaskService;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final StaticCodeAnalysisCategoryRepository staticCodeAnalysisCategoryRepository;

    private final FeedbackMessageService feedbackMessageService;

    public ProgrammingExerciseFeedbackCreationService(ProgrammingExerciseTestCaseRepository testCaseRepository, WebsocketMessagingService websocketMessagingService,
            ProgrammingExerciseTaskService programmingExerciseTaskService, ProgrammingExerciseRepository programmingExerciseRepository,
            StaticCodeAnalysisCategoryRepository staticCodeAnalysisCategoryRepository, FeedbackMessageService feedbackMessageService) {
        this.testCaseRepository = testCaseRepository;
        this.websocketMessagingService = websocketMessagingService;
        this.programmingExerciseTaskService = programmingExerciseTaskService;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.staticCodeAnalysisCategoryRepository = staticCodeAnalysisCategoryRepository;
        this.feedbackMessageService = feedbackMessageService;
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
        String pattern = "^(?:%s): \n*".formatted(assertionRegex);

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
     * Transforms static code analysis reports to structured {@link ScaFeedback} rows. The (heavily
     * duplicated) issue message is stored via the content-addressed {@link FeedbackMessageService}; the
     * tool-reported category is retained separately for the later categorization step
     * ({@link #categorizeScaFeedback(Result, List, ProgrammingExercise)}), which maps it to the Artemis
     * category and sets the graded penalty.
     *
     * @param reports Static code analysis reports to be transformed
     * @return SCA feedback rows representing the static code analysis findings
     */
    public List<ScaFeedback> createFeedbackFromStaticCodeAnalysisReports(List<StaticCodeAnalysisReportDTO> reports) {
        List<ScaFeedback> feedbackList = new ArrayList<>();
        for (final StaticCodeAnalysisReportDTO report : reports) {
            StaticCodeAnalysisTool tool = report.tool();

            for (final StaticCodeAnalysisIssue issue : report.issues()) {
                ScaFeedback scaFeedback = new ScaFeedback();
                scaFeedback.setTool(tool);
                scaFeedback.setToolCategory(StringUtils.truncate(issue.category(), ScaFeedback.MAX_TOOL_CATEGORY_LENGTH));
                scaFeedback.setRule(StringUtils.truncate(issue.rule(), ScaFeedback.MAX_RULE_LENGTH));
                scaFeedback.setFilePath(StringUtils.truncate(removeCIDirectoriesFromPath(issue.filePath()), ScaFeedback.MAX_FILE_PATH_LENGTH));
                scaFeedback.setStartLine(issue.startLine());
                scaFeedback.setEndLine(issue.endLine());
                scaFeedback.setStartColumn(issue.startColumn());
                scaFeedback.setEndColumn(issue.endColumn());
                scaFeedback.setPriority(StringUtils.truncate(issue.priority(), ScaFeedback.MAX_PRIORITY_LENGTH));
                scaFeedback.setMessage(feedbackMessageService.getOrCreate(truncateSCADetailMessage(issue.message())));
                feedbackList.add(scaFeedback);
            }
        }
        return feedbackList;
    }

    private String truncateSCADetailMessage(String message) {
        // Keeps parity with the previous storage limit for SCA messages
        return StringUtils.truncate(message, FEEDBACK_DETAIL_TEXT_DATABASE_MAX_LENGTH - 500);
    }

    /**
     * Create an automatic test-case feedback row from a test job. The (often identical across students
     * and builds) message text is stored via the content-addressed {@link FeedbackMessageService}; credits
     * and visibility are not stored at all — they are derived from the test case at read time.
     *
     * @param testName        the test case name.
     * @param testMessages    a list of informational messages generated by the test job
     * @param successful      true if the test case was successful.
     * @param exercise        the connected programming exercise
     * @param activeTestCases all active test cases of the exercise.
     *                            They are passed as a parameter to avoid redundant database calls when calling this method multiple times.
     * @return the test-case feedback row, or an empty Optional if the test is not known to Artemis (such
     *         feedback was never displayed to students nor persisted — it used to be filtered out in
     *         ProgrammingExerciseGradingService before saving)
     */
    public Optional<TestCaseFeedback> createFeedbackFromTestCase(String testName, List<String> testMessages, boolean successful, final ProgrammingExercise exercise,
            Set<ProgrammingExerciseTestCase> activeTestCases) {
        var testCase = activeTestCases.stream().filter(test -> testName.equals(test.getTestName())).findAny();
        if (testCase.isEmpty()) {
            // This feedback was created by a test which is not known to Artemis (not part of the solution result), e.g. a test invented by a student.
            log.debug("Ignoring feedback of unknown test case {} for exercise {}", testName, exercise.getId());
            return Optional.empty();
        }

        TestCaseFeedback feedback = new TestCaseFeedback();
        feedback.setTestCase(testCase.get());
        feedback.setPositive(successful);

        final String messageText;
        if (!successful) {
            messageText = testMessages.stream().map(errorString -> processResultErrorMessage(exercise.getProgrammingLanguage(), errorString)).collect(Collectors.joining("\n\n"));
        }
        else if (!testMessages.isEmpty()) {
            messageText = String.join("\n\n", testMessages);
        }
        else {
            messageText = null;
        }
        feedback.setMessage(feedbackMessageService.getOrCreate(truncateTestMessage(messageText)));

        return Optional.of(feedback);
    }

    private String truncateTestMessage(String message) {
        if (message == null) {
            return null;
        }
        final int maxFeedbackLength = FeedbackConfiguration.getMaxFeedbackLengthStatic();
        if (message.length() <= maxFeedbackLength) {
            return message;
        }
        // Same protection as the previous long-feedback overflow: excessively long messages are almost
        // always the result of faulty test cases (endless loops, repeated stack traces) and are truncated
        // with an explicit marker.
        final int maxTextLength = maxFeedbackLength - LONG_MESSAGE_TRUNCATION_MARKER.length();
        return message.substring(0, Math.max(0, maxTextLength)) + LONG_MESSAGE_TRUNCATION_MARKER;
    }

    /**
     * Saves the test cases, tolerating a second result for the same exercise that inserted the same ones first.
     * <p>
     * The read above and this write are not atomic, and results are processed on
     * {@code artemis.continuous-integration.concurrent-result-processing-size} threads (16 by default), so two passes
     * over the same exercise can both decide the same test names are new. The loser then violates the unique index on
     * (test_name, exercise_id), and because nothing caught that, the exception unwound the whole result processing -
     * the build's result was lost with "Result could not be processed for build job", not just its test cases.
     * <p>
     * Re-reading and saving only what is genuinely still missing makes the losing pass a no-op instead. Test cases
     * that already carry an id are activation changes rather than inserts; they cannot collide, so they are kept.
     * <p>
     * Deliberately not implemented by deleting and re-inserting: {@code test_case_feedback.test_case_id} is
     * declared {@code ON DELETE CASCADE}, so a delete would take the feedback of every past result for this
     * exercise with it.
     *
     * @param exercise        the exercise the test cases belong to
     * @param testCasesToSave the new test cases and activation changes this pass computed
     */
    private void saveToleratingConcurrentInsert(ProgrammingExercise exercise, Set<ProgrammingExerciseTestCase> testCasesToSave) {
        try {
            testCaseRepository.saveAll(testCasesToSave);
        }
        catch (DataIntegrityViolationException concurrentInsert) {
            Set<ProgrammingExerciseTestCase> persisted = testCaseRepository.findByExerciseId(exercise.getId());
            Set<ProgrammingExerciseTestCase> stillMissing = testCasesToSave.stream()
                    .filter(testCase -> testCase.getId() != null || persisted.stream().noneMatch(testCase::isSameTestCase)).collect(Collectors.toSet());
            if (stillMissing.isEmpty()) {
                log.debug("Test cases for exercise {} were written by a concurrent result; nothing left to save", exercise.getId());
                return;
            }
            // Whatever is left did not collide, so a failure here is not this race and has to reach the caller.
            testCaseRepository.saveAll(stillMissing);
        }
    }

    /**
     * Generates test cases from the given result's feedbacks & notifies the subscribing users about the test cases if they have changed. Has the side effect of sending a message
     * through the websocket!
     *
     * @param buildResult from which to extract the test cases.
     * @param exercise    the programming exercise for which the test cases should be extracted from the new result
     */
    public void extractTestCasesFromResultAndBroadcastUpdates(BuildResultNotification buildResult, ProgrammingExercise exercise) {
        boolean haveTestCasesChanged = generateTestCasesFromBuildResult(buildResult, exercise);
        if (haveTestCasesChanged) {
            // Notify the client about the updated testCases
            Set<ProgrammingExerciseTestCase> testCases = testCaseRepository.findByExerciseId(exercise.getId());
            Set<ProgrammingExerciseTestCaseResponseDTO> testCaseDTOs = testCases.stream().map(ProgrammingExerciseTestCaseResponseDTO::of).collect(Collectors.toSet());
            websocketMessagingService.sendMessage("/topic/programming-exercises/" + exercise.getId() + "/test-cases", testCaseDTOs);
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
    public boolean generateTestCasesFromBuildResult(BuildResultNotification buildResult, ProgrammingExercise exercise) {
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
            saveToleratingConcurrentInsert(exercise, testCasesToSave);
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

    private Set<ProgrammingExerciseTestCase> getTestCasesFromBuildResult(BuildResultNotification buildResult, ProgrammingExercise exercise) {
        Visibility defaultVisibility = exercise.getDefaultTestCaseVisibility();

        return buildResult.jobs().stream().flatMap(job -> Stream.concat(job.failedTests().stream(), job.successfulTests().stream()))
                // we use default values for weight, bonus multiplier and bonus points
                .map(testCase -> new ProgrammingExerciseTestCase().testName(testCase.name()).weight(1.0).bonusMultiplier(1.0).bonusPoints(0.0).exercise(exercise).active(true)
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
    public void categorizeScaFeedback(Result result, List<ScaFeedback> staticCodeAnalysisFeedback, ProgrammingExercise programmingExercise) {
        var categoryPairs = getCategoriesWithMappingForExercise(programmingExercise);

        for (Iterator<ScaFeedback> iterator = staticCodeAnalysisFeedback.iterator(); iterator.hasNext();) {
            var scaFeedback = iterator.next();
            // Determine the Artemis category for this issue via the (persisted) tool-reported category;
            // rows without one (migrated rows whose legacy JSON was unparseable) fall back to their
            // already-resolved Artemis category.
            Optional<StaticCodeAnalysisCategory> category = findCategoryForIssue(scaFeedback, categoryPairs);

            if (category.isEmpty() || category.get().getState() == CategoryState.INACTIVE) {
                // Remove feedback of unmapped or inactive categories permanently
                result.getScaFeedbacks().remove(scaFeedback);
                iterator.remove();
                continue;
            }

            scaFeedback.setCategory(category.get().getName());
            if (category.get().getState() == CategoryState.GRADED) {
                scaFeedback.setPenalty(category.get().getPenalty());
            }
            else {
                scaFeedback.setPenalty(null);
            }
        }
    }

    private Optional<StaticCodeAnalysisCategory> findCategoryForIssue(ScaFeedback scaFeedback,
            Map<StaticCodeAnalysisCategory, List<StaticCodeAnalysisDefaultCategory.CategoryMapping>> categoryPairs) {
        if (scaFeedback.getToolCategory() != null) {
            return categoryPairs.entrySet().stream().filter(
                    pair -> pair.getValue().stream().anyMatch(mapping -> mapping.tool() == scaFeedback.getTool() && mapping.category().equals(scaFeedback.getToolCategory())))
                    .map(Map.Entry::getKey).findFirst();
        }
        // already-categorized row (loaded from the database): match by the stored Artemis category name
        return categoryPairs.keySet().stream().filter(category -> category.getName().equals(scaFeedback.getCategory())).findFirst();
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
