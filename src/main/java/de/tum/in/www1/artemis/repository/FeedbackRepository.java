package de.tum.in.www1.artemis.repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.*;

/**
 * Spring Data JPA repository for the Feedback entity.
 */
@SuppressWarnings("unused")
@Repository
public interface FeedbackRepository extends JpaRepository<Feedback, Long> {

    List<Feedback> findByResult(Result result);

    List<Feedback> findByReferenceInAndResult_Submission_Participation_Exercise(List<String> references, Exercise exercise);

    @Query("select feedback from Feedback feedback where feedback.gradingInstruction.id in :gradingInstructionsIds")
    List<Feedback> findFeedbackByGradingInstructionIds(@Param("gradingInstructionsIds") List<Long> gradingInstructionsIds);

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
        final List<String> references = cluster.getBlocks().stream().map(TextBlock::getId).toList();
        final TextExercise exercise = cluster.getExercise();
        return findByReferenceInAndResult_Submission_Participation_Exercise(references, exercise).parallelStream()
                .collect(Collectors.toMap(Feedback::getReference, feedback -> feedback));
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
    default Feedback createFeedbackFromTestCase(String testName, List<String> testMessages, boolean successful, final ProgrammingLanguage programmingLanguage,
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

    /**
     * Given the grading criteria, collects each sub grading instructions in a list.
     * Then, find all feedback that matches with the grading instructions ids
     *
     * @param gradingCriteria The grading criteria belongs to exercise in a specific course
     * @return list including feedback entries which are associated with the grading instructions
     */
    default List<Feedback> findFeedbackByExerciseGradingCriteria(List<GradingCriterion> gradingCriteria) {
        List<Long> gradingInstructionsIds = gradingCriteria.stream().flatMap(gradingCriterion -> gradingCriterion.getStructuredGradingInstructions().stream())
                .map(GradingInstruction::getId).toList();
        return findFeedbackByGradingInstructionIds(gradingInstructionsIds);
    }
}
