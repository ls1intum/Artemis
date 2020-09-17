package de.tum.in.www1.artemis.service;

import static de.tum.in.www1.artemis.config.Constants.FEEDBACK_DETAIL_TEXT_MAX_CHARACTERS;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.FeedbackType;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.domain.enumeration.StaticCodeAnalysisTool;
import de.tum.in.www1.artemis.repository.FeedbackRepository;
import de.tum.in.www1.artemis.service.connectors.bamboo.dto.BambooBuildResultNotificationDTO;
import de.tum.in.www1.artemis.service.dto.StaticCodeAnalysisReportDTO;

@Service
public class FeedbackService {

    private final Logger log = LoggerFactory.getLogger(FeedbackService.class);

    private final FeedbackRepository feedbackRepository;

    // need bamboo service and resultrepository to create and store from old feedbacks
    public FeedbackService(FeedbackRepository feedbackRepository) {
        this.feedbackRepository = feedbackRepository;
    }

    /**
     * Find all existing Feedback Elements referencing a text block part of a TextCluster.
     *
     * @param cluster TextCluster requesting existing Feedbacks for.
     * @return Map<TextBlockId, Feedback>
     */
    public Map<String, Feedback> getFeedbackForTextExerciseInCluster(TextCluster cluster) {
        final List<String> references = cluster.getBlocks().stream().map(TextBlock::getId).collect(toList());
        final TextExercise exercise = cluster.getExercise();
        return feedbackRepository.findByReferenceInAndResult_Submission_Participation_Exercise(references, exercise).parallelStream()
                .collect(toMap(Feedback::getReference, feedback -> feedback));
    }

    /**
     * Transforms static code analysis reports to feedback objects.
     * As we reuse the Feedback entity to store static code analysis findings, a mapping to those attributes
     * has to be defined, violating the first normal form.
     *
     * @param reports Static code analysis reports to be transformed
     * @return Feedback objects representing the static code analysis findings
     */
    public List<Feedback> createFeedbackFromStaticCodeAnalysisReports(List<StaticCodeAnalysisReportDTO> reports) {
        List<Feedback> feedbackList = new ArrayList<>();
        for (final var report : reports) {
            StaticCodeAnalysisTool tool = report.getTool();

            for (final var issue : report.getIssues()) {
                Feedback feedback = new Feedback();
                feedback.setText(Feedback.STATIC_CODE_ANALYSIS_FEEDBACK_IDENTIFIER + tool.name() + ":" + issue.getCategory());
                feedback.setDetailText(issue.getMessage());
                feedback.setReference(issue.getClassname() + ':' + issue.getLine());
                feedback.setType(FeedbackType.AUTOMATIC);
                feedback.setPositive(false);
                feedbackList.add(feedback);
            }
        }
        return feedbackList;
    }

    /**
     * Create an automatic feedback object from a test job.
     * @param testJob the test case to be transformed.
     * @param successful if the test case was successful.
     * @param programmingLanguage the programming language of the exercise.
     * @return Feedback object for the test job
     */
    public Feedback createFeedbackFromTestJob(BambooBuildResultNotificationDTO.BambooTestJobDTO testJob, boolean successful, final ProgrammingLanguage programmingLanguage) {

        Feedback feedback = new Feedback();
        feedback.setText(testJob.getName()); // in the attribute "methodName", bamboo seems to apply some unwanted logic

        if (!successful) {

            String errorMessageString = testJob.getErrors().stream().map(errorString -> processResultErrorMessage(programmingLanguage, errorString)).reduce("", String::concat);

            if (errorMessageString.length() > FEEDBACK_DETAIL_TEXT_MAX_CHARACTERS) {
                errorMessageString = errorMessageString.substring(0, FEEDBACK_DETAIL_TEXT_MAX_CHARACTERS);
            }

            feedback.setDetailText(errorMessageString);
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
     * @param message The raw error message in the feedback
     * @return A filtered and better formatted error message
     */
    private String processResultErrorMessage(final ProgrammingLanguage programmingLanguage, final String message) {
        if (programmingLanguage == ProgrammingLanguage.JAVA) {
            // Splitting string at the first linebreak to only get the first line of the Exception
            return message.split("\\n", 2)[0]
                    // junit 4
                    .replace("java.lang.AssertionError: ", "")
                    // junit 5
                    .replace("org.opentest4j.AssertionFailedError: ", "");
        }

        return message;
    }
}
