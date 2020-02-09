package de.tum.in.www1.artemis.service;

import static java.util.stream.Collectors.toList;

import java.util.*;

import javax.validation.constraints.NotNull;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.tum.in.www1.artemis.domain.Feedback;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.TextBlock;
import de.tum.in.www1.artemis.domain.TextCluster;
import de.tum.in.www1.artemis.domain.TextSubmission;
import de.tum.in.www1.artemis.domain.enumeration.FeedbackType;
import de.tum.in.www1.artemis.repository.TextBlockRepository;

@Service
@Profile("automaticText")
public class AutomaticTextFeedbackService {

    private final FeedbackService feedbackService;

    private static final double DISTANCE_THRESHOLD = 1;

    private final TextBlockRepository textBlockRepository;

    private final TextAssessmentUtilityService textAssessmentUtilityService;

    public AutomaticTextFeedbackService(FeedbackService feedbackService, TextBlockRepository textBlockRepository, TextAssessmentUtilityService textAssessmentUtilityService) {
        this.feedbackService = feedbackService;
        this.textBlockRepository = textBlockRepository;
        this.textAssessmentUtilityService = textAssessmentUtilityService;
    }

    /**
     * Suggest Feedback for a Submission based on its textCluster.
     * For each TextBlock of the submission, this method finds already existing Feedback elements in the same textCluster and chooses the one with the minimum distance.
     * Otherwise, an empty Feedback Element is created for simplicity.
     * Feedbacks are stored inline with the provided Result object.
     *
     * @param result Result for the Submission
     */
    @Transactional(readOnly = true)
    public void suggestFeedback(@NotNull Result result) {
        final TextSubmission textSubmission = (TextSubmission) result.getSubmission();
        final List<TextBlock> blocks = textBlockRepository.findAllWithEagerClusterBySubmissionId(textSubmission.getId());
        textSubmission.setBlocks(blocks);

        final List<Feedback> suggestedFeedback = blocks.stream().map(block -> {
            final TextCluster textCluster = block.getCluster();
            Feedback newFeedback = new Feedback().reference(block.getId());

            // if TextBlock is part of a textCluster, we try to find an existing Feedback Element
            if (textCluster != null) {
                try {
                    final OptionalDouble suggestedScore = textAssessmentUtilityService.calculateScore(block);

                    final OptionalDouble roundedScore = textAssessmentUtilityService.roundScore(block, suggestedScore.getAsDouble());

                    // Here would be the spot for the REST call to the confidence service

                    // Detail Text left blank on purpose (for now)
                    return newFeedback.credits(roundedScore.getAsDouble()).type(FeedbackType.AUTOMATIC).detailText("");
                }
                catch (NoSuchElementException exception) {
                    // Do nothing since we will request manual feedback
                    // return newFeedback.credits(0d).type(FeedbackType.MANUAL);
                }
            }
            return newFeedback.credits(0d).type(FeedbackType.MANUAL);
        }).collect(toList());

        result.setFeedbacks(suggestedFeedback);
    }
}
