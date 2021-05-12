package de.tum.in.www1.artemis.service.compass;

import static com.google.gson.JsonParser.parseString;
import static de.tum.in.www1.artemis.service.compass.utils.CompassConfiguration.ELEMENT_CONFIDENCE_THRESHOLD;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;

import org.hibernate.Hibernate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonObject;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.cp.IAtomicReference;

import de.tum.in.www1.artemis.domain.Feedback;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.Submission;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.FeedbackType;
import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;
import de.tum.in.www1.artemis.service.compass.assessment.CompassResult;
import de.tum.in.www1.artemis.service.compass.assessment.SimilaritySetAssessment;
import de.tum.in.www1.artemis.service.compass.controller.*;
import de.tum.in.www1.artemis.service.compass.grade.Grade;
import de.tum.in.www1.artemis.service.compass.statistics.CompassStatistics;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLDiagram;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLElement;
import de.tum.in.www1.artemis.service.compass.umlmodel.classdiagram.*;

public class CompassCalculationEngine {

    private final Logger log = LoggerFactory.getLogger(CompassCalculationEngine.class);

    private ModelIndex modelIndex;

    private AutomaticAssessmentController automaticAssessmentController;

    private ModelSelector modelSelector;

    private IAtomicReference<LocalDateTime> lastUsed;

    private CompassStatistics compassStatistics;

    CompassCalculationEngine(Long exerciseId, Set<ModelingSubmission> modelingSubmissions, HazelcastInstance hazelcastInstance) {
        lastUsed = hazelcastInstance.getCPSubsystem().getAtomicReference("lastUsed - " + exerciseId);
        lastUsed.set(LocalDateTime.now());
        modelIndex = new ModelIndex(exerciseId, hazelcastInstance);
        automaticAssessmentController = new AutomaticAssessmentController(exerciseId, hazelcastInstance);
        modelSelector = new ModelSelector(automaticAssessmentController, hazelcastInstance, exerciseId);
        compassStatistics = new CompassStatistics(modelIndex, automaticAssessmentController);
        for (Submission submission : modelingSubmissions) {
            // We have to unproxy here as sometimes the Submission is a Hibernate proxy resulting in a cast exception
            // when iterating over the ModelingSubmissions directly (i.e. for (ModelingSubmission submission : submissions)).
            ModelingSubmission modelingSubmission = (ModelingSubmission) Hibernate.unproxy(submission);

            String model = modelingSubmission.getModel();
            if (model != null) {
                buildModel(modelingSubmission);
                addManualAssessmentForSubmissionIfExists(modelingSubmission);
            }
        }
        assessModelsAutomatically();
    }

    /**
     * Creates a JSONObject from the model contained in the given modeling submission. Afterwards, it builds an UMLClassDiagramm from the JSONObject, analyzes the similarity and
     * sets the similarity ID of each model element and adds the model to the model index of the calculation engine. The model index contains all models of the corresponding
     * exercise.
     *
     * @param modelingSubmission the modeling submission containing the model as JSON string
     */
    private void buildModel(ModelingSubmission modelingSubmission) {
        if (modelingSubmission.getModel() != null) {
            buildModel(modelingSubmission.getId(), modelingSubmission.getModel());
        }
    }

    private void buildModel(long modelSubmissionId, String model) {
        buildModel(modelSubmissionId, parseString(model).getAsJsonObject());
    }

    /**
     * Build an UMLClassDiagramm from a JSON representation of the model, analyzes the similarity and sets the similarity ID of each model element. Afterwards, the model is added
     * to the model index of the calculation engine which contains all models of the corresponding exercise.
     *
     * @param modelSubmissionId the id of the modeling submission the model belongs to
     * @param jsonModel         JSON representation of the UML model
     */
    private void buildModel(long modelSubmissionId, JsonObject jsonModel) {
        try {
            UMLDiagram model = UMLModelParser.buildModelFromJSON(jsonModel, modelSubmissionId);
            SimilarityDetector.analyzeSimilarity(model, modelIndex);
            modelIndex.addModel(model);
        }
        catch (IOException e) {
            log.error("Error while building and adding model!", e);
        }
    }

    public void buildPendingModels() {
        for (Map.Entry<Long, String> modelEntry : modelIndex.getPendingEntries()) {
            buildModel(modelEntry.getKey(), modelEntry.getValue());
        }
    }

    /**
     * Adds the manual assessment of the given submission to Compass so that it can be used for automatic assessments. Additionally, it marks the submission as assessed, i.e. the
     * submission is not considered when providing a submission for manual assessment to the client.
     *
     * @param submission the submission for which the manual assessment is added
     */
    private void addManualAssessmentForSubmissionIfExists(ModelingSubmission submission) {

        Boolean hasManualAssessment = submission.getLatestResult() != null && submission.getLatestResult().getCompletionDate() != null
                && submission.getLatestResult().getAssessmentType().equals(AssessmentType.MANUAL);

        if (!hasManualAssessment) {
            return;
        }

        UMLDiagram model = modelIndex.getModel(submission.getId());

        if (model == null) {
            log.error("Could not build assessment for submission {}", submission.getId());
            return;
        }

        addNewManualAssessment(submission.getLatestResult().getFeedbacks(), model);

        modelSelector.removeModelWaitingForAssessment(submission.getId());
        modelSelector.addAlreadyHandledModel(submission.getId());
    }

