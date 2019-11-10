package de.tum.in.www1.artemis.service;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.repository.FeedbackRepository;

@Service
@Transactional
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
     * @param cluster TextCluster requesting existinging Feedbacks for.
     * @return Map<TextBlockId, Feedback>
     */
    @Transactional(readOnly = true)
    public Map<String, Feedback> getFeedbackForTextExerciseInCluster(TextCluster cluster) {
        final List<String> references = cluster.getBlocks().stream().map(TextBlock::getId).collect(toList());
        final TextExercise exercise = cluster.getExercise();
        return feedbackRepository.findByReferenceInAndResult_Submission_Participation_Exercise(references, exercise).parallelStream()
                .collect(toMap(Feedback::getReference, f -> f));
    }
}
