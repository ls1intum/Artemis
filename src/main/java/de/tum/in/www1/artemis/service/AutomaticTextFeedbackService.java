package de.tum.in.www1.artemis.service;

import static java.util.stream.Collectors.toList;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

import javax.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.tum.in.www1.artemis.domain.Feedback;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.TextBlock;
import de.tum.in.www1.artemis.domain.TextCluster;
import de.tum.in.www1.artemis.domain.TextSubmission;
import de.tum.in.www1.artemis.domain.enumeration.FeedbackType;
import de.tum.in.www1.artemis.exception.NetworkingError;
import de.tum.in.www1.artemis.repository.TextBlockRepository;
import de.tum.in.www1.artemis.service.connectors.TextFeedbackValidationService;

@Service
@Profile("automaticText")
/**
 * This service creates automatic feedback for a text block
 * It is only invoked for a text exercise where automatic feedback is enabled
 */
public class AutomaticTextFeedbackService {

    // Represents the percentage which the confidence percentage needs to surpass in order to suggest automatic feedback
    // Selection of this threshold is based on a text exercise of POM SS19
    private static final double CONFIDENCE_THRESHOLD = 40.0;

    // Represents how many text blocks of the text cluster need to be assessed before automatic feedback is suggested
    private static final int FEEDBACK_THRESHOLD = 2;

    private final Logger log = LoggerFactory.getLogger(AutomaticTextFeedbackService.class);

    private final TextBlockRepository textBlockRepository;

    private final TextClusterUtilityService textClusterUtilityService;

    private final TextFeedbackValidationService textFeedbackValidationService;

    public AutomaticTextFeedbackService(TextBlockRepository textBlockRepository, TextClusterUtilityService textClusterUtilityService,
            TextFeedbackValidationService textFeedbackValidationService) {
        this.textBlockRepository = textBlockRepository;
        this.textClusterUtilityService = textClusterUtilityService;
        this.textFeedbackValidationService = textFeedbackValidationService;
    }

    /**
     * Suggest Feedback for a Submission based on the textCluster of each of the submission's text blocks.
     * For each TextBlock of the submission, this method creates feedback in form of a score and comment and validates it.
     * Otherwise, an empty Feedback Element is created for simplicity.
     * Feedback are stored inline with the provided Result object.
     * @param result Result for the Submission
     */
    @Transactional(readOnly = true)
    public void suggestFeedback(@NotNull Result result) {
        final TextSubmission textSubmission = (TextSubmission) result.getSubmission();
        final List<TextBlock> textBlocks = textBlockRepository.findAllWithEagerClusterBySubmissionId(textSubmission.getId());
        textSubmission.setBlocks(textBlocks);

        final List<Feedback> suggestedFeedback = textBlocks.stream().map(candidate -> {
            final Optional<Feedback> automaticFeedback = createFeedback(candidate, FEEDBACK_THRESHOLD, CONFIDENCE_THRESHOLD);

            if (automaticFeedback.isPresent()) {

                return automaticFeedback.get();
            }

            // Request manual feedback if we do not suggest automatic feedback
            Feedback manualFeedback = new Feedback();
            manualFeedback.setCredits(0d);
            manualFeedback.setReference(candidate.getId());
            manualFeedback.setType(FeedbackType.MANUAL);

            return manualFeedback;
        }).collect(toList());

        result.setFeedbacks(suggestedFeedback);
    }

