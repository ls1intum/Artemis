package de.tum.in.www1.artemis.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jetbrains.annotations.NotNull;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.tum.in.www1.artemis.domain.*;
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
        /*
         * final TextSubmission textSubmission = (TextSubmission) result.getSubmission(); final List<TextBlock> blocks =
         * textBlockRepository.findAllWithEagerClusterBySubmissionId(textSubmission.getId()); textSubmission.setBlocks(blocks); final List<Feedback> suggestedFeedback =
         * blocks.stream().map(block -> { final TextCluster cluster = block.getCluster(); Feedback newFeedback = new Feedback().reference(block.getId()); // if TextBlock is part of
         * a cluster, we try to find an existing Feedback Element if (cluster != null) { // Find all Feedbacks for other Blocks in Cluster. final List<TextBlock> allBlocksInCluster
         * = cluster.getBlocks().parallelStream().filter(elem -> !elem.equals(block)).collect(toList()); final Map<String, Feedback> feedbackForTextExerciseInCluster =
         * feedbackService.getFeedbackForTextExerciseInCluster(cluster); if (feedbackForTextExerciseInCluster.size() != 0) { final Optional<TextBlock>
         * mostSimilarBlockInClusterWithFeedback = allBlocksInCluster.parallelStream() // Filter all other blocks in the cluster for those with Feedback .filter(element ->
         * feedbackForTextExerciseInCluster.keySet().contains(element.getId())) // Find the closest block .min(comparing(element -> cluster.distanceBetweenBlocks(block, element)));
         * if (mostSimilarBlockInClusterWithFeedback.isPresent() && cluster.distanceBetweenBlocks(block, mostSimilarBlockInClusterWithFeedback.get()) < DISTANCE_THRESHOLD) { final
         * Feedback similarFeedback = feedbackForTextExerciseInCluster.get(mostSimilarBlockInClusterWithFeedback.get().getId()); return
         * newFeedback.credits(similarFeedback.getCredits()).detailText(similarFeedback.getDetailText()).type(FeedbackType.AUTOMATIC); } } } return
         * newFeedback.credits(0d).type(FeedbackType.MANUAL); }).collect(toList()); result.setFeedbacks(suggestedFeedback);
         */

    }

    @Override
    public double determineVariance(TextSubmission textSubmission) {

    }

    @Override
    public double determineExpectation(TextSubmission textSubmission) {
        return 0;
    }

    @Override
    public double determineStandardDeviation(TextSubmission textSubmission) {
        return 0;
    }

    @Override
    public double determineCoveragePercentage(TextSubmission textSubmission) {
        final List<TextBlock> blocks = textBlockRepository.findAllWithEagerClusterBySubmissionId(textSubmission.getId());

        final double creditedBlocks = (double) blocks.stream().filter(block -> (getCreditsOfTextBlock(block).isPresent())).count();

        return (creditedBlocks / (double) blocks.size());
    }

    @Override
    public double determineScoreCoveragePercentage(TextSubmission textSubmission, TextBlock textBlock) {

        final List<TextBlock> blocks = textBlockRepository.findAllWithEagerClusterBySubmissionId(textSubmission.getId());

        // Total number of text blocks in a cluster
        final double numberOfBlocks = (double) blocks.stream().filter(block -> getCreditsOfTextBlock(block).isPresent()).count();

        // Credit (e.g. 0.5, 1.0 etc.) score for which the cluster is checked
        final double textBlockCredit = getCreditsOfTextBlock(textBlock).get();

        // Should always return at least 1, since the text block which is assessed should be part of the cluster
        final double sameCreditBlocks = (double) blocks.stream().filter(block -> (getCreditsOfTextBlock(block).get() == textBlockCredit)).count();

        // Return the percentage of blocks which match the credit of a text block
        return (sameCreditBlocks / numberOfBlocks);
    }

    @Override
    public double determineAverage(TextSubmission textSubmission) {
        final List<TextBlock> blocks = textBlockRepository.findAllWithEagerClusterBySubmissionId(textSubmission.getId());
        // double resultSum = blocks.stream().mapToDouble(block -> castResultString(block.getSubmission().getResult().getResultString())).sum();

        double resultSum = blocks.stream().mapToDouble(block -> {
            if (getCreditsOfTextBlock(block).isPresent()) {
                return getCreditsOfTextBlock(block).get();
            }
            return 0.0;
        }).sum();

        double numberOfAssessments = (double) blocks.stream().filter(block -> getCreditsOfTextBlock(block).isPresent()).count();

        return resultSum / numberOfAssessments;
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
