package de.tum.in.www1.artemis.service;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Feedback;
import de.tum.in.www1.artemis.domain.TextBlock;
import de.tum.in.www1.artemis.domain.TextCluster;
import de.tum.in.www1.artemis.domain.TextExercise;
import de.tum.in.www1.artemis.domain.enumeration.FeedbackType;
import de.tum.in.www1.artemis.domain.enumeration.StaticCodeAnalysisTool;
import de.tum.in.www1.artemis.repository.FeedbackRepository;
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
     * Mapping ({} marks optional content as not all static code analysis tools support all attributes):
     * - text: STATIC_CODE_ANALYSIS_FEEDBACK_IDENTIFIER:tool:category:rule
     * - reference: filePath:startLine-endLine:{startColumn-endColumn}
     * - detailText: message
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
                feedback.setText(Feedback.STATIC_CODE_ANALYSIS_FEEDBACK_IDENTIFIER + tool.name() + ":" + issue.getCategory() + ":" + issue.getRule());
                feedback.setReference(createSourceLocation(issue));
                feedback.setDetailText(issue.getMessage());
                feedback.setType(FeedbackType.AUTOMATIC);
                feedback.setPositive(false);
                feedbackList.add(feedback);
            }
        }
        return feedbackList;
    }

    private String createSourceLocation(StaticCodeAnalysisReportDTO.StaticCodeAnalysisIssue issue) {
        StringBuilder sourceLocation = new StringBuilder();

        // The file path is always present
        sourceLocation.append(issue.getFilePath());

        // Start and end line is always present
        sourceLocation.append(":").append(issue.getStartLine()).append("-").append(issue.getEndLine());

        // Columns are not reported by all static code analysis tools. If startColumn is present then endColumn is too
        if (issue.getStartColumn() == null) {
            return sourceLocation.toString();
        }
        sourceLocation.append(":").append(issue.getStartColumn()).append(issue.getEndColumn());
        return sourceLocation.toString();
    }
}
