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
    public Optional<Double> calculateVariance(TextCluster textCluster) {
        return Optional.empty();
    }

    /**
     * Calculates the expected value in a text cluster
     * @param cluster Text cluster for which the expected value is calculated
     * @return
     */
    @Override
    public Optional<Double> calculateExpectation(TextCluster cluster) {
        return Optional.of(cluster.getBlocks().stream()

                // Only take the text blocks which are credited
                .filter(block -> getCreditsOfTextBlock(block).isPresent())

                // Calculate the expected value of each random variable (the credit score) and its
                // probability (its coverage percentage over a cluster which is uniformly distributed)
                .mapToDouble(block -> (double) (1 / cluster.size()) * getCreditsOfTextBlock(block).get())

                // Sum the up to create the expectation value of a cluster in terms of a score
                .sum());
    }

    /**
     * Calculates the standard deviation of a text cluster
     * @param textCluster cluster for which the standard deviation is calculated
     * @return {Optional<Double>}
     */
    @Override
    public Optional<Double> calculateStandardDeviation(TextCluster textCluster) {
        return Optional.empty();
    }

    /**
     * Calculates the coverage percentage of how  many text blocks in a cluster are present
     * @param cluster cluster for which the coverage percentage is calculated
     * @return
     */
    @Override
    public Optional<Double> calculateCoveragePercentage(TextCluster cluster) {
        if (cluster != null) {
            final List<TextBlock> allBlocksInCluster = cluster.getBlocks().parallelStream().collect(toList());

            final double creditedBlocks = (double) allBlocksInCluster.stream().filter(block -> (getCreditsOfTextBlock(block).isPresent())).count();

            return Optional.of(creditedBlocks / (double) allBlocksInCluster.size());
        }

        return Optional.empty();
    }

    /**
     * Calculates the coverage percentage of a cluster with the same score as a given text block
     * @param textBlock text block for which its clusters score coverage percentage is determined
     * @return {Optional<Double>} Optional which contains the percentage between [0.0-1.0] or empty if text block is not in a cluster
     */
    @Override
    public Optional<Double> calculateScoreCoveragePercentage(TextBlock textBlock) {
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

    /**
     * Calculates the average score of a text cluster
     * @param cluster text cluster for which the average score should be computed
     * @return {Optional<Double>} Optional which contains the average scores or empty if there are none
     */
    @Override
    public Optional<Double> calculateAverage(TextCluster cluster) {
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

    /**
     * Getter for the cluster size of a text cluster
     * @param cluster cluster for which the size should be returned
     * @return {Integer} size of the cluster
     */
    @Override
    public Integer getClusterSize(TextCluster cluster) {
        return cluster.size();
    }

    /**
     * Returns the size of the cluster of a text block
     * @param textBlock textBlock for which the cluster size should be determined
     * @return {Integer} size of the text cluster
     */
    @Override
    public Integer getClusterSize(TextBlock textBlock) {
        return textBlock.getCluster().size();
    }

    /**
     * Casts the resultString of a Result to its double representation
     * @param resultString String representatino of the representation
     * @return {Double} Double representation of the result
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
            return Optional.of(feedback.get(block.getId()).getCredits());
        }
        return Optional.empty();
    }
}
