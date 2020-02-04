package de.tum.in.www1.artemis.service;

import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toList;

import java.util.*;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Feedback;
import de.tum.in.www1.artemis.domain.TextBlock;
import de.tum.in.www1.artemis.domain.TextCluster;

@Service
@Profile("automaticText")
public class TextAssessmentUtilityService {

    private static FeedbackService feedbackService;

    public TextAssessmentUtilityService(FeedbackService feedbackService) {
        TextAssessmentUtilityService.feedbackService = feedbackService;
    }

    /**
     * Calculates the variance of a given text textCluster if the number of assessed text block in the textCluster exceeds the variance thresehold
     *
     * @param textCluster textCluster for which a variance is calculated
     * @param thresholdSize threshold for how many text blocks in a cluster are needed in order to allow a variance calculation
     * @return OptionalDouble containing the variance of a textCluster
     */
    public OptionalDouble calculateVariance(TextCluster textCluster, int thresholdSize) {

        final List<TextBlock> allAssessedBlocks = getAssessedBlocks(textCluster);

        // If not enough text blocks in a textCluster are assessed, return an empty optional
        if (allAssessedBlocks.size() < thresholdSize) {
            return OptionalDouble.empty();
        }

        try {
            // Expected value of a textCluster
            final double expectedValue = calculateExpectation(textCluster).getAsDouble();

            return OptionalDouble.of(allAssessedBlocks.stream()
                    // Calculate the variance of each random variable
                    .mapToDouble(block -> {
                        if (getCreditsOfTextBlock(block).isPresent() && calculateScoreCoveragePercentage(block).isPresent()) {
                            return (getCreditsOfTextBlock(block).getAsDouble() - expectedValue) * calculateScoreCoveragePercentage(block).getAsDouble();
                        }
                        return 0.0;
                    })
                    // Sum them up to get the final value
                    .reduce(0.0, Double::sum));
        }
        catch (NoSuchElementException exception) {
            return OptionalDouble.empty();
        }
    }

    /**
     * Calculates the expected value in a text textCluster
     *
     * @param textCluster Text textCluster for which the expected value is calculated
     * @return OptionalDouble containing the expected value of a text cluster
     */
    public OptionalDouble calculateExpectation(TextCluster textCluster) {

        try {
            return OptionalDouble.of(textCluster.getBlocks().stream()

                    // Only take the text blocks which are credited
                    .filter(block -> getCreditsOfTextBlock(block).isPresent())

                    // Calculate the expected value of each random variable (the credit score) and its
                    // probability (its coverage percentage over a textCluster which is uniformly distributed)
                    .mapToDouble(block -> (double) (1 / textCluster.size()) * getCreditsOfTextBlock(block).getAsDouble())

                    // Sum the up to create the expectation value of a textCluster in terms of a score
                    .sum());
        }
        catch (NoSuchElementException exception) {
            return OptionalDouble.empty();
        }
    }

    /**
     * Calculates the standard deviation of a text textCluster
     *
     * @param textCluster textCluster for which the standard deviation is calculated
     * @param thresholdSize thresholdsize which is passed to calculateVariance()
     * @return {Optional<Double>} standard deviation of a text cluster
     */
    public OptionalDouble calculateStandardDeviation(TextCluster textCluster, int thresholdSize) {

        try {
            final double variance = calculateVariance(textCluster, thresholdSize).getAsDouble();

            return OptionalDouble.of(Math.sqrt(variance));
        }
        catch (NoSuchElementException exception) {
            return OptionalDouble.empty();
        }

    }

    /**
     * Calculates the coverage percentage of how  many text blocks in a textCluster are present
     *
     * @param textCluster textCluster for which the coverage percentage is calculated
     * @return OptionalDouble containing the percentage of blocks which are assessed
     */
    public OptionalDouble calculateCoveragePercentage(TextCluster textCluster) {
        if (textCluster != null) {
            final List<TextBlock> allBlocksInCluster = textCluster.getBlocks().parallelStream().collect(toList());

            final double creditedBlocks = (double) allBlocksInCluster.stream().filter(block -> (getCreditsOfTextBlock(block).isPresent())).count();

            return OptionalDouble.of(creditedBlocks / (double) allBlocksInCluster.size());
        }
        return OptionalDouble.empty();
    }

