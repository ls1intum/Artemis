package de.tum.in.www1.artemis.service.compass.assessment;

import java.util.*;

public class Assessment {
    private Map <Context, List <Score>> contextScoreList;
    private Map <Context, Score> contextScoreMapping;

    public Assessment(Context context, Score score) {
        contextScoreList = new HashMap<>();
        contextScoreMapping = new HashMap<>();

        List<Score> contextAssessments = new ArrayList<>();
        contextAssessments.add(score);

        contextScoreList.put(context, contextAssessments);
        contextScoreMapping.put(context, score);
    }

    public boolean hasContext (Context context) {
        return contextScoreMapping.containsKey(context);
    }

    public Score getScore(Context context) {
        return contextScoreMapping.get(context);
    }

    public void addScore(Score score, Context context) {
        HashSet<String> comments = new HashSet<>();
        List<Score> scoreList = contextScoreList.computeIfAbsent(context, k -> new ArrayList<>());

        scoreList.add(score);

        // sum points and save number of assessments for each unique credit number
        double credits = 0;
        HashMap<Double, Integer> counting = new HashMap<>();

        for (Score existingScores : scoreList) {
            double points = existingScores.getPoints();

            credits += points;
            counting.put(points, counting.getOrDefault(points, 0) + 1);
            comments.addAll(existingScores.getComments());
        }

        double maxCount = counting.entrySet().stream().mapToInt(Map.Entry::getValue).max().orElse(0);

        // calculate the mean amount of points
        credits /= scoreList.size();

        // calculate the confidence for this value
        double confidence = maxCount / scoreList.size();

        contextScoreMapping.put(context, new Score(credits, new ArrayList<>(comments), confidence));
    }

    /**
     * Used for statistic
     */
    public Map<Context, List<Score>> getContextScoreList() {
        return this.contextScoreList;
    }
}
