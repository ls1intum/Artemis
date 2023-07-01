package de.tum.in.www1.artemis.service.programming;

import static de.tum.in.www1.artemis.config.Constants.FEEDBACK_DETAIL_TEXT_DATABASE_MAX_LENGTH;

import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.Feedback;
import de.tum.in.www1.artemis.domain.enumeration.*;
import de.tum.in.www1.artemis.service.ProfileService;
import de.tum.in.www1.artemis.service.dto.StaticCodeAnalysisReportDTO;

@Service
public class ProgrammingExerciseFeedbackCreationService {

    private static final String DEFAULT_FILEPATH = "notAvailable";

    private static final String PYTHON_EXCEPTION_LINE_PREFIX = "E       ";

    private static final Pattern JVM_RESULT_MESSAGE_MATCHER = prepareJVMResultMessageMatcher(
            List.of("java.lang.AssertionError", "org.opentest4j.AssertionFailedError", "de.tum.in.test.api.util.UnexpectedExceptionError"));

    private static final Predicate<String> IS_NOT_STACK_TRACE_LINE = line -> !line.startsWith("\tat ");

    private static final Predicate<String> IS_PYTHON_EXCEPTION_LINE = line -> line.startsWith(PYTHON_EXCEPTION_LINE_PREFIX);

    private static final List<String> TIMEOUT_EXCEPTIONS = Arrays.asList("org.junit.runners.model.TestTimedOutException", "java.util.concurrent.TimeoutException",
            "org.awaitility.core.ConditionTimeoutException", "Timed?OutException");

    private final ProfileService profileService;

    public ProgrammingExerciseFeedbackCreationService(ProfileService profileService) {
        this.profileService = profileService;
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
    private String processResultErrorMessage(final ProgrammingLanguage programmingLanguage, final ProjectType projectType, final String message) {
        final String timeoutDetailText = "The test case execution timed out. This indicates issues in your code such as endless loops, issues with recursion or really slow performance. Please carefully review your code to avoid such issues. In case you are absolutely sure that there are no issues like this, please contact your instructor to check the setup of the test.";
        final String exceptionPrefix = "Exception message: ";
        // Overwrite timeout exception messages for Junit4, Junit5 and other
        // Defining two pattern groups, (1) the exception name and (2) the exception text
        Pattern findTimeoutPattern = Pattern.compile("^.*(" + String.join("|", TIMEOUT_EXCEPTIONS) + "):?(.*)");
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

            // the feedback from gradle test result is duplicated on bamboo therefore it's cut in half
            if (projectType != null && projectType.isGradle() && profileService.isBamboo()) {
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
     *
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
        for (final var report : reports) {
            StaticCodeAnalysisTool tool = report.getTool();

            for (final var issue : report.getIssues()) {
                // Remove CI specific path segments
                issue.setFilePath(removeCIDirectoriesFromPath(issue.getFilePath()));

                if (issue.getMessage() != null) {
                    // Note: the feedback detail text is limited to 5.000 characters, so we limit the issue message to 4.500 characters to avoid issues
                    // the remaining 500 characters are used for the json structure of the issue
                    int maxLength = Math.min(issue.getMessage().length(), FEEDBACK_DETAIL_TEXT_DATABASE_MAX_LENGTH - 500);
                    issue.setMessage(issue.getMessage().substring(0, maxLength));
                }

                Feedback feedback = new Feedback();
                feedback.setText(Feedback.STATIC_CODE_ANALYSIS_FEEDBACK_IDENTIFIER);
                feedback.setReference(tool.name());
                feedback.setType(FeedbackType.AUTOMATIC);
                feedback.setPositive(false);

                // Store static code analysis in JSON format
                try {
                    // the feedback is already pre-truncated to fit, it should not be shortened further
                    feedback.setDetailTextTruncated(mapper.writeValueAsString(issue));
                }
                catch (JsonProcessingException e) {
                    continue;
                }
                feedbackList.add(feedback);
            }
        }
        return feedbackList;
    }

    /**
     * Create an automatic feedback object from a test job.
     *
     * @param testName            the test case name.
     * @param testMessages        a list of informational messages generated by the test job
     * @param successful          if the test case was successful.
     * @param programmingLanguage the programming language of the exercise.
     * @param projectType         the project type of the exercise.
     * @return Feedback object for the test job
     */
    public Feedback createFeedbackFromTestCase(String testName, List<String> testMessages, boolean successful, final ProgrammingLanguage programmingLanguage,
            final ProjectType projectType) {
        Feedback feedback = new Feedback();
        feedback.setText(testName);

        if (!successful) {
            String errorMessageString = testMessages.stream().map(errorString -> processResultErrorMessage(programmingLanguage, projectType, errorString))
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

}
