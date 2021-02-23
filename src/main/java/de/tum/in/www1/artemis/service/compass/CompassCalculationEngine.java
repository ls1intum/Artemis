package de.tum.in.www1.artemis.service.compass;

import static com.google.gson.JsonParser.parseString;
import static de.tum.in.www1.artemis.service.compass.utils.CompassConfiguration.ELEMENT_CONFIDENCE_THRESHOLD;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import org.hibernate.Hibernate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import de.tum.in.www1.artemis.domain.Feedback;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.Submission;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.FeedbackType;
import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;
import de.tum.in.www1.artemis.service.compass.assessment.CompassResult;
import de.tum.in.www1.artemis.service.compass.assessment.Score;
import de.tum.in.www1.artemis.service.compass.assessment.SimilaritySetAssessment;
import de.tum.in.www1.artemis.service.compass.controller.*;
import de.tum.in.www1.artemis.service.compass.grade.Grade;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLDiagram;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLElement;
import de.tum.in.www1.artemis.service.compass.umlmodel.classdiagram.*;

public class CompassCalculationEngine{

    private final Logger log = LoggerFactory.getLogger(CompassCalculationEngine.class);

    private ModelIndex modelIndex;

    private AssessmentIndex assessmentIndex;

    private AutomaticAssessmentController automaticAssessmentController;

    private ModelSelector modelSelector;

    private LocalDateTime lastUsed;

    CompassCalculationEngine(Set<ModelingSubmission> modelingSubmissions) {
        lastUsed = LocalDateTime.now();
        modelIndex = new ModelIndex();
        assessmentIndex = new AssessmentIndex();
        automaticAssessmentController = new AutomaticAssessmentController();
        modelSelector = new ModelSelector();

        for (Submission submission : modelingSubmissions) {
            // We have to unproxy here as sometimes the Submission is a Hibernate proxy resulting in a cast exception
            // when iterating over the ModelingSubmissions directly (i.e. for (ModelingSubmission submission : submissions)).
            ModelingSubmission modelingSubmission = (ModelingSubmission) Hibernate.unproxy(submission);

            String model = modelingSubmission.getModel();
            if (model != null) {
                buildModel(modelingSubmission);

                if (hasCompletedManualAssessment(modelingSubmission)) {
                    addManualAssessmentForSubmission(modelingSubmission);
                }
            }
        }
        assessModelsAutomatically();
    }

    /**
     * Checks if the given modeling submission already has a completed manual assessment. The assessment is completed if the submission has a result with a completion date.
     *
     * @param modelingSubmission the modeling submission to check
     * @return true if the submission already has a completed manual assessment, false otherwise
     */
    private boolean hasCompletedManualAssessment(ModelingSubmission modelingSubmission) {
        return modelingSubmission.getLatestResult() != null && modelingSubmission.getLatestResult().getCompletionDate() != null
                && modelingSubmission.getLatestResult().getAssessmentType().equals(AssessmentType.MANUAL);
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
            buildModel(modelingSubmission.getId(), parseString(modelingSubmission.getModel()).getAsJsonObject());
        }
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

    /**
     * Adds the manual assessment of the given submission to Compass so that it can be used for automatic assessments. Additionally, it marks the submission as assessed, i.e. the
     * submission is not considered when providing a submission for manual assessment to the client.
     *
     * @param submission the submission for which the manual assessment is added
     */
    private void addManualAssessmentForSubmission(ModelingSubmission submission) {
        UMLDiagram model = modelIndex.getModelMap().get(submission.getId());

        if (model == null || submission.getLatestResult() == null || submission.getLatestResult().getCompletionDate() == null) {
            log.error("Could not build assessment for submission {}", submission.getId());
            return;
        }

        addNewManualAssessment(submission.getLatestResult().getFeedbacks(), model);

        modelSelector.removeModelWaitingForAssessment(submission.getId());
        modelSelector.addAlreadyHandledModel(submission.getId());
    }

