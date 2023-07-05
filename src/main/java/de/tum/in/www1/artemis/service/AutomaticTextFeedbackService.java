package de.tum.in.www1.artemis.service;

import java.util.*;

import javax.validation.constraints.NotNull;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.repository.FeedbackRepository;
import de.tum.in.www1.artemis.repository.TextBlockRepository;

@Service
@Profile("athena")
public class AutomaticTextFeedbackService {

    private final FeedbackRepository feedbackRepository;

    private final TextBlockRepository textBlockRepository;

    public AutomaticTextFeedbackService(FeedbackRepository feedbackRepository, TextBlockRepository textBlockRepository) {
        this.feedbackRepository = feedbackRepository;
        this.textBlockRepository = textBlockRepository;
    }

    /**
     * Suggest Feedback for a Submission.
     * Otherwise, an empty Feedback Element is created for simplicity.
     * Feedbacks are stored inline with the provided Result object.
     *
     * @param result Result for the Submission
     */
    public void suggestFeedback(@NotNull Result result) {
        final TextSubmission textSubmission = (TextSubmission) result.getSubmission();
        final var blocks = textBlockRepository.findAllBySubmissionId(textSubmission.getId());
        textSubmission.setBlocks(blocks);

        final List<Feedback> suggestedFeedback = new ArrayList<>();

        // TODO: request from Athena service
        // TODO: check if feedbackRepository is still needed for this

        result.setFeedbacks(suggestedFeedback);
    }

}