    /**
     * Calculates the coverage percentage of a textCluster with the same score as a given text block
     *
     * @param textBlock text block for which its textClusters score coverage percentage is determined
     * @return {Optional<Double>} Optional which contains the percentage between [0.0-1.0] or empty if text block is not in a textCluster
     */
    public OptionalDouble calculateScoreCoveragePercentage(TextBlock textBlock) {
        final TextCluster textCluster = textBlock.getCluster();

        if (textCluster != null) {

            final List<TextBlock> allBlocksInCluster = textCluster.getBlocks().parallelStream().collect(toList());

            final double textBlockCredits = getCreditsOfTextBlock(textBlock).getAsDouble();

            final List<TextBlock> blocksWithSameCredit = textCluster.getBlocks()

                    .parallelStream()

                    // Get all text blocks which have the same score as the current text block
                    .filter(block -> (getCreditsOfTextBlock(block).getAsDouble() == textBlockCredits))

                    .collect(toList());
            try {
                return OptionalDouble.of(((double) blocksWithSameCredit.size()) / ((double) allBlocksInCluster.size()));
            }
            catch (NoSuchElementException exception) {
                return OptionalDouble.empty();
            }
        }

        return OptionalDouble.empty();
    }

    /**
     * Calculates the average score of a text textCluster
     *
     * @param textCluster text textCluster for which the average score should be computed
     * @return {Optional<Double>} Optional which contains the average scores or empty if there are none
     */
    public OptionalDouble calculateAverage(TextCluster textCluster) {
        if (textCluster != null) {

            final List<TextBlock> allBlocksInCluster = textCluster.getBlocks().parallelStream().collect(toList());

            final double scoringSum = allBlocksInCluster.stream().mapToDouble(block -> {
                if (getCreditsOfTextBlock((block)).isPresent()) {
                    return getCreditsOfTextBlock(block).getAsDouble();
                }
                return 0.0;
            }).sum();

            return OptionalDouble.of(scoringSum / (double) allBlocksInCluster.size());
        }
        return OptionalDouble.empty();
    }

    /**
     * Gets the max score of a text textCluster
     * @param textCluster textCluster for which the max is calculated
     * @return OptionalDouble containing the median score if present or empty
     */
    public OptionalDouble getMaxScore(TextCluster textCluster) {

        final List<TextBlock> allAssessedBlocks = getAssessedBlocks(textCluster);

        if (allAssessedBlocks.size() > 0) {
            return allAssessedBlocks.stream().mapToDouble(block -> getCreditsOfTextBlock(block).getAsDouble()).reduce(Math::max);
        }

        return OptionalDouble.empty();
    }

    /**
     * Gets the median score of a textCluster
     * @param textCluster textCluster for which the median is calculated
     * @return OptionalDouble containing the median score if present or empty
     */
    public OptionalDouble getMedianScore(TextCluster textCluster) {

        final List<TextBlock> allAssessedBlocks = getAssessedBlocks(textCluster);

        try {
            final double[] textBlockArray = allAssessedBlocks.stream().mapToDouble(block -> getCreditsOfTextBlock(block).getAsDouble()).toArray();

            Arrays.sort(textBlockArray);

            if (textBlockArray.length > 0) {
                return OptionalDouble.of(textBlockArray[(textBlockArray.length / 2) - 1]);
            }
        }
        catch (NoSuchElementException exception) {
            return OptionalDouble.empty();
        }

        return OptionalDouble.empty();
    }

    /**
     * Gets the minimum score of a text textCluster
     * @param textCluster textCluster for which the minimum is calculated
     * @return OptionalDouble containing the minimum score if present or empty
     */
    public OptionalDouble getMinimumScore(TextCluster textCluster) {

        final List<TextBlock> allAssessedBlocks = getAssessedBlocks(textCluster);
        try {
            if (allAssessedBlocks.size() > 0) {
                return allAssessedBlocks.stream().mapToDouble(block -> getCreditsOfTextBlock(block).getAsDouble()).reduce(Math::min);
            }
        }
        catch (NoSuchElementException exception) {
            return OptionalDouble.empty();
        }

        return OptionalDouble.empty();
    }

