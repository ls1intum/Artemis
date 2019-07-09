package de.tum.in.www1.artemis.service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.jetbrains.annotations.NotNull;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.FeedbackType;

@Service
@Profile("automaticText")
public class AutomaticTextFeedbackService {

    private final FeedbackService feedbackService;

    public AutomaticTextFeedbackService(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    /**
     * @param result
     */
    @Transactional
    public void suggestFeedback(@NotNull Result result) {
        final TextSubmission textSubmission = (TextSubmission) result.getSubmission();
        final TextExercise exercise = (TextExercise) textSubmission.getParticipation().getExercise();
        final List<TextBlock> blocks = textSubmission.getBlocks();

        final List<Feedback> suggestedFeedback = blocks.parallelStream().map(block -> {
            final TextCluster cluster = block.getCluster();
            Feedback newFeedback = new Feedback().reference(block.getId());

            if (cluster != null) {
                final List<TextBlock> allBlocksInCluster = cluster.getBlocks();
                final List<Feedback> feedbackForTextExerciseInCluster = feedbackService.getFeedbackForTextExerciseInCluster(exercise, cluster);
                // TODO: Use HashMap

                final List<String> feedbackReferences = feedbackForTextExerciseInCluster.parallelStream().map(Feedback::getReference).collect(Collectors.toList());
                final Optional<TextBlock> mostSimilarBlockInClusterWithFeedback = allBlocksInCluster.parallelStream()
                        .filter(element -> feedbackReferences.contains(element.getId())).min(Comparator.comparing(element -> cluster.distanceBetweenBlocks(block, element)));
                // TODO: Refactor Comparator to TextBlock Class

                if (mostSimilarBlockInClusterWithFeedback.isPresent()) {
                    final Feedback similarFeedback = feedbackForTextExerciseInCluster.parallelStream()
                            .filter(element -> element.getReference().equals(mostSimilarBlockInClusterWithFeedback.get().getId())).findFirst()
                            .orElseThrow(() -> new IllegalStateException("Feedback Element must exist. Existence checked before!"));

                    return newFeedback.reference(block.getId()).credits(similarFeedback.getCredits()).detailText(similarFeedback.getDetailText()).type(FeedbackType.AUTOMATIC);

                }
            }

            return newFeedback.credits(0d).type(FeedbackType.MANUAL);
        }).collect(Collectors.toList());

        result.getFeedbacks().addAll(suggestedFeedback);
    }

}
