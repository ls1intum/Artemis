package de.tum.in.www1.artemis.service.compass.assessment;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import de.tum.in.www1.artemis.domain.Feedback;

/**
 * An assessment for elements of the same similarity set. The assessment contains feedback items from manual assessments and a score resulting from the manual feedback.
 */
public class SimilaritySetAssessment {

    private Set<Feedback> feedbackItems;

    private Score score;

    public SimilaritySetAssessment(Feedback feedback) {
        feedbackItems = ConcurrentHashMap.newKeySet();
        feedbackItems.add(feedback);
        List<String> comments = Collections.singletonList(feedback.getText());
        score = new Score(feedback.getCredits(), comments, 1.0);
    }

    /**
     * Get the score contained in the assessment. The score contains points, comments and the confidence of the assessment.
     *
     * @return the score of the assessment
     */
    public Score getScore() {
        return score;
    }

    /**
     * Get the list of feedback items contained in the assessment.
     *
     * @return the list of feedback items of the assessment
     */
    public List<Feedback> getFeedbackList() {
        return new ArrayList<>(feedbackItems);
    }

    /**
     * Add feedback to the list of feedback items and recalculate the score of the assessment. If feedback with the same ID already exists, it will be replaced by the new feedback
     * item.
     *
     * @param feedback The feedback to add
     */
    public void addFeedback(Feedback feedback) {
        feedbackItems.removeIf(existingFeedback -> existingFeedback.getId().equals(feedback.getId()));
        feedbackItems.add(feedback);
        score = calculateTotalPoints(feedbackItems);
    }

    /**
     * Calculates a score for a given list of feedback elements. The score contains points, a collection of feedback comments and the confidence. Points: the credits that the
     * maximum amount of feedback elements share. Feedback comments: the collected feedback texts of all feedback elements that share the credits from above. Confidence: the
     * maximum percentage of feedback elements that share the same credits.
     *
     * @param feedbackItems the list of feedback elements for which the new score should be calculated
     * @return the score containing points, a collection of feedback text and the confidence
     */
    private Score calculateTotalPoints(Set<Feedback> feedbackItems) {
        // counts the amount of feedback elements that have the same credits assigned, i.e. maps "credits -> amount" for every unique credit number
        Map<Double, Integer> creditCount = new HashMap<>();
        // collects the feedback texts of the feedback elements that have the same credits assigned, i.e. maps "credits -> set of feedback text" for every unique credit number
        Map<Double, Set<String>> creditFeedbackText = new HashMap<>();

        for (Feedback existingFeedback : feedbackItems) {
            double credits = existingFeedback.getCredits();
            creditCount.put(credits, creditCount.getOrDefault(credits, 0) + 1);

            if (existingFeedback.getText() != null) {
                Set<String> feedbackTextForCredits = creditFeedbackText.getOrDefault(credits, new HashSet<>());
                feedbackTextForCredits.add(existingFeedback.getText());
                creditFeedbackText.put(credits, feedbackTextForCredits);
            }
        }

        double maxCount = creditCount.values().stream().mapToInt(i -> i).max().orElse(0);
        double confidence = maxCount / feedbackItems.size();
        double maxCountCredits = creditCount.entrySet().stream().filter(entry -> entry.getValue() == maxCount).map(Map.Entry::getKey).findFirst().orElse(0.0);
        Set<String> feedbackTextForMaxCountCredits = creditFeedbackText.getOrDefault(maxCountCredits, new HashSet<>());

        return new Score(maxCountCredits, new ArrayList<>(feedbackTextForMaxCountCredits), confidence);
    }
}
