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

    private static final int VARIANCE_THRESHOLD = 5;

    private final TextBlockRepository textBlockRepository;

    public AutomaticTextFeedbackService(FeedbackService feedbackService, TextBlockRepository textBlockRepository) {
        this.feedbackService = feedbackService;
        this.textBlockRepository = textBlockRepository;
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
                // Find all Feedbacks for other Blocks in Cluster.
                final List<TextBlock> allBlocksInCluster = textCluster.getBlocks().parallelStream().filter(elem -> !elem.equals(block)).collect(toList());
                final Map<String, Feedback> feedbackForTextExerciseInCluster = feedbackService.getFeedbackForTextExerciseInCluster(textCluster);

                if (feedbackForTextExerciseInCluster.size() != 0) {
                    final Optional<TextBlock> mostSimilarBlockInClusterWithFeedback = allBlocksInCluster.parallelStream()

                            // Filter all other blocks in the textCluster for those with Feedback
                            .filter(element -> feedbackForTextExerciseInCluster.containsKey(element.getId()))

                            // Find the closest block
                            .min(comparing(element -> textCluster.distanceBetweenBlocks(block, element)));

                    if (mostSimilarBlockInClusterWithFeedback.isPresent()
                            && textCluster.distanceBetweenBlocks(block, mostSimilarBlockInClusterWithFeedback.get()) < DISTANCE_THRESHOLD) {
                        final Feedback similarFeedback = feedbackForTextExerciseInCluster.get(mostSimilarBlockInClusterWithFeedback.get().getId());
                        return newFeedback.credits(similarFeedback.getCredits()).detailText(similarFeedback.getDetailText()).type(FeedbackType.AUTOMATIC);

                    }
                }
            }

            return newFeedback.credits(0d).type(FeedbackType.MANUAL);
        }).collect(toList());

        result.setFeedbacks(suggestedFeedback);
    }

    /**
     * Calculates the variance of a given text textCluster if the number of assessed text block in the textCluster exceeds the variance thresehold
     *
     * @param textCluster
     * @return
     */
    @Override
    public Optional<Double> calculateVariance(TextCluster textCluster) {

        final List<TextBlock> allAssessedBlocks = getAssessedBlocks(textCluster);

        // If not enough text blocks in a textCluster are assessed, return an empty optional
        if (allAssessedBlocks.size() < VARIANCE_THRESHOLD) {
            return Optional.empty();
        }

        // Expected value of a textCluster
        final double expectedValue = calculateExpectation(textCluster).get();

        return Optional.of(allAssessedBlocks.stream()

                // Calculate the variance of each random variable
                .mapToDouble(block -> {
                    if (getCreditsOfTextBlock(block).isPresent() && calculateScoreCoveragePercentage(block).isPresent()) {
                        return (getCreditsOfTextBlock(block).get() - expectedValue) * calculateScoreCoveragePercentage(block).get();
                    }
                    return 0.0;
                })
                // Sum them up to get the final value
                .reduce(0.0, Double::sum));
    }

    /**
     * Calculates the expected value in a text textCluster
     *
     * @param textCluster Text textCluster for which the expected value is calculated
     * @return
     */
    @Override
    public Optional<Double> calculateExpectation(TextCluster textCluster) {
        return Optional.of(textCluster.getBlocks().stream()

                // Only take the text blocks which are credited
                .filter(block -> getCreditsOfTextBlock(block).isPresent())

                // Calculate the expected value of each random variable (the credit score) and its
                // probability (its coverage percentage over a textCluster which is uniformly distributed)
                .mapToDouble(block -> (double) (1 / textCluster.size()) * getCreditsOfTextBlock(block).get())

                // Sum the up to create the expectation value of a textCluster in terms of a score
                .sum());
    }

    /**
     * Calculates the standard deviation of a text textCluster
     *
     * @param textCluster textCluster for which the standard deviation is calculated
     * @return {Optional<Double>}
     */
    @Override
    public Optional<Double> calculateStandardDeviation(TextCluster textCluster) {

        final double variance = calculateVariance(textCluster).get();

        return Optional.of(Math.sqrt(variance));
    }

    /**
     * Calculates the coverage percentage of how  many text blocks in a textCluster are present
     *
     * @param textCluster textCluster for which the coverage percentage is calculated
     * @return
     */
    @Override
    public Optional<Double> calculateCoveragePercentage(TextCluster textCluster) {
        if (textCluster != null) {
            final List<TextBlock> allBlocksInCluster = textCluster.getBlocks().parallelStream().collect(toList());

            final double creditedBlocks = (double) allBlocksInCluster.stream().filter(block -> (getCreditsOfTextBlock(block).isPresent())).count();

            return Optional.of(creditedBlocks / (double) allBlocksInCluster.size());
        }

        return Optional.empty();
    }

    /**
     * Calculates the coverage percentage of a textCluster with the same score as a given text block
     *
     * @param textBlock text block for which its textClusters score coverage percentage is determined
     * @return {Optional<Double>} Optional which contains the percentage between [0.0-1.0] or empty if text block is not in a textCluster
     */
    @Override
    public Optional<Double> calculateScoreCoveragePercentage(TextBlock textBlock) {
        final TextCluster textCluster = textBlock.getCluster();

        if (textCluster != null) {

            final List<TextBlock> allBlocksInCluster = textCluster.getBlocks().parallelStream().collect(toList());

            final double textBlockCredits = getCreditsOfTextBlock(textBlock).get();

            final List<TextBlock> blocksWithSameCredit = textCluster.getBlocks()

                    .parallelStream()

                    // Get all text blocks which have the same score as the current text block
                    .filter(block -> (getCreditsOfTextBlock(block).get() == textBlockCredits))

                    .collect(toList());

            return Optional.of(((double) blocksWithSameCredit.size()) / ((double) allBlocksInCluster.size()));
        }

        return Optional.empty();
    }

    /**
     * Calculates the average score of a text textCluster
     *
     * @param textCluster text textCluster for which the average score should be computed
     * @return {Optional<Double>} Optional which contains the average scores or empty if there are none
     */
    @Override
    public Optional<Double> calculateAverage(TextCluster textCluster) {
        if (textCluster != null) {

            final List<TextBlock> allBlocksInCluster = textCluster.getBlocks().parallelStream().collect(toList());

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
     * Gets the max score of a text textCluster
     * @param textCluster
     * @return
     */
    public OptionalDouble getMaxScore(TextCluster textCluster) {

        final List<TextBlock> allAssessedBlocks = getAssessedBlocks(textCluster);

        if (allAssessedBlocks.size() > 0) {
            return allAssessedBlocks.stream().mapToDouble(block -> getCreditsOfTextBlock(block).get()).reduce(Math::max);
        }

        return OptionalDouble.empty();
    }

    /**
     * Gets the medium score of a text textCluster
     * @param textCluster
     * @return
     */
    public Optional<Double> getMedianScore(TextCluster textCluster) {

        final List<TextBlock> allAssessedBlocks = getAssessedBlocks(textCluster);

        if (allAssessedBlocks.size() > 0) {
            return getCreditsOfTextBlock(allAssessedBlocks.get((allAssessedBlocks.size() / 2)));
        }
        return Optional.empty();
    }

    /**
     * Gets the minimum score of a text textCluster
     *
     * @param textCluster
     * @return
     */
    public OptionalDouble getMinimumScore(TextCluster textCluster) {

        final List<TextBlock> allAssessedBlocks = getAssessedBlocks(textCluster);

        if (allAssessedBlocks.size() > 0) {
            return allAssessedBlocks.stream().mapToDouble(block -> getCreditsOfTextBlock(block).get()).reduce(Math::min);
        }
        return OptionalDouble.empty();
    }

    /**
     * Getter for the textCluster size of a text textCluster
     * @param textCluster textCluster for which the size should be returned
     * @return {Integer} size of the textCluster
     */
    @Override
    public Integer getClusterSize(TextCluster textCluster) {
        return textCluster.size();
    }

    /**
     * Returns the size of the textCluster of a text block
     * @param textBlock textBlock for which the textCluster size should be determined
     * @return {Integer} size of the text textCluster
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
        final TextCluster textCluster = block.getCluster();

        final Map<String, Feedback> feedback = feedbackService.getFeedbackForTextExerciseInCluster(textCluster);
        if (feedback != null) {
            return Optional.of(feedback.get(block.getId()).getCredits());
        }
        return Optional.empty();
    }

    /**
     * Returns all assessed block in a text textCluster as a List
     * @param textCluster textCluster for which all assessed blocks should be returned
     * @return {List<Textblock>} all assessed text blocks
     */
    public List<TextBlock> getAssessedBlocks(TextCluster textCluster) {
        return textCluster.getBlocks()

                .stream()
                // Only get text blocks which are assessed
                .filter(block -> getCreditsOfTextBlock(block).isPresent())

                .collect(toList());
    }
}
