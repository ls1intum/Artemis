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

    double getConfidence() {
        return confidence;
    }
}
