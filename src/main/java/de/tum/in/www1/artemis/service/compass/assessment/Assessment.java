package de.tum.in.www1.artemis.service.compass.assessment;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import de.tum.in.www1.artemis.domain.Feedback;

public class Assessment {

    private Map<Context, List<Feedback>> contextFeedbackList;

    private Map<Context, Score> contextScoreMapping;

    public Assessment(Context context, Feedback feedback) {
        contextFeedbackList = new ConcurrentHashMap<>();
        contextScoreMapping = new ConcurrentHashMap<>();
        List<Feedback> contextAssessments = new ArrayList<>();
        contextAssessments.add(feedback);
        List<String> comments = Collections.singletonList(feedback.getText());
        Score score = new Score(feedback.getCredits(), comments, 1.0);
        contextFeedbackList.put(context, contextAssessments);
        contextScoreMapping.put(context, score);
    }

    public boolean hasContext(Context context) {
        return contextScoreMapping.containsKey(context);
    }

    public Score getScore(Context context) {
        return contextScoreMapping.get(context);
    }

    /**
     * @param context The context whose associated scores are to be returned
     * @return List of Scores added for the given context. Returns empty List when no scores are available for context
     */
    public List<Feedback> getFeedbacks(Context context) {
        if (context == null) {
            return new ArrayList<>();
        }

        return contextFeedbackList.getOrDefault(context, new ArrayList<>());
    }

    /**
     * Add feedback for a specific context to the contextFeedbackList, recalculate metrics for the contextScoreMapping. If feedback with the same ID already exists, it will be
     * replaced by the new feedback item.
     *
     * @param feedback The feedback to add
     * @param context  The context connected to the score
     */
    public void addFeedback(Feedback feedback, Context context) {
        List<Feedback> feedbacks = getFeedbacks(context);
        feedbacks.removeIf(existingFeedback -> existingFeedback.getId().equals(feedback.getId()));
        feedbacks.add(feedback);
        contextFeedbackList.put(context, feedbacks);
        contextScoreMapping.put(context, calculateTotalScore(feedbacks));
    }

    /**
     * Used for statistic
     * @return the ContextFeedBackList
     */
    public Map<Context, List<Feedback>> getContextFeedbackList() {
        return this.contextFeedbackList;
    }

    /**
     * Calculates a score for a given list of feedback elements. The score contains points, a collection of feedback comments and the confidence. Points: the credits that the
     * maximum amount of feedback elements share. Feedback comments: the collected feedback texts of all feedback elements that share the credits from above. Confidence: the
     * maximum percentage of feedback elements that share the same credits.
     *
     * @param feedbacks the list of feedback elements for which the new score should be calculated
     * @return the score containing points, a collection of feedback text and the confidence
     */
    private Score calculateTotalScore(List<Feedback> feedbacks) {
        // counts the amount of feedback elements that have the same credits assigned, i.e. maps "credits -> amount" for every unique credit number
        Map<Double, Integer> creditCount = new HashMap<>();
        // collects the feedback texts of the feedback elements that have the same credits assigned, i.e. maps "credits -> set of feedback text" for every unique credit number
        Map<Double, Set<String>> creditFeedbackText = new HashMap<>();

        for (Feedback existingFeedback : feedbacks) {
            double credits = existingFeedback.getCredits();
            creditCount.put(credits, creditCount.getOrDefault(credits, 0) + 1);

            if (existingFeedback.getText() != null) {
                Set<String> feedbackTextForCredits = creditFeedbackText.getOrDefault(credits, new HashSet<>());
                feedbackTextForCredits.add(existingFeedback.getText());
                creditFeedbackText.put(credits, feedbackTextForCredits);
            }
        }

        double maxCount = creditCount.values().stream().mapToInt(i -> i).max().orElse(0);
        double confidence = maxCount / feedbacks.size();
        double maxCountCredits = creditCount.entrySet().stream().filter(entry -> entry.getValue() == maxCount).map(Map.Entry::getKey).findFirst().orElse(0.0);
        Set<String> feedbackTextForMaxCountCredits = creditFeedbackText.getOrDefault(maxCountCredits, new HashSet<>());

        return new Score(maxCountCredits, new ArrayList<>(feedbackTextForMaxCountCredits), confidence);
    }
}
