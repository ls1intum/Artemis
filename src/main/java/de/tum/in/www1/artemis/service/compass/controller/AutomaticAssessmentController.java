package de.tum.in.www1.artemis.service.compass.controller;

import de.tum.in.www1.artemis.service.compass.assessment.Assessment;
import de.tum.in.www1.artemis.service.compass.assessment.CompassResult;
import de.tum.in.www1.artemis.service.compass.assessment.Context;
import de.tum.in.www1.artemis.service.compass.assessment.Score;
import de.tum.in.www1.artemis.service.compass.umlmodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AutomaticAssessmentController {

    private final Logger log = LoggerFactory.getLogger(AutomaticAssessmentController.class);

    private double totalCoverage;
    private double totalConfidence;

    /**
     * Add a score to an assessment, creates a new assessment if it does not exists
     *
     * @param index manages all assessments
     * @param scoreHashMap maps elementIds to scores
     * @param model the UML model - contains all elements with its corresponding jsonIds
     * @throws IOException if the score for the element is null
     */
    public void addScoresToAssessment(AssessmentIndex index, Map<String, Score> scoreHashMap, UMLModel model) throws IOException {

        for (String jsonElementID : scoreHashMap.keySet()) {
            UMLElement element = model.getElementByJSONID(jsonElementID);

            if (element == null) {
                throw new IOException("Score for element was not fount");
            }

            Context context = element.getContext();
            Assessment assessment = index.getAssessment(element);

            if (assessment == null) {
                assessment = new Assessment(context, scoreHashMap.get(jsonElementID));
                index.addAssessment(element.getElementID(), assessment);
            } else {
                assessment.addScore(scoreHashMap.get(jsonElementID), context);
            }
        }
    }


    /**
     * Loops over all models and triggers their automatic assessments
     *
     * @param modelIndex manages all models
     * @param assessmentIndex manages all assessments
     */
    public void assessModelsAutomatically(ModelIndex modelIndex, AssessmentIndex assessmentIndex) {

        totalCoverage = 0;
        totalConfidence = 0;

        for (UMLModel model : modelIndex.getModelCollection()) {

            CompassResult compassResult = assessModelAutomatically(model, assessmentIndex);

            totalCoverage += compassResult.getCoverage();
            totalConfidence += compassResult.getConfidence();

        }

        totalConfidence /= modelIndex.getModelCollectionSize();
        totalCoverage /= modelIndex.getModelCollectionSize();
    }

    /**
     * Loop over all elements of a model, get their assessments and build a result with them
     *
     * @param model the UML model which contains all the model elements
     * @param assessmentIndex manages all assessments
     * @return a result
     */
    public CompassResult assessModelAutomatically(UMLModel model, AssessmentIndex assessmentIndex) {
        List<CompassResult> compassResultList = new ArrayList<>();

        double totalCount = 0;
        double missingCount = 0;

        for (UMLClass element : model.getConnectableList()) {
            CompassResult compassResult = assessConnectable(element, assessmentIndex);
            compassResultList.add(compassResult);

            int classCount = element.getElementCount();
            totalCount += classCount;
            missingCount += classCount - compassResult.entitiesCovered();
        }

        Map<UMLElement, Score> scoreHashMap = new HashMap<>();

        for (UMLRelation relation : model.getRelationList()) {
            Assessment assessment = assessmentIndex.getAssessment(relation);
            totalCount++;

            if (assessment == null) {
                missingCount++;
            } else {
                Score score = assessment.getScore(relation.getContext());
                if (score == null) {
                    log.debug("Unable to find score for relation " + relation.getJSONElementID() + " in model " + model.getModelID()
                        + " with the specific context");
                } else {
                    scoreHashMap.put(relation, score);
                }
            }
        }

        double coverage = (totalCount - missingCount) / totalCount;

        compassResultList.add(new CompassResult(scoreHashMap, coverage));

        CompassResult compassResult = CompassResult.buildResultFromResultList(compassResultList, coverage);

        model.setLastAssessmentCompassResult(compassResult);

        return compassResult;
    }

    private CompassResult assessConnectable(UMLClass umlClass, AssessmentIndex index) {
        HashMap<UMLElement, Score> scoreHashMap = new HashMap<>();

        int missing = 0;

        Context childContext = new Context(umlClass.getElementID());

        for (UMLAttribute attribute : umlClass.getAttributeList()) {
            Assessment assessment = index.getAssessment(attribute);

            if (assessment == null) {
                missing++;
            } else if (assessment.hasContext(childContext)) {
                Score score = assessment.getScore(childContext);

                if (score == null) {
                    log.warn("Unable to find score for attribute " + attribute.getJSONElementID());
                } else {
                    scoreHashMap.put(attribute, score);
                }
            }
        }

        for (UMLMethod method : umlClass.getMethodList()) {
            Assessment assessment = index.getAssessment(method);

            if (assessment == null) {
                missing++;
            } else if (assessment.hasContext(childContext)) {
                Score score = assessment.getScore(childContext);

                if (score == null) {
                    log.warn("Unable to find score for method " + method.getJSONElementID());
                } else {
                    scoreHashMap.put(method, score);
                }
            }
        }

        Assessment assessment = index.getAssessment(umlClass);

        if (assessment == null) {
            missing++;
        } else {
            Score score = assessment.getScore(umlClass.getContext());

            if (score == null) {
                log.debug("Unable to find score for class " + umlClass.getJSONElementID() + " with the specific context");
            } else {
                scoreHashMap.put(umlClass, score);
            }
        }

        double totalCount = umlClass.getElementCount();
        double coverage = (totalCount - missing) / totalCount;

        return new CompassResult(scoreHashMap, coverage);
    }


    public double getTotalCoverage() {
        return totalCoverage;
    }

    public double getTotalConfidence() {
        return totalConfidence;
    }

}
