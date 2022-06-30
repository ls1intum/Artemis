package de.tum.in.www1.artemis.service;

import static java.util.Comparator.comparing;

import java.util.*;
import java.util.stream.Collectors;

import javax.validation.constraints.NotNull;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.FeedbackType;
import de.tum.in.www1.artemis.repository.FeedbackRepository;
import de.tum.in.www1.artemis.repository.TextBlockRepository;

@Service
@Profile("athene")
public class AutomaticTextFeedbackService {

    private final FeedbackRepository feedbackRepository;

    private static final double DISTANCE_THRESHOLD = 1;

    private final TextBlockRepository textBlockRepository;

    public AutomaticTextFeedbackService(FeedbackRepository feedbackRepository, TextBlockRepository textBlockRepository) {
        this.feedbackRepository = feedbackRepository;
        this.textBlockRepository = textBlockRepository;
    }

    /**
     * Suggest Feedback for a Submission based on its cluster.
     * For each TextBlock of the submission, this method finds already existing Feedback elements in the same cluster and chooses the one with the minimum distance.
     * Otherwise, an empty Feedback Element is created for simplicity.
     * Feedbacks are stored inline with the provided Result object.
     *
     * @param result Result for the Submission
     */
    @Transactional(readOnly = true)
    public void suggestFeedback(@NotNull Result result) {
        final TextSubmission textSubmission = (TextSubmission) result.getSubmission();
        final var blocks = textBlockRepository.findAllWithEagerClusterBySubmissionId(textSubmission.getId());
        textSubmission.setBlocks(blocks);

        final List<Feedback> suggestedFeedback = blocks.stream().map(block -> {
            final TextCluster cluster = block.getCluster();

            // if TextBlock is part of a cluster and the cluster is not disabled, we try to find an existing Feedback Element
            if (cluster != null && !cluster.isDisabled()) {
                // Find all Feedbacks for other Blocks in Cluster.
                final List<TextBlock> allBlocksInCluster = cluster.getBlocks().parallelStream().filter(elem -> !elem.equals(block)).toList();
                final Map<String, Feedback> feedbackForTextExerciseInCluster = feedbackRepository.getFeedbackForTextExerciseInCluster(cluster);

                if (!feedbackForTextExerciseInCluster.isEmpty()) {
                    final Optional<TextBlock> mostSimilarBlockInClusterWithFeedback = allBlocksInCluster.parallelStream()

                            // Filter all other blocks in the cluster for those with Feedback
                            .filter(element -> feedbackForTextExerciseInCluster.containsKey(element.getId()))

                            // Find the closest block
                            .min(comparing(element -> cluster.distanceBetweenBlocks(block, element)));

                    if (mostSimilarBlockInClusterWithFeedback.isPresent()
                            && cluster.distanceBetweenBlocks(block, mostSimilarBlockInClusterWithFeedback.get()) < DISTANCE_THRESHOLD) {
                        final Feedback similarFeedback = feedbackForTextExerciseInCluster.get(mostSimilarBlockInClusterWithFeedback.get().getId());
                        String originBlockReference = mostSimilarBlockInClusterWithFeedback.get().getId();
                        Long originSubmissionId = mostSimilarBlockInClusterWithFeedback.get().getSubmission().getId();
                        Long originParticipationId = mostSimilarBlockInClusterWithFeedback.get().getSubmission().getParticipation().getId();

                        Feedback feedback = new Feedback().reference(block.getId()).credits(similarFeedback.getCredits())
                                .suggestedFeedbackOrigin(originBlockReference, originSubmissionId, originParticipationId).type(FeedbackType.AUTOMATIC);

                        if (similarFeedback.getGradingInstruction() != null) {
                            feedback.setGradingInstruction(similarFeedback.getGradingInstruction());
                        }
                        else {
                            feedback.setDetailText(similarFeedback.getDetailText());
                        }

                        return feedback;
                    }
                }
            }

            return null;
        }).filter(Objects::nonNull).collect(Collectors.toCollection(ArrayList::new));

        result.setFeedbacks(suggestedFeedback);
    }

}