    /**
     * Returns the credits of a text block as an optional depending on wether a text block has credits
     * @param block {TextBlock} text block for which the credits are returned
     * @return {Optional<Double>} credits of the text block as Double Object in an Optional Wrapper
     */
    public OptionalDouble getCreditsOfTextBlock(TextBlock block) {
        final TextCluster textCluster = block.getCluster();

        final Map<String, Feedback> feedback = feedbackService.getFeedbackForTextExerciseInCluster(textCluster);
        if (feedback != null) {
            return OptionalDouble.of(feedback.get(block.getId()).getCredits());
        }
        return OptionalDouble.empty();
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

    /**
     * Calculates a suggested feedback score for a textblock based on its surrounding neighbors in a Text cluster
     * @param textBlock textBlock for which the score is calculated
     * @return OptionalDouble containing the score of a textblock or empty, if none present
     */
    public OptionalDouble calculateScore(TextBlock textBlock) {
        final TextCluster textCluster = textBlock.getCluster();

        final List<TextBlock> assessedBlocks = getAssessedBlocks(textCluster);

        if (getCreditsOfTextBlock(textBlock).isPresent() || assessedBlocks.size() < 1 || textCluster == null) {
            return OptionalDouble.empty();
        }

        try {
            // Get the total distance between the text block which is automatically assessed and every other assessed block in the cluster
            final double totalWeight = assessedBlocks.stream()

                    // Get the distance between the original text block and each assessed block
                    .mapToDouble(blockIterator -> (1 / Math.abs(textCluster.distanceBetweenBlocks(textBlock, blockIterator))))
                    // Sum the up to get the total distance
                    .reduce(Double::sum)
                    // Return as double
                    .getAsDouble();

            final double weightedScore = assessedBlocks.stream()

                    // Calculate the impact each assessed block should have on the final score, by determining the percental closeness of its weight compared to the other assessed
                    // blocks
                    .mapToDouble(blockIterator -> ((1 / Math.abs(textCluster.distanceBetweenBlocks(textBlock, blockIterator))) / totalWeight)
                            * getCreditsOfTextBlock(blockIterator).getAsDouble())
                    .sum();
            // Get upper range threshold for wether the score matches the expectation
            final OptionalDouble upperRange = OptionalDouble.of(calculateExpectation(textCluster).getAsDouble() + calculateStandardDeviation(textCluster, 3).getAsDouble());

            // Get lower range threshold for wether the score matches the expectation
            final OptionalDouble lowerRange = OptionalDouble.of(calculateExpectation(textCluster).getAsDouble() - calculateStandardDeviation(textCluster, 3).getAsDouble());

            // Verify if weighted score lies in range interval
            if (weightedScore < upperRange.getAsDouble() && weightedScore > lowerRange.getAsDouble()) {
                return OptionalDouble.of(weightedScore);
            }
            return OptionalDouble.empty();
        }
        catch (NoSuchElementException exception) {
            return OptionalDouble.empty();
        }
    }

    /**
     * Rounds a score to the closest given score in a text cluster
     * This workaround is needed, since no concrete grading scales are given
     * @param textBlock text block which should receive the suggested score
     * @param suggestedScore suggested score which should be rounded
     * @return OptionalDouble of the "closest" score or Empty
     */
    public OptionalDouble roundScore(TextBlock textBlock, double suggestedScore) {
        final TextCluster cluster = textBlock.getCluster();

        final List<TextBlock> allAssessedBlocks = getAssessedBlocks(cluster);

        HashSet<Double> givenScores = new HashSet<Double>();

        // Will never throw a NoSuchElementException since getAssessedBlocks() already takes care of it
        allAssessedBlocks.stream().forEach(block -> givenScores.add(getCreditsOfTextBlock(block).getAsDouble()));

        final Optional<Double> roundedScore = givenScores.stream().min(comparing(score -> Math.abs(suggestedScore - score)));

        if (roundedScore.isPresent()) {
            return OptionalDouble.of(roundedScore.get());
        }
        return OptionalDouble.empty();
    }
}
