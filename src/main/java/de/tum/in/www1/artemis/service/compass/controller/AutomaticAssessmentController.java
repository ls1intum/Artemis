package de.tum.in.www1.artemis.service.compass.controller;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.tum.in.www1.artemis.domain.Feedback;
import de.tum.in.www1.artemis.service.compass.assessment.CompassResult;
import de.tum.in.www1.artemis.service.compass.assessment.Score;
import de.tum.in.www1.artemis.service.compass.assessment.SimilaritySetAssessment;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLDiagram;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLElement;

public class AutomaticAssessmentController {

    private final Logger log = LoggerFactory.getLogger(AutomaticAssessmentController.class);

    private Map<Integer, SimilaritySetAssessment> similarityIdAssessmentMapping;

    private Map<Long, CompassResult> lastAssessmentResultMapping;

    private double totalCoverage;

    private double totalConfidence;

    public AutomaticAssessmentController() {
        similarityIdAssessmentMapping = new ConcurrentHashMap<>();
        lastAssessmentResultMapping = new ConcurrentHashMap<>();
    }

    /**
     * Get the assessment for the similarity set with the given similarityId.
     *
     * @param similarityId the ID of the similarity set
     * @return an Optional containing the assessment if the similarity ID exists, an empty Optional otherwise
     */
    public Optional<SimilaritySetAssessment> getAssessmentForSimilaritySet(int similarityId) {
        SimilaritySetAssessment similaritySetAssessment = similarityIdAssessmentMapping.get(similarityId);
        return Optional.ofNullable(similaritySetAssessment);
    }

    /**
     * Add a new assessment for the similarity set with the given ID to the similarityId assessment mapping.
     *
     * @param similarityId the ID of the corresponding similarity set
     * @param similaritySetAssessment the assessment for the corresponding similarity set
     */
    protected void addSimilaritySetAssessment(int similarityId, SimilaritySetAssessment similaritySetAssessment) {
        similarityIdAssessmentMapping.putIfAbsent(similarityId, similaritySetAssessment);
    }

    /**
     * Used for statistics. Get the complete map of similarity set assessments.
     *
     * @return The complete map with all similarity set assessments
     */
    public Map<Integer, SimilaritySetAssessment> getAssessmentMap() {
        return this.similarityIdAssessmentMapping;
    }

    /**
     * For every model element it adds the feedback to the assessment of the corresponding similarity set. If there is no assessment for the similarity set yet, it creates a new
     * one.
     *
     * @param feedbacks            list of feedbacks
     * @param model                the UML model - contains all elements with its jsonIds
     */
    public void addFeedbacksToSimilaritySet(List<Feedback> feedbacks, UMLDiagram model) {
        for (Feedback feedback : feedbacks) {
            String jsonElementId = feedback.getReferenceElementId();
            if (jsonElementId != null) {
                UMLElement element = model.getElementByJSONID(jsonElementId);

                if (element == null) {
                    log.warn("Element with id " + jsonElementId + " could not be found in model.");
                    continue;
                }

                Optional<SimilaritySetAssessment> optionalAssessment = getAssessmentForSimilaritySet(element.getSimilarityID());

                if (optionalAssessment.isPresent()) {
                    optionalAssessment.get().addFeedback(feedback);
                }
                else {
                    SimilaritySetAssessment newAssessment = new SimilaritySetAssessment(feedback);
                    addSimilaritySetAssessment(element.getSimilarityID(), newAssessment);
                }
            }
        }
    }

    /**
     * Loop over all models and triggers their automatic assessment.
     *
     * @param modelIndex      manages all models
     */
    // TODO CZ: only assess models automatically that do not already have a complete manual assessment?
    public void assessModelsAutomatically(ModelIndex modelIndex) {

        totalCoverage = 0;
        totalConfidence = 0;

        for (UMLDiagram model : modelIndex.getModelCollection()) {

            CompassResult compassResult = assessModelAutomatically(model);

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
     * @return a Compass result built from the assessments of the model elements
     */
    public CompassResult assessModelAutomatically(UMLDiagram model) {
        double totalCount = 0;
        double missingCount = 0;

        Map<UMLElement, Score> scoreHashMap = new ConcurrentHashMap<>();

        for (UMLElement element : model.getAllModelElements()) {
            Optional<SimilaritySetAssessment> optionalAssessment = getAssessmentForSimilaritySet(element.getSimilarityID());
            totalCount++;

            if (optionalAssessment.isEmpty()) {
                missingCount++;
            }
            else {
                Score score = optionalAssessment.get().getScore();

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

        lastAssessmentResultMapping.put(model.getModelSubmissionId(), compassResult);

        return compassResult;
    }

    public double getTotalCoverage() {
        return totalCoverage;
    }

    public double getTotalConfidence() {
        return totalConfidence;
    }

    /**
     * Set the lastAssessmentCompassResult that represents the most recent automatic assessment calculated by Compass for this diagram.
     *
     * @param compassResult the most recent Compass result for this diagram
     */
    public void setLastAssessmentCompassResult(Long submissionId, CompassResult compassResult) {
        lastAssessmentResultMapping.put(submissionId, compassResult);
    }

    /**
     * Returns the lastAssessmentCompassResult that represents the most recent automatic assessment calculated by Compass for this diagram.
     * This method is deprecated because the UML Diagram should not store such information. This should rather be stored somewhere else!
     * @return the most recent Compass result for this diagram
     */
    public CompassResult getLastAssessmentCompassResult(Long submissionId) {
        return lastAssessmentResultMapping.get(submissionId);
    }

    /**
     * Indicates if this diagram already has an automatic assessment calculated by Compass or not.
     *
     * @return true if Compass has not already calculated an automatic assessment for this diagram, false otherwise
     */
    public boolean isUnassessed(Long submissionId) {
        return getLastAssessmentCompassResult(submissionId) == null;
    }

    /**
     * Get the confidence of the last compass result, i.e. the most recent automatic assessment calculated by Compass for this diagram.
     *
     * @return The confidence of the last compass result, -1 if no compass result is available
     */
    public double getLastAssessmentConfidence(Long submissionId) {
        if (isUnassessed(submissionId)) {
            return -1;
        }

        return getLastAssessmentCompassResult(submissionId).getConfidence();
    }

    /**
     * Get the coverage for the last assessed compass result, i.e. the most recent automatic assessment calculated by Compass for this diagram.
     *
     * @return The coverage of the last compass result, -1 if no compass result is available
     */
    public double getLastAssessmentCoverage(Long submissionId) {
        if (isUnassessed(submissionId)) {
            return -1;
        }

        return getLastAssessmentCompassResult(submissionId).getCoverage();
    }
}
