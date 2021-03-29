package de.tum.in.www1.artemis.service.compass.assessment;

import java.io.Serializable;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.tum.in.www1.artemis.service.compass.grade.Grade;

public class CompassResult implements Grade, Serializable {

    private final Logger log = LoggerFactory.getLogger(Grade.class);

    private Map<String, Score> elementScoreMapping;

    private Map<String, String> jsonIdCommentsMapping;

    private Map<String, Double> jsonIdPointsMapping;

    private double points;

    private double confidence;

    private double coverage;

    /**
     * to make mockito happy
     */
    public CompassResult() {
    }

    public CompassResult(Map<String, Score> elementScoreMapping, double coverage) {
        jsonIdCommentsMapping = new ConcurrentHashMap<>();
        jsonIdPointsMapping = new ConcurrentHashMap<>();

        this.elementScoreMapping = elementScoreMapping;
        this.coverage = coverage;

        buildValues();
    }

    private void buildValues() {
        for (Score score : elementScoreMapping.values()) {
            if (score == null) {
                confidence += 1;
                log.error("This should never ever happen but for some reason score in CompassResult buildValues is null");
                continue;
            }
            points += score.getPoints();
            confidence += score.getConfidence();
        }

        if (!elementScoreMapping.isEmpty()) {
            confidence /= elementScoreMapping.size();
        }
    }

    private void buildMapping() {
        for (Map.Entry<String, Score> entry : elementScoreMapping.entrySet()) {
            if (entry.getValue() == null) {
                log.error("This should never ever happen but for some reason score in CompassResult buildMapping is null");
                continue;
            }

            String elementFeedbackText = "";
            List<String> comments = entry.getValue().getComments();
            if (comments != null) {
                // get the longest of the given comments as feedback text - we assume that a longer text means more information/feedback for the student
                elementFeedbackText = comments.stream().filter(Objects::nonNull).max(Comparator.comparingInt(String::length)).orElse("");
            }

            jsonIdCommentsMapping.put(entry.getKey(), elementFeedbackText);
            jsonIdPointsMapping.put(entry.getKey(), entry.getValue().getPoints());
        }
    }

    /**
     * @return number of elements for which a score exists
     */
    public int entitiesCovered() {
        return elementScoreMapping.size();
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
        if (jsonIdCommentsMapping.isEmpty()) {
            buildMapping();
        }
        return jsonIdCommentsMapping;
    }

    @Override
    public Map<String, Double> getJsonIdPointsMapping() {
        if (jsonIdPointsMapping.isEmpty()) {
            buildMapping();
        }
        return jsonIdPointsMapping;
    }
}
