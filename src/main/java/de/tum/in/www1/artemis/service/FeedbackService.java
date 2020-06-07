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
import de.tum.in.www1.artemis.service.dto.StaticAssessmentReportDTO;

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

    // TODO: Move this to a StaticAssessmentService, implement it as a Helper Entity or directly in Feedback?
    /**
     * Transforms static assessment reports to feedback objects.
     * As we reuse the Feedback entity to store static code analysis issues, a mapping to those attributes
     * has to be defined, violating the first normal form.
     *
     * @param reports Static Assessment reports to be transformed
     * @return Feedback objects storing the static assessment issues
     */
    public List<Feedback> createFeedbackFromStaticAssessmentReports(List<StaticAssessmentReportDTO> reports) {
        List<Feedback> feedbackList = new ArrayList<>();
        for (final var report : reports) {
            StaticCodeAnalysisTool tool = report.getTool();

            for (final var issue : report.getIssues()) {
                Feedback feedback = new Feedback();
                feedback.setText(tool.name());
                feedback.setDetailText(issue.getMessage());
                feedback.setReference(issue.getClassname() + ':' + issue.getLine());
                feedback.setType(FeedbackType.AUTOMATIC_STATIC_ASSESSMENT);
                feedback.setPositive(false);
                feedbackList.add(feedback);
            }
        }
        return feedbackList;
    }
}
