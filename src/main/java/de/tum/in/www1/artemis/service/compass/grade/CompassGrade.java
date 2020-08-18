package de.tum.in.www1.artemis.service.compass.grade;

import java.util.Map;

public class CompassGrade implements Grade {

    private final Map<String, String> jsonIdCommentsMapping;

    private final Map<String, Double> jsonIdPointsMapping;

    private final double points;

    private final double confidence;

    private final double coverage;

    public CompassGrade(double coverage, double confidence, double points, Map<String, String> jsonIdCommentsMapping, Map<String, Double> jsonIdPointsMapping) {
        this.coverage = coverage;
        this.confidence = confidence;
        this.points = points;
        this.jsonIdCommentsMapping = jsonIdCommentsMapping;
        this.jsonIdPointsMapping = jsonIdPointsMapping;
    }

    @Override
    public double getPoints() {
        return points;
    }

    @Override
    public double getConfidence() {
        return confidence;
    }

    @Override
    public double getCoverage() {
        return coverage;
    }

    @Override
    public Map<String, String> getJsonIdCommentsMapping() {
        return jsonIdCommentsMapping;
    }

    @Override
    public Map<String, Double> getJsonIdPointsMapping() {
        return jsonIdPointsMapping;
    }
}