    private void assessModelsAutomatically() {
        automaticAssessmentController.assessModelsAutomatically(modelIndex);
    }

    /**
     * Get the given number of ids of the next optimal modeling submissions. Optimal means that an assessment for this model results in the biggest knowledge gain for Compass which
     * can be used for automatic assessments.
     *
     * @param numberOfModels the number of next optimal models to load
     * @return the ids of the next optimal modeling submissions, or an empty list if there are no unhandled submissions
     */
    public List<Long> getNextOptimalModels(int numberOfModels) {
        lastUsed.set(LocalDateTime.now());
        return modelSelector.selectNextModels(modelIndex, numberOfModels);
    }

    /**
     * Get the assessment result for a model. If no assessment is saved for the given model, it tries to create a new one automatically with the existing information of the engine.
     *
     * @param modelSubmissionId the id of the model
     * @return the assessment result for the model
     */
    public Grade getGradeForModel(long modelSubmissionId) {
        lastUsed.set(LocalDateTime.now());

        UMLDiagram model = modelIndex.getModel(modelSubmissionId);
        if (model == null) {
            return null;
        }

        CompassResult compassResult = automaticAssessmentController.getLastAssessmentCompassResult(modelSubmissionId);

        if (compassResult == null) {
            return automaticAssessmentController.assessModelAutomatically(model);
        }
        return compassResult;
    }

    public Collection<Long> getModelIds() {
        return modelIndex.getModelIds();
    }

    /**
     * Update the engine with a new manual assessment.
     *
     * @param modelingAssessment the new assessment as list of individual Feedback objects
     * @param assessedModelSubmissionId  the id of the corresponding model
     */
    public void notifyNewAssessment(List<Feedback> modelingAssessment, long assessedModelSubmissionId) {
        lastUsed.set(LocalDateTime.now());
        modelSelector.addAlreadyHandledModel(assessedModelSubmissionId);
        UMLDiagram model = modelIndex.getModel(assessedModelSubmissionId);
        if (model == null) {
            log.warn("Cannot add manual assessment to Compass, because the model in modelIndex is null for submission id {}", assessedModelSubmissionId);
            return;
        }
        addNewManualAssessment(modelingAssessment, model);
        modelSelector.removeModelWaitingForAssessment(model.getModelSubmissionId());
        assessModelsAutomatically();
    }

    /**
     * Add a new model
     *
     * @param model   the new model as raw sting
     * @param modelId the id of the new model
     */
    public void notifyNewModel(String model, long modelId) {
        lastUsed.set(LocalDateTime.now());
        // Do not add models that might already exist
        if (modelIndex.modelExists(modelId)) {
            return;
        }
        if (model != null) {
            modelIndex.addPendingModel(modelId, model);
        }
    }

    /**
     * @return the time when the engine has been used last
     */
    public LocalDateTime getLastUsedAt() {
        return lastUsed.get();
    }

    /**
     * Get the list of model IDs which have been selected for the next manual assessments. Typically these models are the ones where Compass learns the most, when they are
     * assessed. All returned models do not have a complete assessment.
     *
     * @return a list of modelIds that should be assessed next
     */
    public List<Long> getModelsWaitingForAssessment() {
        return modelSelector.getModelsWaitingForAssessment();
    }

    /**
     * Removes the model with the given id from the list of models that should be assessed next. The isAssessed flag indicates if the corresponding model still needs an assessment
     * or not, i.e. if the flag is true, the model will no longer be considered for assessment by Compass.
     *
     * @param modelSubmissionId    the id of the model
     * @param isAssessed a flag indicating if the model still needs an assessment or not
     */
    public void removeModelWaitingForAssessment(long modelSubmissionId, boolean isAssessed) {
        modelSelector.removeModelWaitingForAssessment(modelSubmissionId);
        if (isAssessed) {
            modelSelector.addAlreadyHandledModel(modelSubmissionId);
        }
        else {
            modelSelector.removeAlreadyHandledModel(modelSubmissionId);
        }
    }

    /**
     * Mark a model as unassessed, i.e. that it (still) needs to be assessed. By that it is not locked anymore and can be returned for assessment by Compass again.
     *
     * @param modelSubmissionId the id of the model that should be marked as unassessed
     */
    public void markModelAsUnassessed(long modelSubmissionId) {
        modelSelector.removeAlreadyHandledModel(modelSubmissionId);
    }

