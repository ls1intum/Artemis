package de.tum.in.www1.artemis.service.compass.assessment;

import java.util.*;

public class Assessment {

    private Map<Context, List<Score>> contextScoreList; //TODO own object for Score in List with assessment specific information like resultId/modelID ?!
    private Map<Context, Score> contextScoreMapping;

    public Assessment(Context context, Score score) {
        contextScoreList = new HashMap<>();
        contextScoreMapping = new HashMap<>();

        List<Score> contextAssessments = new ArrayList<>();
        contextAssessments.add(score);

        contextScoreList.put(context, contextAssessments);
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
    public List<Score> getScores(Context context) {
        return contextScoreList.getOrDefault(context, new ArrayList<>());
    }

    /**
     * Add score for a specific context to the contextScoreList, recalculate metrics for the contextScoreMapping
     *
     * @param score   The score to add
     * @param context The context connected to the score
     */
    public void addScore(Score score, Context context) {
        List<Score> scores = getScores(context);
        scores.add(score);
        contextScoreMapping.put(context, calculateTotalScore(scores));
    }

    /**
     * Used for statistic
     */
    public Map<Context, List<Score>> getContextScoreList() {
        return this.contextScoreList;
    }

    private Score calculateTotalScore(List<Score> scores) {
        HashSet<String> comments = new HashSet<>();
        // sum points and save number of assessments for each unique credit number
        double credits = 0;
        HashMap<Double, Integer> counting = new HashMap<>();

        for (Score existingScores : scores) {
            double points = existingScores.getPoints();

            credits += points;
            counting.put(points, counting.getOrDefault(points, 0) + 1);
            comments.addAll(existingScores.getComments());
        }

        double maxCount = counting.entrySet().stream().mapToInt(Map.Entry::getValue).max().orElse(0);

        // calculate the mean amount of points
        credits /= scores.size();
        double confidence = maxCount / scores.size();
        return new Score(credits, new ArrayList<>(comments), confidence);
    }
}