    protected Collection<UMLDiagram> getUmlModelCollection() {
        return modelIndex.getModelCollection();
    }

    protected Map<Long, UMLDiagram> getModelMap() {
        return modelIndex.getModelMap();
    }

    @SuppressWarnings("unused")
    private double getTotalCoverage() {
        return automaticAssessmentController.getTotalCoverage();
    }

    @SuppressWarnings("unused")
    private double getTotalConfidence() {
        return automaticAssessmentController.getTotalConfidence();
    }

    private void assessModelsAutomatically() {
        automaticAssessmentController.assessModelsAutomatically(modelIndex, assessmentIndex);
    }

    /**
     * Get the given number of ids of the next optimal modeling submissions. Optimal means that an assessment for this model results in the biggest knowledge gain for Compass which
     * can be used for automatic assessments.
     *
     * @param numberOfModels the number of next optimal models to load
     * @return the ids of the next optimal modeling submissions, or an empty list if there are no unhandled submissions
     */
    public List<Long> getNextOptimalModels(int numberOfModels) {
        lastUsed = LocalDateTime.now();
        return modelSelector.selectNextModels(modelIndex, numberOfModels);
    }

    /**
     * Get the assessment result for a model. If no assessment is saved for the given model, it tries to create a new one automatically with the existing information of the engine.
     *
     * @param modelSubmissionId the id of the model
     * @return the assessment result for the model
     */
    public Grade getGradeForModel(long modelSubmissionId) {
        lastUsed = LocalDateTime.now();
        if (!modelIndex.getModelMap().containsKey(modelSubmissionId)) {
            return null;
        }

        UMLDiagram model = modelIndex.getModelMap().get(modelSubmissionId);
        CompassResult compassResult = model.getLastAssessmentCompassResult();

        if (compassResult == null) {
            return automaticAssessmentController.assessModelAutomatically(model, assessmentIndex);
        }
        return compassResult;
    }

    public Collection<Long> getModelIds() {
        return modelIndex.getModelMap().keySet();
    }

