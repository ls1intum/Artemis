package de.tum.in.www1.artemis.service.compass.controller;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.tum.in.www1.artemis.domain.Feedback;
import de.tum.in.www1.artemis.service.compass.assessment.Assessment;
import de.tum.in.www1.artemis.service.compass.assessment.CompassResult;
import de.tum.in.www1.artemis.service.compass.assessment.Context;
import de.tum.in.www1.artemis.service.compass.assessment.Score;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLDiagram;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLElement;

public class AutomaticAssessmentController {

    private final Logger log = LoggerFactory.getLogger(AutomaticAssessmentController.class);

    private double totalCoverage;

    private double totalConfidence;

    /**
     * For every model element it adds the feedback (together with the context of the element) to the assessment of the corresponding similarity set. If there is no assessment for
     * the similarity set yet, it creates a new one.
     *
     * @param index                manages all assessments
     * @param elementIdFeedbackMap maps elementIds to feedbacks
     * @param model                the UML model - contains all elements with its jsonIds
     */
    public void addFeedbacksToAssessment(AssessmentIndex index, Map<String, Feedback> elementIdFeedbackMap, UMLDiagram model) {
        for (String jsonElementID : elementIdFeedbackMap.keySet()) {
            UMLElement element = model.getElementByJSONID(jsonElementID);

            if (element == null) {
                log.warn("Element with id " + jsonElementID + " could not be found in model.");
                continue;
            }

            Context context = element.getContext();
            Optional<Assessment> assessmentOptional = index.getAssessment(element.getSimilarityID());

            if (assessmentOptional.isPresent()) {
                assessmentOptional.get().addFeedback(elementIdFeedbackMap.get(jsonElementID), context);
            }
            else {
                Assessment newAssessment = new Assessment(context, elementIdFeedbackMap.get(jsonElementID));
                index.addAssessment(element.getSimilarityID(), newAssessment);
            }
        }
    }

    /**
     * Loop over all models and triggers their automatic assessment.
     *
     * @param modelIndex      manages all models
     * @param assessmentIndex manages all assessments
     */
    // TODO CZ: only assess models automatically that do not already have a complete manual assessment?
    public void assessModelsAutomatically(ModelIndex modelIndex, AssessmentIndex assessmentIndex) {

        totalCoverage = 0;
        totalConfidence = 0;

        for (UMLDiagram model : modelIndex.getModelCollection()) {

            CompassResult compassResult = assessModelAutomatically(model, assessmentIndex);

            totalCoverage += compassResult.getCoverage();
            totalConfidence += compassResult.getConfidence();

        }

        totalConfidence /= modelIndex.getModelCollectionSize();
        totalCoverage /= modelIndex.getModelCollectionSize();
    }

    /**
     * Loop over all elements of the given model, get their assessments form the assessment index and build a Compass result with them.
     *
     * @param model           the UML model which contains all the model elements
     * @param assessmentIndex manages all assessments
     * @return a Compass result built from the assessments of the model elements
     */
    public CompassResult assessModelAutomatically(UMLDiagram model, AssessmentIndex assessmentIndex) {
        double totalCount = 0;
        double missingCount = 0;

        Map<UMLElement, Score> scoreHashMap = new ConcurrentHashMap<>();

        for (UMLElement element : model.getAllModelElements()) {
            Optional<Assessment> assessmentOptional = assessmentIndex.getAssessment(element.getSimilarityID());
            totalCount++;

            if (assessmentOptional.isEmpty()) {
                missingCount++;
            }
            else {
                Score score = assessmentOptional.get().getScore(element.getContext());

                if (score == null) {
                    log.debug("Unable to find score for element " + element.getJSONElementID() + " in model " + model.getModelSubmissionId() + " with the specific context");
                }
                else {
                    scoreHashMap.put(element, score);
                }
            }
        }

        double coverage = 1;

        if (totalCount != 0) {
            coverage = (totalCount - missingCount) / totalCount;
        }
        else {
            log.warn("'totalCount' was 0. Set coverage to 1 for a CompassResult");
        }

        CompassResult compassResult = new CompassResult(scoreHashMap, coverage);

        model.setLastAssessmentCompassResult(compassResult);

        return compassResult;
    }

    public double getTotalCoverage() {
        return totalCoverage;
    }

    public double getTotalConfidence() {
        return totalConfidence;
    }
}