    /**
     * Generate a Feedback list from the given Grade for the given model. The Grade was generated by Compass earlier in the automatic assessment process. It basically contains the
     * Compass internal representation of the automatic assessment for the given model.
     *
     * @param grade   the Grade generated by Compass from which the Feedback list should be generated from
     * @param modelId the id of the corresponding model
     * @param result  the corresponding result that will be linked to the newly created feedback items
     * @return the list of Feedback items generated from the Grade
     */
    public List<Feedback> convertToFeedback(Grade grade, long modelId, Result result) {
        UMLDiagram model = this.modelIndex.getModel(modelId);

        if (model == null || grade == null || grade.getJsonIdPointsMapping() == null) {
            return new ArrayList<>();
        }

        List<Feedback> feedbackList = new ArrayList<>();

        for (Map.Entry<String, Double> gradePointsEntry : grade.getJsonIdPointsMapping().entrySet()) {
            Feedback feedback = new Feedback();

            String jsonElementID = gradePointsEntry.getKey();
            UMLElement umlElement = model.getElementByJSONID(jsonElementID);

            if (umlElement == null) {
                log.error("Element {} was not found in Model", jsonElementID);
                continue;
            }

            // Get the confidence for this element of the model. If the confidence is less than the configured threshold, no automatic feedback will be created for this element
            // and the loop will continue with the next model element.
            double elementConfidence = getConfidenceForElement(jsonElementID);
            if (elementConfidence < ELEMENT_CONFIDENCE_THRESHOLD) {
                log.debug("Confidence {} of element {} is smaller than configured confidence threshold {}", elementConfidence, jsonElementID, ELEMENT_CONFIDENCE_THRESHOLD);
                continue;
            }

            // Set the values of the automatic feedback using the values of the Grade that Compass calculated earlier in the automatic assessment process.
            feedback.setCredits(gradePointsEntry.getValue());
            feedback.setPositive(feedback.getCredits() >= 0);
            feedback.setText(grade.getJsonIdCommentsMapping().getOrDefault(jsonElementID, ""));
            feedback.setReference(buildReferenceString(umlElement, jsonElementID));
            feedback.setType(FeedbackType.AUTOMATIC);
            feedback.setResult(result);

            feedbackList.add(feedback);
        }
        return feedbackList;
    }

    /**
     * Creates the reference string to be stored in an Feedback element for modeling submissions. The reference of a modeling Feedback stores the type of the corresponding UML
     * element and its jsonElementId and is of the form "<umlElementType>:<jsonElementIds>".
     *
     * @param umlElement    the UML model element the Feedback belongs to
     * @param jsonElementId the jsonElementId of the UML model element
     * @return the formatted reference string containing the element type and its jsonElementId
     */
    private String buildReferenceString(UMLElement umlElement, String jsonElementId) {
        return umlElement.getType() + ":" + jsonElementId;
    }

    /**
     * Get the confidence for a specific model element with the given id. The confidence is the percentage indicating how certain Compass is about the assessment of the given model
     * element. If it is smaller than a configured threshold, the element with the given id will not be assessed automatically.
     *
     * @param elementId    the id of the model element
     * @return the confidence for the model element with the given id
     */
    public double getConfidenceForElement(String elementId) {
        Integer similarityId = modelIndex.getSimilarityId(elementId);
        if (similarityId == null) {
            return 0.0;
        }

        Optional<SimilaritySetAssessment> optionalAssessment = automaticAssessmentController.getAssessmentForSimilaritySet(similarityId);
        return optionalAssessment.map(assessment -> assessment.getScore().getConfidence()).orElse(0.0);
    }

    /**
     * Tells the automatic assessment controller to add the given feedback elements of a manual assessment to the assessment of the corresponding similarity sets where it can be
     * used for automatic assessments.
     *
     * @param modelingAssessment the feedback of a manual assessment
     * @param model              the corresponding model
     */
    private void addNewManualAssessment(List<Feedback> modelingAssessment, UMLDiagram model) {
        automaticAssessmentController.addFeedbacksToSimilaritySet(modelingAssessment, model);
    }

    /**
     * Used for internal analysis of modeling data. Do not remove, usage is commented out due to performance reasons.
     *
     * @param exerciseId the id of the modeling exercise that is analyzed
     * @param finishedResults the list of finished results, i.e. results for which assessor and completion date is not null
     */
    public void printStatistic(long exerciseId, List<Result> finishedResults) {
        compassStatistics.printStatistic(exerciseId, finishedResults);
    }

    /**
     * format: uniqueElements [{id} name apollonId conflicts] numberModels numberConflicts totalConfidence totalCoverage models [{id} confidence coverage conflicts]
     *
     * @return statistics about the UML model
     */
    // TODO: I don't think we should expose JSONObject to this internal server class. It would be better to return Java objects here
    public JsonObject getStatistics() {
        return compassStatistics.getStatistics();
    }

    public void destroy() {
        modelIndex.destroy();
        automaticAssessmentController.destroy();
        modelSelector.destroy();
    }

}