    /**
     * Update the engine with a new manual assessment.
     *
     * @param modelingAssessment the new assessment as list of individual Feedback objects
     * @param assessedModelSubmissionId  the id of the corresponding model
     */
    public void notifyNewAssessment(List<Feedback> modelingAssessment, long assessedModelSubmissionId) {
        lastUsed = LocalDateTime.now();
        modelSelector.addAlreadyHandledModel(assessedModelSubmissionId);
        UMLDiagram model = modelIndex.getModel(assessedModelSubmissionId);
        if (model == null) {
            log.warn("Cannot add manual assessment to Compass, because the model in modelIndex is null for submission id " + assessedModelSubmissionId);
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
        lastUsed = LocalDateTime.now();
        // Do not add models that might already exist
        if (modelIndex.getModelMap().containsKey(modelId)) {
            return;
        }
        if (model != null) {
            JsonElement jsonElement = parseString(model);
            if (jsonElement != null) {
                buildModel(modelId, jsonElement.getAsJsonObject());
            }
        }
    }

    /**
     * @return the time when the engine has been used last
     */
    public LocalDateTime getLastUsedAt() {
        return lastUsed;
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
        UMLDiagram model = this.modelIndex.getModelMap().get(modelId);

        if (model == null || grade == null || grade.getJsonIdPointsMapping() == null) {
            return new ArrayList<>();
        }

        List<Feedback> feedbackList = new ArrayList<>();

        for (Map.Entry<String, Double> gradePointsEntry : grade.getJsonIdPointsMapping().entrySet()) {
            Feedback feedback = new Feedback();

            String jsonElementID = gradePointsEntry.getKey();
            UMLElement umlElement = model.getElementByJSONID(jsonElementID);

            if (umlElement == null) {
                log.error("Element " + jsonElementID + " was not found in Model");
                continue;
            }

            // Get the confidence for this element of the model. If the confidence is less than the configured threshold, no automatic feedback will be created for this element
            // and the loop will continue with the next model element.
            double elementConfidence = getConfidenceForElement(jsonElementID, modelId);
            if (elementConfidence < ELEMENT_CONFIDENCE_THRESHOLD) {
                log.debug("Confidence " + elementConfidence + " of element " + jsonElementID + " is smaller than configured confidence threshold " + ELEMENT_CONFIDENCE_THRESHOLD);
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
     * format: uniqueElements [{id} name apollonId conflicts] numberModels numberConflicts totalConfidence totalCoverage models [{id} confidence coverage conflicts]
     *
     * @return statistics about the UML model
     */
    // TODO: I don't think we should expose JSONObject to this internal server class. It would be better to return Java objects here
    public JsonObject getStatistics() {
        JsonObject jsonObject = new JsonObject();

        JsonObject uniqueElements = new JsonObject();
        int numberOfConflicts = 0;
        for (UMLElement umlElement : modelIndex.getUniqueElements()) {
            JsonObject uniqueElement = new JsonObject();
            uniqueElement.addProperty("name", umlElement.toString());
            uniqueElement.addProperty("apollonId", umlElement.getJSONElementID());
            boolean hasConflict = hasConflict(umlElement.getSimilarityID());
            if (hasConflict) {
                numberOfConflicts++;
            }
            uniqueElement.addProperty("conflicts", hasConflict);
            uniqueElements.add(String.valueOf(umlElement.getSimilarityID()), uniqueElement);
        }
        jsonObject.add("uniqueElements", uniqueElements);

        jsonObject.addProperty("numberModels", modelIndex.getModelCollection().size());
        jsonObject.addProperty("numberConflicts", numberOfConflicts);
        jsonObject.addProperty("totalConfidence", getTotalConfidence());
        jsonObject.addProperty("totalCoverage", getTotalCoverage());

        JsonObject models = new JsonObject();
        for (Map.Entry<Long, UMLDiagram> modelEntry : getModelMap().entrySet()) {
            JsonObject model = new JsonObject();
            model.addProperty("coverage", modelEntry.getValue().getLastAssessmentCoverage());
            model.addProperty("confidence", modelEntry.getValue().getLastAssessmentConfidence());
            int numberOfModelConflicts = 0;
            List<UMLElement> elements = new ArrayList<>(modelEntry.getValue().getAllModelElements());
            for (UMLElement element : elements) {
                boolean modelHasConflict = hasConflict(element.getSimilarityID());
                if (modelHasConflict) {
                    numberOfModelConflicts++;
                }
            }
            model.addProperty("conflicts", numberOfModelConflicts);
            model.addProperty("elements", elements.size());
            model.addProperty("classes", elements.stream().filter(umlElement -> umlElement instanceof UMLClass).count());
            model.addProperty("attributes", elements.stream().filter(umlElement -> umlElement instanceof UMLAttribute).count());
            model.addProperty("methods", elements.stream().filter(umlElement -> umlElement instanceof UMLMethod).count());
            model.addProperty("associations", elements.stream().filter(umlElement -> umlElement instanceof UMLRelationship).count());
            models.add(modelEntry.getKey().toString(), model);
        }
        jsonObject.add("models", models);

        return jsonObject;
    }

    /**
     * Get the confidence for a specific model element with the given id. The confidence is the percentage indicating how certain Compass is about the assessment of the given model
     * element. If it is smaller than a configured threshold, the element with the given id will not be assessed automatically.
     *
     * @param elementId    the id of the model element
     * @param submissionId the id of the submission that contains the corresponding model
     * @return the confidence for the model element with the given id
     */
    public double getConfidenceForElement(String elementId, long submissionId) {
        UMLDiagram model = modelIndex.getModel(submissionId);
        UMLElement element = model.getElementByJSONID(elementId);
        if (element == null) {
            return 0.0;
        }

        Optional<SimilaritySetAssessment> optionalAssessment = assessmentIndex.getAssessmentForSimilaritySet(element.getSimilarityID());
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
        Map<String, Feedback> feedbackMapping = createElementIdFeedbackMapping(modelingAssessment);
        automaticAssessmentController.addFeedbackToSimilaritySet(assessmentIndex, feedbackMapping, model);
    }

    /**
     * Create a jsonModelId to Feedback mapping generated from the feedback list of a submission.
     *
     * @param feedbackList the feedbackList
     * @return a map of elementIds to scores
     */
    private Map<String, Feedback> createElementIdFeedbackMapping(List<Feedback> feedbackList) {
        Map<String, Feedback> elementIdFeedbackMap = new HashMap<>();
        feedbackList.forEach(feedback -> {
            String jsonElementId = feedback.getReferenceElementId();
            if (jsonElementId != null) {
                elementIdFeedbackMap.put(jsonElementId, feedback);
            }
        });
        return elementIdFeedbackMap;
    }

    private boolean hasConflict(int elementId) {
        Optional<SimilaritySetAssessment> optionalAssessment = assessmentIndex.getAssessmentForSimilaritySet(elementId);

        if (optionalAssessment.isEmpty() || optionalAssessment.get().getFeedbackList() == null || optionalAssessment.get().getFeedbackList().isEmpty()) {
            return false;
        }

        List<Feedback> feedbackList = optionalAssessment.get().getFeedbackList();
        // if not all feedback entries have the same score as the first feedback, i.e. not all scores are the same, there is a conflict in the assessment
        return !feedbackList.stream().allMatch(feedback -> feedback.getCredits().equals(feedbackList.get(0).getCredits()));
    }

    /**
     * Used for internal analysis of modeling data. Do not remove, usage is commented out due to performance reasons.
     *
     * @param exerciseId the id of the modeling exercise that is analyzed
     * @param finishedResults the list of finished results, i.e. results for which assessor and completion date is not null
     */
    public void printStatistic(long exerciseId, List<Result> finishedResults) {
        log.info("Statistics for exercise " + exerciseId + "\n\n\n");

        long totalNumberOfFeedback = 0;
        long totalNumberOfAutomaticFeedback = 0;
        long totalNumberOfAdaptedFeedback = 0;
        long totalNumberOfManualFeedback = 0;

        long numberOfAssessedClasses = 0;
        long numberOfAssessedAttrbutes = 0;
        long numberOfAssessedMethods = 0;
        long numberOfAssessedRelationships = 0;
        long numberOfAssessedPackages = 0;

        long totalLengthOfFeedback = 0;
        long totalLengthOfPositiveFeedback = 0;
        long totalNumberOfPositiveFeedbackItems = 0;
        long totalLengthOfNegativeFeedback = 0;
        long totalNumberOfNegativeFeedbackItems = 0;
        long totalLengthOfNeutralFeedback = 0;
        long totalNumberOfNeutralFeedbackItems = 0;

        for (Result result : finishedResults) {
            List<Feedback> referenceFeedback = result.getFeedbacks().stream().filter(feedback -> feedback.getReference() != null).collect(Collectors.toList());

            totalNumberOfFeedback += referenceFeedback.size();
            totalNumberOfAutomaticFeedback += referenceFeedback.stream().filter(feedback -> feedback.getType() == FeedbackType.AUTOMATIC).count();
            totalNumberOfAdaptedFeedback += referenceFeedback.stream().filter(feedback -> feedback.getType() == FeedbackType.AUTOMATIC_ADAPTED).count();
            totalNumberOfManualFeedback += referenceFeedback.stream().filter(feedback -> feedback.getType() == FeedbackType.MANUAL).count();

            numberOfAssessedClasses += referenceFeedback.stream()
                    .filter(feedback -> feedback.getReferenceElementType().equals("Class") || feedback.getReferenceElementType().equals("AbstractClass")
                            || feedback.getReferenceElementType().equals("Interface") || feedback.getReferenceElementType().equals("Enumeration"))
                    .count();
            numberOfAssessedAttrbutes += referenceFeedback.stream().filter(feedback -> feedback.getReferenceElementType().equals("ClassAttribute")).count();
            numberOfAssessedMethods += referenceFeedback.stream().filter(feedback -> feedback.getReferenceElementType().equals("ClassMethod")).count();
            numberOfAssessedRelationships += referenceFeedback.stream()
                    .filter(feedback -> feedback.getReferenceElementType().equals("ClassBidirectional") || feedback.getReferenceElementType().equals("ClassUnidirectional")
                            || feedback.getReferenceElementType().equals("ClassAggregation") || feedback.getReferenceElementType().equals("ClassInheritance")
                            || feedback.getReferenceElementType().equals("ClassDependency") || feedback.getReferenceElementType().equals("ClassComposition")
                            || feedback.getReferenceElementType().equals("ClassRealization"))
                    .count();
            numberOfAssessedPackages += referenceFeedback.stream().filter(feedback -> feedback.getReferenceElementType().equals("Package")).count();

            for (Feedback feedback : referenceFeedback) {
                int feedbackLength = 0;

                if (feedback.getText() != null) {
                    feedbackLength = feedback.getText().length();
                }

                totalLengthOfFeedback += feedbackLength;

                if (feedback.getCredits() > 0) {
                    totalLengthOfPositiveFeedback += feedbackLength;
                    totalNumberOfPositiveFeedbackItems++;
                }
                else if (feedback.getCredits() == 0) {
                    totalLengthOfNeutralFeedback += feedbackLength;
                    totalNumberOfNeutralFeedbackItems++;
                }
                else if (feedback.getCredits() < 0) {
                    totalLengthOfNegativeFeedback += feedbackLength;
                    totalNumberOfNegativeFeedbackItems++;
                }
            }
        }

        long numberOfModels = modelIndex.getModelCollection().size();
        Map<UMLElement, Integer> modelElementMapping = modelIndex.getModelElementMapping();
        long numberOfModelElements = modelElementMapping.size();
        long numberOfClasses = 0;
        long numberOfAttrbutes = 0;
        long numberOfMethods = 0;
        long numberOfRelationships = 0;
        long numberOfPackages = 0;

        for (UMLElement element : modelElementMapping.keySet()) {
            if (element instanceof UMLClass) {
                numberOfClasses++;
            }
            else if (element instanceof UMLAttribute) {
                numberOfAttrbutes++;
            }
            else if (element instanceof UMLMethod) {
                numberOfMethods++;
            }
            else if (element instanceof UMLRelationship) {
                numberOfRelationships++;
            }
            else if (element instanceof UMLPackage) {
                numberOfPackages++;
            }
        }

        // General information
        log.info("################################################## General information ##################################################" + "\n");

        log.info("Number of models: " + numberOfModels + "\n");
        log.info("Number of model elements: " + numberOfModelElements + "\n");
        log.info("Number of classes: " + numberOfClasses + "\n");
        log.info("Number of attributes: " + numberOfAttrbutes + "\n");
        log.info("Number of methods: " + numberOfMethods + "\n");
        log.info("Number of relationships: " + numberOfRelationships + "\n");
        log.info("Number of packages: " + numberOfPackages + "\n");
        double elementsPerModel = numberOfModelElements * 1.0 / numberOfModels;
        log.info("Average number of elements per model: " + elementsPerModel + "\n");

        log.info("Number of assessed models: " + finishedResults.size() + "\n");
        log.info("Number of assessed model elements: " + totalNumberOfFeedback + "\n");
        log.info("Number of assessed classes: " + numberOfAssessedClasses + " (" + Math.round(numberOfAssessedClasses * 10000.0 / numberOfClasses) / 100.0 + "%)" + "\n");
        log.info("Number of assessed attributes: " + numberOfAssessedAttrbutes + " (" + Math.round(numberOfAssessedAttrbutes * 10000.0 / numberOfAttrbutes) / 100.0 + "%)" + "\n");
        log.info("Number of assessed methods: " + numberOfAssessedMethods + " (" + Math.round(numberOfAssessedMethods * 10000.0 / numberOfMethods) / 100.0 + "%)" + "\n");
        log.info("Number of assessed relationships: " + numberOfAssessedRelationships + " (" + Math.round(numberOfAssessedRelationships * 10000.0 / numberOfRelationships) / 100.0
                + "%)" + "\n");
        log.info("Number of assessed packages: " + numberOfAssessedPackages + " (" + Math.round(numberOfAssessedPackages * 10000.0 / numberOfPackages) / 100.0 + "%)" + "\n");
        double feedbackPerAssessment = totalNumberOfFeedback * 1.0 / finishedResults.size();
        log.info("Average number of feedback elements per assessment: " + feedbackPerAssessment + "\n\n\n");

        // Feedback type
        log.info("################################################## Feedback type ##################################################" + "\n");

        log.info("Automatic feedback: " + totalNumberOfAutomaticFeedback + " (" + Math.round(totalNumberOfAutomaticFeedback * 10000.0 / totalNumberOfFeedback) / 100.0 + "%)"
                + "\n");
        log.info("Adapted feedback: " + totalNumberOfAdaptedFeedback + " (" + Math.round(totalNumberOfAdaptedFeedback * 10000.0 / totalNumberOfFeedback) / 100.0 + "%)" + "\n");
        log.info("Manual feedback: " + totalNumberOfManualFeedback + " (" + Math.round(totalNumberOfManualFeedback * 10000.0 / totalNumberOfFeedback) / 100.0 + "%)" + "\n");
        log.info("Amount of automatic feedback that was adapted: "
                + Math.round(totalNumberOfAdaptedFeedback * 10000.0 / (totalNumberOfAutomaticFeedback + totalNumberOfAdaptedFeedback)) / 100.0 + "%\n\n\n");

        // Feedback length
        log.info("################################################## Feedback length ##################################################" + "\n");

        log.info("Total amount of feedback: " + totalNumberOfFeedback + "\n");
        log.info("Average length of feedback: " + totalLengthOfFeedback * 1.0 / totalNumberOfFeedback + "\n");
        log.info("Total amount of positive feedback: " + totalNumberOfPositiveFeedbackItems + "\n");
        log.info("Average length of positive feedback: " + totalLengthOfPositiveFeedback * 1.0 / totalNumberOfPositiveFeedbackItems + "\n");
        log.info("Total amount of neutral feedback: " + totalNumberOfNeutralFeedbackItems + "\n");
        log.info("Average length of neutral feedback: " + totalLengthOfNeutralFeedback * 1.0 / totalNumberOfNeutralFeedbackItems + "\n");
        log.info("Total amount of negative feedback: " + totalNumberOfNegativeFeedbackItems + "\n");
        log.info("Average length of negative feedback: " + totalLengthOfNegativeFeedback * 1.0 / totalNumberOfNegativeFeedbackItems + "\n\n\n");

        // Similarity sets
        log.info("################################################## Similarity sets ##################################################" + "\n");

        // Note, that these two value refer to all similarity sets that have an assessment, i.e. it is not the total number as it excludes the sets without assessments. This might
        // distort the analysis values below.
        long numberOfSimilaritySets = assessmentIndex.getAssessmentMap().size();
        long numberOfElementsInSimilaritySets = 0;

        long numberOfSimilaritySetsPositiveScore = 0;
        long numberOfSimilaritySetsPositiveScoreRegardingConfidence = 0;

        for (SimilaritySetAssessment similaritySetAssessment : assessmentIndex.getAssessmentMap().values()) {
            numberOfElementsInSimilaritySets += similaritySetAssessment.getFeedbackList().size();

            Score score = similaritySetAssessment.getScore();
            if (score.getPoints() > 0) {
                numberOfSimilaritySetsPositiveScore += 1;
                if (score.getConfidence() >= 0.8) {
                    numberOfSimilaritySetsPositiveScoreRegardingConfidence += 1;
                }
            }
        }

        log.info("Number of unique elements (without context) of submitted models: " + modelIndex.getNumberOfUniqueElements() + "\n");
        log.info("Number of similarity sets (including context) of assessed models: " + numberOfSimilaritySets + "\n");
        log.info("Average number of elements per similarity set: " + numberOfElementsInSimilaritySets * 1.0 / numberOfSimilaritySets + "\n");
        // The optimal correction effort describes the maximum amount of model elements that tutors would have to assess in an optimal scenario
        log.info("Optimal correction effort (# similarity sets / # model elements): " + numberOfSimilaritySets * 1.0 / numberOfElementsInSimilaritySets + "\n");

        log.info("Number of similarity sets with positive score: " + numberOfSimilaritySetsPositiveScore + "\n");
        log.info("Number of similarity sets with positive score and confidence at least 80%: " + numberOfSimilaritySetsPositiveScoreRegardingConfidence + "\n\n\n");

        // Variability index
        log.info("################################################## Variability index ##################################################" + "\n");

        log.info("Variability index #1 (positive score): " + numberOfSimilaritySetsPositiveScore / elementsPerModel + "\n");
        log.info("Variability index #2 (positive score and confidence >= 80%): " + numberOfSimilaritySetsPositiveScoreRegardingConfidence / elementsPerModel + "\n");
        log.info("Variability index #3 (based on \"all\" similarity sets): " + numberOfSimilaritySets / elementsPerModel + "\n");

        log.info("Normalized variability index #1 (positive score): " + (numberOfSimilaritySetsPositiveScore - elementsPerModel) / (numberOfModelElements - elementsPerModel)
                + "\n");
        log.info("Normalized variability index #2 (positive score and confidence >= 80%): "
                + (numberOfSimilaritySetsPositiveScoreRegardingConfidence - elementsPerModel) / (numberOfModelElements - elementsPerModel) + "\n");
        log.info("Normalized variability index #3 (based on \"all\" similarity sets): " + (numberOfSimilaritySets - elementsPerModel) / (numberOfModelElements - elementsPerModel)
                + "\n");

        // Alternative calculation of the variability index considering the average feedback items per assessment instead of the average elements per model
        log.info("Alternative variability index #1 (positive score): " + numberOfSimilaritySetsPositiveScore / feedbackPerAssessment + "\n");
        log.info("Alternative variability index #2 (positive score and confidence >= 80%): " + numberOfSimilaritySetsPositiveScoreRegardingConfidence / feedbackPerAssessment
                + "\n");
        log.info("Alternative variability index #3 (based on \"all\" similarity sets): " + numberOfSimilaritySets / feedbackPerAssessment + "\n");

        log.info("Normalized alternative variability index #1 (positive score): "
                + (numberOfSimilaritySetsPositiveScore - feedbackPerAssessment) / (totalNumberOfFeedback - feedbackPerAssessment) + "\n");
        log.info("Normalized alternative variability index #2 (positive score and confidence >= 80%): "
                + (numberOfSimilaritySetsPositiveScoreRegardingConfidence - feedbackPerAssessment) / (totalNumberOfFeedback - feedbackPerAssessment) + "\n");
        log.info("Normalized alternative variability index #3 (based on \"all\" similarity sets): "
                + (numberOfSimilaritySets - feedbackPerAssessment) / (totalNumberOfFeedback - feedbackPerAssessment) + "\n");
    }
}
