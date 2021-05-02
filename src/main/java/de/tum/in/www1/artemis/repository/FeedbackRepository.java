package de.tum.in.www1.artemis.repository;

import static de.tum.in.www1.artemis.config.Constants.FEEDBACK_DETAIL_TEXT_MAX_CHARACTERS;
import static java.util.stream.Collectors.*;

import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.FeedbackType;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.domain.enumeration.StaticCodeAnalysisTool;
import de.tum.in.www1.artemis.service.dto.StaticCodeAnalysisReportDTO;

/**
 * Spring Data JPA repository for the Feedback entity.
 */
@SuppressWarnings("unused")
@Repository
public interface FeedbackRepository extends JpaRepository<Feedback, Long> {

    String DEFAULT_FILEPATH = "notAvailable";

    Pattern JVM_RESULT_MESSAGE_MATCHER = prepareJVMResultMessageMatcher(
            List.of("java.lang.AssertionError", "org.opentest4j.AssertionFailedError", "de.tum.in.test.api.util.UnexpectedExceptionError"));

    Predicate<String> IS_NOT_STACK_TRACE_LINE = line -> !line.startsWith("\tat ");

    List<Feedback> findByResult(Result result);

    List<Feedback> findByReferenceInAndResult_Submission_Participation_Exercise(List<String> references, Exercise exercise);

    @Query("select count(fd.id) from Feedback fd where fd.gradingInstruction.id in :gradingInstructionsId")
    long findFeedbackCountByStructuredGradingInstructionIds(@Param("gradingInstructionsId") List<Long> gradingInstructionsId);

    /**
     * Delete all feedbacks that belong to the given result
     * @param resultId the Id of the result where the feedbacks should be deleted
     */
    void deleteByResult_Id(long resultId);

    /**
     * Save the given feedback elements to the database in case they are not yet connected to a result
     *
     * @param feedbackList the feedback items that should be saved
     * @return all elements of the original list with the saved feedback items (i.e. the ones without result) having an id now.
     */
    default List<Feedback> saveFeedbacks(List<Feedback> feedbackList) {
        List<Feedback> updatedFeedbackList = new ArrayList<>();
        for (var feedback : feedbackList) {
            if (feedback.getResult() == null) {
                // only save feedback not yet connected to a result
                updatedFeedbackList.add(save(feedback));
            }
            else {
                updatedFeedbackList.add(feedback);
            }
        }
        return updatedFeedbackList;
    }

    /**
     * Find all existing Feedback Elements referencing a text block part of a TextCluster.
     *
     * @param cluster TextCluster requesting existing Feedbacks for.
     * @return Map<TextBlockId, Feedback>
     */
    default Map<String, Feedback> getFeedbackForTextExerciseInCluster(TextCluster cluster) {
        final List<String> references = cluster.getBlocks().stream().map(TextBlock::getId).collect(toList());
        final TextExercise exercise = cluster.getExercise();
        return findByReferenceInAndResult_Submission_Participation_Exercise(references, exercise).parallelStream().collect(toMap(Feedback::getReference, feedback -> feedback));
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
    default List<Feedback> createFeedbackFromStaticCodeAnalysisReports(List<StaticCodeAnalysisReportDTO> reports) {
        ObjectMapper mapper = new ObjectMapper();
        List<Feedback> feedbackList = new ArrayList<>();
        for (final var report : reports) {
            StaticCodeAnalysisTool tool = report.getTool();

            for (final var issue : report.getIssues()) {
                // Remove CI specific path segments
                issue.setFilePath(removeCIDirectoriesFromPath(issue.getFilePath()));

                Feedback feedback = new Feedback();
                feedback.setText(Feedback.STATIC_CODE_ANALYSIS_FEEDBACK_IDENTIFIER);
                feedback.setReference(tool.name());
                feedback.setType(FeedbackType.AUTOMATIC);
                feedback.setPositive(false);

                // Store static code analysis in JSON format
                try {
                    feedback.setDetailText(mapper.writeValueAsString(issue));
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
     * @param testName the test case name.
     * @param testMessages a list of informational messages generated by the test job
     * @param successful if the test case was successful.
     * @param programmingLanguage the programming language of the exercise.
     * @return Feedback object for the test job
     */
    default Feedback createFeedbackFromTestCase(String testName, List<String> testMessages, boolean successful, final ProgrammingLanguage programmingLanguage) {
        Feedback feedback = new Feedback();
        feedback.setText(testName);

        if (!successful) {
            String errorMessageString = testMessages.stream().map(errorString -> processResultErrorMessage(programmingLanguage, errorString)).collect(Collectors.joining("\n\n"));
            if (errorMessageString.length() > FEEDBACK_DETAIL_TEXT_MAX_CHARACTERS) {
                errorMessageString = errorMessageString.substring(0, FEEDBACK_DETAIL_TEXT_MAX_CHARACTERS);
            }
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

    default Long findFeedbackByStructuredGradingInstructionId(List<GradingCriterion> gradingCriteria) {
        List<Long> list = gradingCriteria.stream().flatMap( gradingCriterion ->  gradingCriterion.getStructuredGradingInstructions().stream()).map(GradingInstruction::getId).collect(toList());
        return findFeedbackCountByStructuredGradingInstructionIds(list);
    }

    /**
     * Filters and processes a feedback error message, thereby removing any unwanted strings depending on
     * the programming language, or just reformatting it to only show the most important details.
     *
     * @param programmingLanguage The programming language for which the feedback was generated
     * @param message The raw error message in the feedback
     * @return A filtered and better formatted error message
     */
    private static String processResultErrorMessage(final ProgrammingLanguage programmingLanguage, final String message) {
        final String timeoutDetailText = "The test case execution timed out. This indicates issues in your code such as endless for / while loops or issues with recursion. Please carefully review your code to avoid such issues. In case you are absolutely sure that there are no issues like this, please contact your instructor to check the setup of the test.";
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
            // overwrite AJTS: TimeoutException
            String generalTimeOutExceptionText = matcher.group(1);
            return timeoutDetailText + "\n" + exceptionPrefix + generalTimeOutExceptionText.trim();
        }

        // Filter out unneeded Exception classnames
        if (programmingLanguage == ProgrammingLanguage.JAVA || programmingLanguage == ProgrammingLanguage.KOTLIN) {
            String messageWithoutStackTrace = message.lines().takeWhile(IS_NOT_STACK_TRACE_LINE).collect(Collectors.joining("\n")).trim();
            return JVM_RESULT_MESSAGE_MATCHER.matcher(messageWithoutStackTrace).replaceAll("");
        }

        return message;
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
}
