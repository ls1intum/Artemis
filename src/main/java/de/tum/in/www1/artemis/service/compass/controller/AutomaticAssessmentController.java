package de.tum.in.www1.artemis.service.compass.controller;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.tum.in.www1.artemis.domain.Feedback;
import de.tum.in.www1.artemis.service.compass.assessment.Assessment;
import de.tum.in.www1.artemis.service.compass.assessment.CompassResult;
import de.tum.in.www1.artemis.service.compass.assessment.Context;
import de.tum.in.www1.artemis.service.compass.assessment.Score;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLElement;
import de.tum.in.www1.artemis.service.compass.umlmodel.classdiagram.*;

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
     * @throws IOException if the score for the element is null
     */
    public void addFeedbacksToAssessment(AssessmentIndex index, Map<String, Feedback> elementIdFeedbackMap, UMLClassDiagram model) throws IOException {
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
     * Loops over all models and triggers their automatic assessments
     *
     * @param modelIndex      manages all models
     * @param assessmentIndex manages all assessments
     */
    // TODO CZ: only assess models automatically that do not already have a complete manual assessment?
    public void assessModelsAutomatically(ModelIndex modelIndex, AssessmentIndex assessmentIndex) {

        totalCoverage = 0;
        totalConfidence = 0;

        for (UMLClassDiagram model : modelIndex.getModelCollection()) {

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
     * @param model           the UML model which contains all the model elements
     * @param assessmentIndex manages all assessments
     * @return a result
     */
    public CompassResult assessModelAutomatically(UMLClassDiagram model, AssessmentIndex assessmentIndex) {
        List<CompassResult> compassResultList = new ArrayList<>();

        double totalCount = 0;
        double missingCount = 0;

        for (UMLClass element : model.getClassList()) {
            CompassResult compassResult = assessConnectable(element, assessmentIndex);
            compassResultList.add(compassResult);

            int classCount = element.getElementCount();
            totalCount += classCount;
            missingCount += classCount - compassResult.entitiesCovered();
        }

        Map<UMLElement, Score> scoreHashMap = new ConcurrentHashMap<>();

        // TODO CZ: combine iterating over relations and packages
        for (UMLClassRelationship relation : model.getAssociationList()) {
            Optional<Assessment> assessmentOptional = assessmentIndex.getAssessment(relation.getSimilarityID());
            totalCount++;

            if (!assessmentOptional.isPresent()) {
                missingCount++;
            }
            else {
                Score score = assessmentOptional.get().getScore(relation.getContext());
                if (score == null) {
                    log.debug("Unable to find score for relation " + relation.getJSONElementID() + " in model " + model.getModelSubmissionId() + " with the specific context");
                }
                else {
                    scoreHashMap.put(relation, score);
                }
            }
        }

        // TODO CZ: combine iterating over relations and packages
        for (UMLPackage umlPackage : model.getPackageList()) {
            Optional<Assessment> assessmentOptional = assessmentIndex.getAssessment(umlPackage.getSimilarityID());
            totalCount++;

            if (!assessmentOptional.isPresent()) {
                missingCount++;
            }
            else {
                Score score = assessmentOptional.get().getScore(umlPackage.getContext());
                if (score == null) {
                    log.debug("Unable to find score for package " + umlPackage.getJSONElementID() + " in model " + model.getModelSubmissionId() + " with the specific context");
                }
                else {
                    scoreHashMap.put(umlPackage, score);
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

        compassResultList.add(new CompassResult(scoreHashMap, coverage));

        CompassResult compassResult = CompassResult.buildResultFromResultList(compassResultList, coverage);

        model.setLastAssessmentCompassResult(compassResult);

        return compassResult;
    }

    private CompassResult assessConnectable(UMLClass umlClass, AssessmentIndex index) {
        Map<UMLElement, Score> scoreHashMap = new ConcurrentHashMap<>();

        int missing = 0;

        Context childContext = new Context(umlClass.getSimilarityID());

        for (UMLAttribute attribute : umlClass.getAttributes()) {
            Optional<Assessment> assessmentOptional = index.getAssessment(attribute.getSimilarityID());

            if (!assessmentOptional.isPresent()) {
                missing++;
            }
            else if (assessmentOptional.get().hasContext(childContext)) {
                Score score = assessmentOptional.get().getScore(childContext);
                if (score == null) {
                    log.warn("Unable to find score for attribute " + attribute.getJSONElementID());
                }
                else {
                    scoreHashMap.put(attribute, score);
                }
            }
        }

        for (UMLMethod method : umlClass.getMethods()) {
            Optional<Assessment> assessmentOptional = index.getAssessment(method.getSimilarityID());

            if (!assessmentOptional.isPresent()) {
                missing++;
            }
            else if (assessmentOptional.get().hasContext(childContext)) {
                Score score = assessmentOptional.get().getScore(childContext);

                if (score == null) {
                    log.warn("Unable to find score for method " + method.getJSONElementID());
                }
                else {
                    scoreHashMap.put(method, score);
                }
            }
        }

        Optional<Assessment> assessmentOptional = index.getAssessment(umlClass.getSimilarityID());

        if (!assessmentOptional.isPresent()) {
            missing++;
        }
        else {
            Score score = assessmentOptional.get().getScore(umlClass.getContext());

            if (score == null) {
                log.debug("Unable to find score for class " + umlClass.getJSONElementID() + " with the specific context");
            }
            else {
                scoreHashMap.put(umlClass, score);
            }
        }

        double totalCount = umlClass.getElementCount();
        double coverage = 1;
        if (totalCount != 0) {
            coverage = (totalCount - missing) / totalCount;
        }
        else {
            log.warn("'totalCount' was 0. Set coverage to 1 for a CompassResult");
        }

        return new CompassResult(scoreHashMap, coverage);
    }

    public double getTotalCoverage() {
        return totalCoverage;
    }

    public double getTotalConfidence() {
        return totalConfidence;
    }
}
