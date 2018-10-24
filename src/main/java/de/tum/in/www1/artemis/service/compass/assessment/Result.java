package de.tum.in.www1.artemis.service.compass.assessment;

import de.tum.in.www1.artemis.service.compass.grade.Grade;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class Result implements Grade {

    private final Logger log = LoggerFactory.getLogger(Grade.class);

    private Map<UMLElement, Score> elementScoreMapping;
    private Map<String, String> jsonIdCommentsMapping;
    private Map<String, Double> jsonIdPointsMapping;

    private double points;
    private double confidence;
    private double coverage;

    public Result(Map<UMLElement, Score> elementScoreMapping, double coverage) {
        jsonIdCommentsMapping = new HashMap<>();
        jsonIdPointsMapping = new HashMap<>();

        this.elementScoreMapping = elementScoreMapping;
        this.coverage = coverage;

        buildValues();
    }

    private void buildValues () {
        for (Score score : elementScoreMapping.values()) {
            if (score == null) {
                confidence += 1;
                log.error("This should never ever happen but for some reason score in Result buildValues is null");
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
        for (Map.Entry<UMLElement, Score> entry: elementScoreMapping.entrySet()) {
            if (entry.getValue() == null) {
                log.error("This should never ever happen but for some reason score in Result buildMapping is null");
                continue;
            }

            StringBuilder elementComments = new StringBuilder();
            Iterator<String> iterator = entry.getValue().getComments().iterator();

            while(iterator.hasNext()) {
                elementComments.append(iterator.next());

                if (iterator.hasNext()) {
                    elementComments.append("\n\n");
                }
            }
            jsonIdCommentsMapping.put(entry.getKey().getJSONElementID(), elementComments.toString());
            jsonIdPointsMapping.put(entry.getKey().getJSONElementID(), entry.getValue().getPoints());
        }
    }

    /**
     * Process a list of results to build a new result out of it
     *
     * @param resultList a list of results to be contained in the new result
     * @param coverage the coverage is directly reused for the returned result
     * @return the calculated result
     */
    public static Result buildResultFromResultList (List<Result> resultList, double coverage) {
        HashMap<UMLElement, Score> newScoreMapping = new HashMap<>();

        for (Result result : resultList) {
            newScoreMapping.putAll(result.elementScoreMapping);
        }

        return new Result(newScoreMapping, coverage);
    }

    /**
     *
     * @return number of elements for which a score exists
     */
    public int entitiesCovered() {
        return elementScoreMapping.size();
    }

    @Override
    public double getPoints() { return points; }

    @Override
    public double getConfidence() { return confidence; }

    @Override
    public double getCoverage () { return coverage; }

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
