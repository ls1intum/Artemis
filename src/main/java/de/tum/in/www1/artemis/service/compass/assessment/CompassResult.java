package de.tum.in.www1.artemis.service.compass.assessment;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.tum.in.www1.artemis.service.compass.grade.Grade;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLElement;

public class CompassResult implements Grade {

    private final Logger log = LoggerFactory.getLogger(Grade.class);

    private Map<UMLElement, Score> elementScoreMapping;

    private Map<String, String> jsonIdCommentsMapping;

    private Map<String, Double> jsonIdPointsMapping;

    private double points;

    private double confidence;

    private double coverage;

    public CompassResult(Map<UMLElement, Score> elementScoreMapping, double coverage) {
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
        for (Map.Entry<UMLElement, Score> entry : elementScoreMapping.entrySet()) {
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

            jsonIdCommentsMapping.put(entry.getKey().getJSONElementID(), elementFeedbackText);
            jsonIdPointsMapping.put(entry.getKey().getJSONElementID(), entry.getValue().getPoints());
        }
    }

    /**
     * Process a list of results to build a new result out of it
     *
     * @param compassResultList a list of results to be contained in the new result
     * @param coverage          the coverage is directly reused for the returned result
     * @return the calculated result
     */
    public static CompassResult buildResultFromResultList(List<CompassResult> compassResultList, double coverage) {
        Map<UMLElement, Score> newScoreMapping = new ConcurrentHashMap<>();

        for (CompassResult compassResult : compassResultList) {
            newScoreMapping.putAll(compassResult.elementScoreMapping);
        }

        return new CompassResult(newScoreMapping, coverage);
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
