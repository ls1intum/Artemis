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
/**
 * This class provides utility methods for working with text clusters, their text blocks and the correspondingly given feedback
 */
public class TextClusterUtilityService {

    private final FeedbackService feedbackService;

    public TextClusterUtilityService(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    /**
     * Calculates the variance of a given text textCluster if the number of assessed text block in the textCluster exceeds the variance thresehold
     * @param textCluster textCluster for which a variance is calculated
     * @return OptionalDouble wrapping the variance of a textCluster
     */
    public OptionalDouble calculateVariance(TextCluster textCluster) {
        final List<TextBlock> allAssessedBlocks = getAssessedBlocks(textCluster);
        final OptionalDouble expectedValue = calculateExpectedValue(textCluster);
        final HashSet<Double> givenScores = getGivenScores(textCluster);
        final HashMap<Double, Double> scoreProbability = new HashMap<>();

        if (allAssessedBlocks.size() == 0 || expectedValue.isEmpty()) {
            return OptionalDouble.empty();
        }
        // \operatorname {Var} (X)=\sum _{i=1}^{n}p_{i}\cdot (x_{i}-\mu )^{2}

        // This equals to defining \p_{i}
        for (Double score : givenScores) {
            scoreProbability.put(score, ((double) getNumberOfScoreOccurrence(textCluster, score)) / ((double) allAssessedBlocks.size()));
        }

        final double variance = getGivenScores(textCluster).stream()

                // Equals to \p_{i}\cdot (x_{i}-\mu )^{2}
                .map(scoreIterator -> (Math.pow((scoreIterator - expectedValue.getAsDouble()), 2) * scoreProbability.get(scoreIterator)))

                // Equals to \sum _{i=1}^{n}p_{i}\cdot (x_{i}-\mu )^{2}
                .reduce(0.0, Double::sum);

        return OptionalDouble.of(variance);
    }

    /**
     * Calculates the expected value in a text textCluster
     * @param textCluster textCluster for which the expected value is calculated
     * @return OptionalDouble wrapping the expected value of a text cluster
     */
    public OptionalDouble calculateExpectedValue(TextCluster textCluster) {
        final List<TextBlock> assessedTextBlocks = getAssessedBlocks(textCluster);

        if (assessedTextBlocks.size() == 0) {
            return OptionalDouble.empty();
        }

        // \operatorname {E} [X]=\sum _{i=1}^{k}x_{i}\,p_{i}=x_{1}p_{1}+x_{2}p_{2}+\cdots +x_{k}p_{k}.
        final OptionalDouble expectedValue = OptionalDouble.of(
                assessedTextBlocks.stream().mapToDouble(blockIterator -> (1.0 / (double) assessedTextBlocks.size()) * (getScoreOfTextBlock(blockIterator).getAsDouble())).sum());

        return expectedValue;
    }

    /**
     * Calculates the standard deviation of a text textCluster
     * @param textCluster textCluster for which the standard deviation is calculated
     * @return OptionalDouble standard deviation of a text cluster
     */
    public OptionalDouble calculateStandardDeviation(TextCluster textCluster) {
        final OptionalDouble variance = calculateVariance(textCluster);

        if (variance.isPresent()) {
            return OptionalDouble.of(Math.sqrt(variance.getAsDouble()));
        }

        return OptionalDouble.empty();
    }

    /**
     * Calculates the coverage percentage of a textCluster with the same score as a given text block
     * @param textBlock text block for which its textClusters score coverage percentage is determined
     * @return OptionalDouble Optional which contains the percentage between [0.0-1.0] or empty if text block is not in a textCluster
     */
    public OptionalDouble calculateScoreCoveragePercentage(TextBlock textBlock) {
        final TextCluster textCluster = textBlock.getCluster();
        final List<TextBlock> assessedBlocks = getAssessedBlocks(textCluster);

        if (textCluster == null || getScoreOfTextBlock(textBlock).isEmpty() || assessedBlocks.size() == 0) {
            return OptionalDouble.empty();
        }

        final double score = getScoreOfTextBlock(textBlock).getAsDouble();

        final List<TextBlock> blocksWithSameScore = assessedBlocks.stream()

                .filter(block -> (getScoreOfTextBlock(block).getAsDouble() == score)).collect(toList());

        return OptionalDouble.of(((double) blocksWithSameScore.size()) / ((double) assessedBlocks.size()));
    }

    /**
     * Gets the number of occurrences of an input score inside a text cluster
     * @param textCluster text cluster which serves as reference
     * @param score score which is looked for
     * @return int indicating the number of times the score appears in the text cluster
     */
    public int getNumberOfScoreOccurrence(TextCluster textCluster, double score) {
        final int numberOfOccurrence = getAssessedBlocks(textCluster).stream().filter(blockIterator -> (getScoreOfTextBlock(blockIterator).getAsDouble()) == score)
                .collect(toList()).size();

        return numberOfOccurrence;
    }

    /**
     * Returns each uniquely given score inside a text cluster as a Hashset
     * @param textCluster text cluster for which each uniquely given score should be returned
     * @return HashSet<Double> containing the given scores of a text cluster
     */
    public HashSet<Double> getGivenScores(TextCluster textCluster) {
        final HashSet<Double> result = new HashSet<>();
        final List<TextBlock> assessedBlocks = getAssessedBlocks(textCluster);

        assessedBlocks.stream().forEach(blockIterator -> result.add(getScoreOfTextBlock(blockIterator).getAsDouble()));

        return result;
    }

    /**
     * Returns the credits of a text block as an optional depending on wether a text block has credits
     * @param block {TextBlock} text block for which the credits are returned
     * @return OptionalDouble wrapping the score
     */
    public OptionalDouble getScoreOfTextBlock(TextBlock block) {
        final TextCluster textCluster = block.getCluster();
        final Map<String, Feedback> feedback = feedbackService.getFeedbackForTextExerciseInCluster(textCluster);

        if (feedback == null || feedback.get(block.getId()) == null || feedback.get(block.getId()).getCredits() == null) {
            return OptionalDouble.empty();
        }

        return OptionalDouble.of(feedback.get(block.getId()).getCredits());
    }

    /**
     * Returns the comment of a text block
     * @param block {TextBlock} text block for which the comment should be returned
     * @return Optional<String> wrapping the comment
     */
    public Optional<String> getCommentOfTextBlock(TextBlock block) {
        final TextCluster textCluster = block.getCluster();
        final Map<String, Feedback> feedback = feedbackService.getFeedbackForTextExerciseInCluster(textCluster);

        if (feedback == null || feedback.get(block.getId()) == null || feedback.get(block.getId()).getDetailText() == null) {
            return Optional.empty();
        }

        return Optional.of(feedback.get(block.getId()).getDetailText());
    }

    /**
     * Returns all assessed block in a text textCluster as a List
     * @param textCluster textCluster for which all assessed blocks should be returned
     * @return List<Textblock> all assessed text blocks
     */
    public List<TextBlock> getAssessedBlocks(TextCluster textCluster) {
        List<TextBlock> assessedBlocks = new ArrayList<>();
        if (textCluster != null) {
            assessedBlocks = textCluster.getBlocks().stream().filter(block -> getScoreOfTextBlock(block).isPresent()).collect(toList());
        }

        return assessedBlocks;
    }

    /**
     * Rounds a score to the closest given score in a text cluster
     * This workaround is needed, since no concrete grading scales are given
     * @param textBlock text block which should receive the suggested score
     * @param suggestedScore suggested score which should be rounded
     * @return OptionalDouble of the "closest" score or Empty
     */
    public OptionalDouble roundScore(TextBlock textBlock, double suggestedScore) {
        final TextCluster textCluster = textBlock.getCluster();
        final HashSet<Double> givenScores = getGivenScores(textCluster);

        if (givenScores.size() == 0 || textCluster == null) {
            return OptionalDouble.empty();
        }

        final OptionalDouble roundedScore = OptionalDouble.of(givenScores.stream().min(comparing(score -> Math.abs(suggestedScore - score))).get());

        return roundedScore;
    }

    /**
     * Get the nearest neighbor of a text block inside a text cluster with a given score
     * @param textBlock text block for which the nearest neighbor should be searched
     * @param score score which the nearest neighbor should have
     * @return Optional<TextBlock> wrapping the nearest neighbor if present
     */
    public Optional<TextBlock> getNearestNeighborWithScore(TextBlock textBlock, double score) {
        final TextCluster textCluster = textBlock.getCluster();

        if (textCluster == null) {
            return Optional.empty();
        }

        // Filter out the the text block itself as a text block's distance to itself will always be 0 and therefore the minimum
        final Optional<TextBlock> nearestNeighorWithScore = getAssessedBlocks(textCluster).stream()
                .filter(blockIterator -> getScoreOfTextBlock(blockIterator).getAsDouble() == score && blockIterator.getId() != textBlock.getId())
                .min(comparing(element -> Math.abs(textCluster.distanceBetweenBlocks(textBlock, element))));

        return nearestNeighorWithScore;
    }

    /**
     * Filters the text cluster of a text block with all text blocks which have received a certain score
     * @param textCluster text cluster which should be filtered
     * @param score score which should be filtered after
     * @return List<TextBlock> text blocks which have all received the given score
     */
    public List<TextBlock> filterTextCluster(TextCluster textCluster, double score) {
        final List<TextBlock> assessedBlocks = getAssessedBlocks(textCluster);
        List<TextBlock> filteredTextCluster = new ArrayList<>();

        if (assessedBlocks.size() > 0) {
            filteredTextCluster = assessedBlocks.stream().filter(block -> getScoreOfTextBlock(block).getAsDouble() == score).collect(toList());
        }

        return filteredTextCluster;
    }
}
