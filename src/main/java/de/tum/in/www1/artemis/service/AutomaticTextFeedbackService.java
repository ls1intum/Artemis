package de.tum.in.www1.artemis.service;

import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toList;

import java.util.*;

import org.jetbrains.annotations.NotNull;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.FeedbackType;
import de.tum.in.www1.artemis.repository.TextBlockRepository;

@Service
@Profile("automaticText")
public class AutomaticTextFeedbackService implements TextAssessmentUtilities {

    private final FeedbackService feedbackService;

    private static final double DISTANCE_THRESHOLD = 1;

    private final TextBlockRepository textBlockRepository;

    public AutomaticTextFeedbackService(FeedbackService feedbackService, TextBlockRepository textBlockRepository) {
        this.feedbackService = feedbackService;
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
        final List<TextBlock> blocks = textBlockRepository.findAllWithEagerClusterBySubmissionId(textSubmission.getId());
        textSubmission.setBlocks(blocks);

        final List<Feedback> suggestedFeedback = blocks.stream().map(block -> {
            final TextCluster cluster = block.getCluster();
            Feedback newFeedback = new Feedback().reference(block.getId());

            // if TextBlock is part of a cluster, we try to find an existing Feedback Element
            if (cluster != null) {
                // Find all Feedbacks for other Blocks in Cluster.
                final List<TextBlock> allBlocksInCluster = cluster.getBlocks().parallelStream().filter(elem -> !elem.equals(block)).collect(toList());
                final Map<String, Feedback> feedbackForTextExerciseInCluster = feedbackService.getFeedbackForTextExerciseInCluster(cluster);

                if (feedbackForTextExerciseInCluster.size() != 0) {
                    final Optional<TextBlock> mostSimilarBlockInClusterWithFeedback = allBlocksInCluster.parallelStream()

                            // Filter all other blocks in the cluster for those with Feedback
                            .filter(element -> feedbackForTextExerciseInCluster.keySet().contains(element.getId()))

                            // Find the closest block
                            .min(comparing(element -> cluster.distanceBetweenBlocks(block, element)));

                    if (mostSimilarBlockInClusterWithFeedback.isPresent()
                            && cluster.distanceBetweenBlocks(block, mostSimilarBlockInClusterWithFeedback.get()) < DISTANCE_THRESHOLD) {
                        final Feedback similarFeedback = feedbackForTextExerciseInCluster.get(mostSimilarBlockInClusterWithFeedback.get().getId());
                        return newFeedback.credits(similarFeedback.getCredits()).detailText(similarFeedback.getDetailText()).type(FeedbackType.AUTOMATIC);

                    }
                }
            }

            return newFeedback.credits(0d).type(FeedbackType.MANUAL);
        }).collect(toList());

        result.setFeedbacks(suggestedFeedback);
    }

    @Override
    public Optional<Double> determineVariance(TextBlock textBlock) {
        return Optional.empty();
    }

    @Override
    public Optional<Double> determineExpectation(TextBlock textBlock) {
        final TextCluster cluster = textBlock.getCluster();
        final double credits = getCreditsOfTextBlock(textBlock).get();

    }

    @Override
    public Optional<Double> determineStandardDeviation(TextBlock textBlock) {
        return Optional.empty();
    }

    @Override
    public Optional<Double> determineCoveragePercentage(TextBlock textBlock) {
        final TextCluster cluster = textBlock.getCluster();

        if (cluster != null) {
            final List<TextBlock> allBlocksInCluster = cluster.getBlocks().parallelStream().collect(toList());

            final double creditedBlocks = (double) allBlocksInCluster.stream().filter(block -> (getCreditsOfTextBlock(block).isPresent())).count();

            return Optional.of(creditedBlocks / (double) allBlocksInCluster.size());
        }

        return Optional.empty();
    }

    @Override
    public Optional<Double> determineScoreCoveragePercentage(TextBlock textBlock) {
        final TextCluster cluster = textBlock.getCluster();

        if (cluster != null) {

            final List<TextBlock> allBlocksInCluster = cluster.getBlocks().parallelStream().collect(toList());

            final double textBlockCredits = getCreditsOfTextBlock(textBlock).get();

            final List<TextBlock> blocksWithSameCredit = cluster.getBlocks().parallelStream().filter(block -> (getCreditsOfTextBlock(block).get() == textBlockCredits))
                    .collect(toList());

            return Optional.of(((double) blocksWithSameCredit.size()) / ((double) allBlocksInCluster.size()));
        }

        return Optional.empty();
    }

    @Override
    public Optional<Double> determineAverage(TextBlock textBlock) {
        final TextCluster cluster = textBlock.getCluster();

        if (cluster != null) {

            final List<TextBlock> allBlocksInCluster = cluster.getBlocks().parallelStream().collect(toList());

            final Double scoringSum = allBlocksInCluster.stream().mapToDouble(block -> {
                if (getCreditsOfTextBlock((block)).isPresent()) {
                    return getCreditsOfTextBlock(block).get();
                }
                return 0.0;
            }).sum();

            return Optional.of(scoringSum / (double) allBlocksInCluster.size());
        }

        return Optional.empty();
    }

    @Override
    public Integer determineClusterSize(TextBlock textBlock) {
        return textBlock.getCluster().size();
    }

    /**
     * Casts the resultString of a Result to its double representation
     * @param resultString
     * @return
     */
    public double castResultString(String resultString) {
        if (resultString.contains(" ")) {
            resultString = resultString.substring(0, resultString.indexOf(" "));
            return Double.parseDouble(resultString);
        }
        return -1.0;
    }

    /**
     * Returns the credits of a text block as an optional depending on wether a text block has credits
     * @param block {TextBlock} text block for which the credits are returned
     * @return {Optional<Double>} credits of the text block as Double Object in an Optional Wrapper
     */
    public Optional<Double> getCreditsOfTextBlock(TextBlock block) {
        final TextCluster cluster = block.getCluster();

        final Map<String, Feedback> feedback = feedbackService.getFeedbackForTextExerciseInCluster(cluster);
        if (feedback != null) {
            return Optional.of(feedback.values().parallelStream().mapToDouble(f -> f.getCredits()).sum());
        }
        return Optional.empty();
    }
}
