package de.tum.in.www1.artemis.service.compass.assessment;

import de.tum.in.www1.artemis.domain.Feedback;

import java.util.*;

public class Assessment {

    private Map<Context, List<Feedback>> contextFeedbackList; //TODO MJ replace Score with Feedback
    private Map<Context, Score> contextScoreMapping;

    public Assessment(Context context, Feedback feedback) {
        contextFeedbackList = new HashMap<>();
        contextScoreMapping = new HashMap<>();
        List<Feedback> contextAssessments = new ArrayList<>();
        contextAssessments.add(feedback);
        List<String> comments = Collections.singletonList(feedback.getText());
        Score score = new Score(feedback.getCredits(),comments,1.0);
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
        return contextFeedbackList.getOrDefault(context, new ArrayList<>());
    }

    /**
     * Add feedback for a specific context to the contextFeedbackList, recalculate metrics for the contextScoreMapping
     *
     * @param feedback   The feedback to add
     * @param context The context connected to the score
     */
    public void addFeedback(Feedback feedback, Context context) {
        List<Feedback> feedbacks = getFeedbacks(context);
        feedbacks.add(feedback);
        contextScoreMapping.put(context, calculateTotalScore(feedbacks));
    }

    /**
     * Used for statistic
     */
    public Map<Context, List<Feedback>> getContextFeedbackList() {
        return this.contextFeedbackList;
    }

    private Score calculateTotalScore(List<Feedback> feedbacks) {
        Set<String> comments = new HashSet<>();
        // sum points and save number of assessments for each unique credit number
        double credits = 0;
        Map<Double, Integer> counting = new HashMap<>();

        for (Feedback existingFeedback : feedbacks) {
            double points = existingFeedback.getCredits();

            credits += points;
            counting.put(points, counting.getOrDefault(points, 0) + 1);
            comments.add(existingFeedback.getText());
        }

        double maxCount = counting.entrySet().stream().mapToInt(Map.Entry::getValue).max().orElse(0);
        double mean = credits / feedbacks.size();
        double confidence = maxCount / feedbacks.size();
        return new Score(mean, new ArrayList<>(comments), confidence);
    }
}
