package de.tum.in.www1.artemis.service.compass.assessment;

import java.util.List;

public class Score {

    private double points;

    private double confidence;

    private List<String> comments;

    public Score(double points, List<String> comments, double confidence) {
        this.points = points;
        this.comments = comments;
        this.confidence = confidence;
    }

    public double getPoints() {
        return points;
    }

    public List<String> getComments() {
        return comments;
    }

    /**
     * Returns the confidence of the score. The confidence is the percentage indicating how certain Compass is about the points and comments of the Score.
     *
     * @return the confidence of the score
     */
    public double getConfidence() {
        return confidence;
    }
}