    /**
     * Creates the score of the automatic feedback for a text block based on a normalization approach
     * @param textBlock text block for which the automatic score should be generated
     * @return double of the score
     */
    public double createScore(TextBlock textBlock) {
        final TextCluster textCluster = textBlock.getCluster();
        final List<TextBlock> feedbackPool = textClusterUtilityService.getAssessedBlocks(textCluster);

        final double totalWeight = feedbackPool.stream()

                // Get the weight between the candidate text block and every text block of the feedback pool
                .mapToDouble(blockIterator -> (1.0 / Math.abs(textCluster.distanceBetweenBlocks(textBlock, blockIterator)))).sum();

        final double weightedScore = feedbackPool.stream()

                // Calculate the impact each assessed block should have on the final score, by determining the percental closeness
                // of its weight compared to the other assessed blocks
                .mapToDouble(blockIterator -> ((1.0 / Math.abs(textCluster.distanceBetweenBlocks(textBlock, blockIterator))) / totalWeight)
                        * textClusterUtilityService.getScoreOfTextBlock(blockIterator).getAsDouble())
                .sum();

        // Round to 1 decimals as we would e.g. receive 0.999999 instead of 1.0
        return new BigDecimal(weightedScore).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    /**
     * Creates the comment of the automatic feedback for a text block based on a nearest neighbor search
     * @param textBlock text block for which the automatic feedback should be created
     * @param score score of the automatic feedback
     * @return String containing the comment or an empty string if no comment was given
     */
    public String createComment(TextBlock textBlock, double score) {
        try {
            final TextBlock nearestNeighborWithScore = textClusterUtilityService.getNearestNeighborWithScore(textBlock, score).get();
            final Optional<String> nearestComment = textClusterUtilityService.getCommentOfTextBlock(nearestNeighborWithScore);

            return nearestComment.get();
        }
        catch (NoSuchElementException exception) {

            return "";
        }
    }

    /**
     * Creates the automatic feedback for a text block
     *
     * For following cases we do not create automatic feedback for a text block:
     * 1. Text block is not part of a text cluster
     * 2. Text block already has feedback (this is an error)
     * 3. Text cluster does not have enough (= feedback_threshold) assessed text blocks yet.
     * 4. The suggested score does not lie within the expected score interval of the text cluster
     * 5. The confidence percentage of the feedback does not exceed the confidence threshold,
     *    this is an additional NLP metric considering the results of segmentation and clustering.
     *
     * If any of these cases occur an empty Optional will be returned
     *
     * @param textBlock text block for which automatic feedback is created
     * @param feedbackThreshold minimum number of assessed text blocks in the text cluster of the textBlock
     * @param confidenceThreshold confidence threshold which needs to be surpassed in order to suggest automatic feedback
     * @return Optional<Feedback> wrapping the feedback
     */
    public Optional<Feedback> createFeedback(TextBlock textBlock, int feedbackThreshold, double confidenceThreshold) {
        final TextCluster textCluster = textBlock.getCluster();
        final List<TextBlock> feedbackPool = textClusterUtilityService.getAssessedBlocks(textCluster);

        if (textCluster == null || textClusterUtilityService.getScoreOfTextBlock(textBlock).isPresent() || feedbackPool.size() < feedbackThreshold) {

            return Optional.empty();
        }

        // Begin Normalization
        final double score = createScore(textBlock);

        final double suggestedScore = textClusterUtilityService.roundScore(textBlock, score).getAsDouble();

        // Begin Nearest Neighbor
        final String suggestedComment = createComment(textBlock, suggestedScore);

        Feedback feedback = new Feedback();
        feedback.setReference(textBlock.getId());
        feedback.setCredits(suggestedScore);
        feedback.setDetailText(suggestedComment);
        feedback.setType(FeedbackType.AUTOMATIC);

        final double confidencePercentage = calculateConfidence(textBlock, feedback);

        if (confidencePercentage > confidenceThreshold && verifyScore(textBlock, score)) {

            return Optional.of(feedback);
        }

        return Optional.empty();
    }

    /**
     * Verifies whether the suggested score lies within the range of deviation from the text cluster
     * @param textBlock text block for which the feedback is given
     * @param score score which is verified for
     * @return boolean
     */
    public boolean verifyScore(TextBlock textBlock, double score) {
        try {
            final TextCluster textCluster = textBlock.getCluster();
            final OptionalDouble standardDeviation = textClusterUtilityService.calculateStandardDeviation(textCluster);
            final OptionalDouble expectedValue = textClusterUtilityService.calculateExpectedValue(textCluster);

            final double upperRange = expectedValue.getAsDouble() + standardDeviation.getAsDouble();
            final double lowerRange = expectedValue.getAsDouble() - standardDeviation.getAsDouble();

            if (score >= lowerRange && score <= upperRange) {

                return true;
            }
        }
        catch (NoSuchElementException exception) {
            log.error(exception.toString());
        }

        return false;
    }

    /**
     * Invokes the external (Python) validation service and calculates a confidence percentage
     * If the percentage exceeds the predefined threshold return true, else false
     * @param textBlock text block for which the feedback should be validated
     * @param feedback feedback which should be validated for
     * @return double
     */
    public double calculateConfidence(TextBlock textBlock, Feedback feedback) {
        try {
            final TextCluster textCluster = textBlock.getCluster();
            final List<TextBlock> references = textClusterUtilityService.filterTextCluster(textCluster, feedback.getCredits());

            // @TODO UI
            final double confidencePercentage = textFeedbackValidationService.validateFeedback(textBlock, references);

            return confidencePercentage;
        }
        catch (NetworkingError networkingError) {
            log.error(networkingError.toString());

            return 0.0;
        }
    }
}
