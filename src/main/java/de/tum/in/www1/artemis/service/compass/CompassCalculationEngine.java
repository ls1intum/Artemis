package de.tum.in.www1.artemis.service.compass;

import static de.tum.in.www1.artemis.service.compass.utils.CompassConfiguration.ELEMENT_CONFIDENCE_THRESHOLD;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import org.hibernate.Hibernate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.Feedback;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.Submission;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.FeedbackType;
import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;
import de.tum.in.www1.artemis.service.compass.assessment.Assessment;
import de.tum.in.www1.artemis.service.compass.assessment.CompassResult;
import de.tum.in.www1.artemis.service.compass.controller.*;
import de.tum.in.www1.artemis.service.compass.grade.Grade;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLElement;
import de.tum.in.www1.artemis.service.compass.umlmodel.classdiagram.*;

public class CompassCalculationEngine implements CalculationEngine {

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

                if (hasCompleteManualAssessment(modelingSubmission)) {
                    addManualAssessmentForSubmission(modelingSubmission);
                }
            }
        }
        assessModelsAutomatically();
    }

    /**
     * Checks if the given modeling submission already has a complete manual assessment. The assessment is complete if the submission has a result with a completion date.
     */
    private boolean hasCompleteManualAssessment(ModelingSubmission modelingSubmission) {
        return modelingSubmission.getResult() != null && modelingSubmission.getResult().getCompletionDate() != null
                && modelingSubmission.getResult().getAssessmentType().equals(AssessmentType.MANUAL);
    }

    /**
     * @param modelingSubmission modelingSubmission the modelingAssessment belongs to
     * @param modelingAssessment assessment to check for conflicts
     * @return a list of conflicts modelingAssessment causes with the current manual assessment data
     */
    public Map<String, List<Feedback>> getConflictingFeedbacks(ModelingSubmission modelingSubmission, List<Feedback> modelingAssessment) {
        HashMap<String, List<Feedback>> elementConflictingFeedbackMapping = new HashMap<>();
        UMLClassDiagram model = getModel(modelingSubmission);
        if (model == null) {
            return elementConflictingFeedbackMapping;
        }
        modelingAssessment.forEach(currentFeedback -> {
            UMLElement currentElement = model.getElementByJSONID(currentFeedback.getReferenceElementId()); // TODO MJ return Optional ad throw Exception if no UMLElement found?
            assessmentIndex.getAssessment(currentElement.getSimilarityID()).ifPresent(assessment -> {
                List<Feedback> feedbacks = assessment.getFeedbacks(currentElement.getContext());
                List<Feedback> feedbacksInConflict = feedbacks.stream().filter(feedback -> !scoresAreConsideredEqual(feedback.getCredits(), currentFeedback.getCredits()))
                        .collect(Collectors.toList());
                if (!feedbacksInConflict.isEmpty()) {
                    elementConflictingFeedbackMapping.put(currentElement.getJSONElementID(), feedbacksInConflict);
                }
            });
        });
        return elementConflictingFeedbackMapping;
    }

    private UMLClassDiagram getModel(ModelingSubmission modelingSubmission) {
        UMLClassDiagram model = modelIndex.getModel(modelingSubmission.getId());
        // TODO properly handle this case and make sure after server restart the modelIndex is reloaded properly
        if (model == null) {
            // handle the case that model is null (e.g. after server restart)
            buildModel(modelingSubmission);
            model = modelIndex.getModel(modelingSubmission.getId());
        }
        return model;
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
            buildModel(modelingSubmission.getId(), new JsonParser().parse(modelingSubmission.getModel()).getAsJsonObject());
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
            UMLClassDiagram model = JSONParser.buildModelFromJSON(jsonModel, modelSubmissionId);
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
        UMLClassDiagram model = modelIndex.getModelMap().get(submission.getId());
        if (model == null || submission.getResult() == null || submission.getResult().getCompletionDate() == null) {
            log.error("Could not build assessment for submission {}", submission.getId());
            return;
        }
        addNewManualAssessment(submission.getResult().getFeedbacks(), model);
        modelSelector.removeModelWaitingForAssessment(submission.getId());
        modelSelector.addAlreadyAssessedModel(submission.getId());
    }

    protected Collection<UMLClassDiagram> getUmlModelCollection() {
        return modelIndex.getModelCollection();
    }

    protected Map<Long, UMLClassDiagram> getModelMap() {
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
     * @return id of the next optimal model or null if all models are completely assessed
     */
    @Override
    public Long getNextOptimalModel() {
        lastUsed = LocalDateTime.now();
        return modelSelector.selectNextModel(modelIndex);
    }

    @Override
    public Grade getGradeForModel(long modelSubmissionId) {
        lastUsed = LocalDateTime.now();
        if (!modelIndex.getModelMap().containsKey(modelSubmissionId)) {
            return null;
        }

        // TODO: adjust when supporting other diagram types than class diagrams
        UMLClassDiagram model = modelIndex.getModelMap().get(modelSubmissionId);
        CompassResult compassResult = model.getLastAssessmentCompassResult();

        if (compassResult == null) {
            return automaticAssessmentController.assessModelAutomatically(model, assessmentIndex);
        }
        return compassResult;
    }

    @Override
    public Collection<Long> getModelIds() {
        return modelIndex.getModelMap().keySet();
    }

    @Override
    public void notifyNewAssessment(List<Feedback> modelingAssessment, long assessedModelSubmissionId) {
        lastUsed = LocalDateTime.now();
        modelSelector.addAlreadyAssessedModel(assessedModelSubmissionId);
        UMLClassDiagram model = modelIndex.getModel(assessedModelSubmissionId);
        if (model == null) {
            log.warn("Cannot add manual assessment to Compass, because the model in modelIndex is null for submission id " + assessedModelSubmissionId);
            return;
        }
        addNewManualAssessment(modelingAssessment, model);
        modelSelector.removeModelWaitingForAssessment(model.getModelSubmissionId());
        assessModelsAutomatically();
    }

    @Override
    public void notifyNewModel(String model, long modelId) {
        lastUsed = LocalDateTime.now();
        // Do not add models that might already exist
        if (modelIndex.getModelMap().containsKey(modelId)) {
            return;
        }
        buildModel(modelId, new JsonParser().parse(model).getAsJsonObject());
    }

    @Override
    public LocalDateTime getLastUsedAt() {
        return lastUsed;
    }

    @Override
    public List<Long> getModelsWaitingForAssessment() {
        return modelSelector.getModelsWaitingForAssessment();
    }

    @Override
    public void removeModelWaitingForAssessment(long modelSubmissionId, boolean isAssessed) {
        modelSelector.removeModelWaitingForAssessment(modelSubmissionId);
        if (isAssessed) {
            modelSelector.addAlreadyAssessedModel(modelSubmissionId);
        }
        else {
            modelSelector.removeAlreadyAssessedModel(modelSubmissionId);
        }
    }

    @Override
    public void markModelAsUnassessed(long modelSubmissionId) {
        modelSelector.removeAlreadyAssessedModel(modelSubmissionId);
    }

    // TODO adapt the parser to support different UML diagrams
    @Override
    public List<Feedback> convertToFeedback(Grade grade, long modelId, Result result) {
        UMLClassDiagram model = this.modelIndex.getModelMap().get(modelId);
        if (model == null) {
            return null;
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

            double elementConfidence = getConfidenceForElement(jsonElementID, modelId);
            if (elementConfidence < ELEMENT_CONFIDENCE_THRESHOLD) {
                log.error("Confidence " + elementConfidence + " of element " + jsonElementID + " is smaller than configured confidence threshold " + ELEMENT_CONFIDENCE_THRESHOLD);
                continue;
            }

            feedback.setCredits(gradePointsEntry.getValue());
            feedback.setPositive(feedback.getCredits() >= 0);
            feedback.setText(grade.getJsonIdCommentsMapping().getOrDefault(jsonElementID, ""));
            feedback.setReference(buildReferenceString(umlElement, jsonElementID));
            feedback.setType(FeedbackType.AUTOMATIC);
            feedback.setResult(result);

            feedbackList.add(feedback);
        }

        // TODO: in the future we want to store this information as well, but for now we
        // ignore it.
        // jsonObject.addProperty(JSONMapping.assessmentElementConfidence,
        // grade.getConfidence());
        // jsonObject.addProperty(JSONMapping.assessmentElementCoverage,
        // grade.getCoverage());

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
    // TODO: I don't think we should expose JSONObject to this internal server
    // class. It would be
    // better to return Java objects here
    @Override
    public JsonObject getStatistics() {
        JsonObject jsonObject = new JsonObject();

        JsonObject uniqueElements = new JsonObject();
        int conflicts = 0;
        for (UMLElement umlElement : this.modelIndex.getUniqueElements()) {
            JsonObject uniqueElement = new JsonObject();
            uniqueElement.addProperty("name", umlElement.getName());
            uniqueElement.addProperty("apollonId", umlElement.getJSONElementID());
            boolean conflict = this.hasConflict(umlElement.getSimilarityID());
            if (conflict) {
                conflicts++;
            }
            uniqueElement.addProperty("conflicts", conflict);
            uniqueElements.add(umlElement.getSimilarityID() + "", uniqueElement);
        }
        jsonObject.add("uniqueElements", uniqueElements);

        jsonObject.addProperty("numberModels", this.modelIndex.getModelCollection().size());
        jsonObject.addProperty("numberConflicts", conflicts);
        jsonObject.addProperty("totalConfidence", this.getTotalConfidence());
        jsonObject.addProperty("totalCoverage", this.getTotalCoverage());

        JsonObject models = new JsonObject();
        for (Map.Entry<Long, UMLClassDiagram> modelEntry : this.getModelMap().entrySet()) {
            JsonObject model = new JsonObject();
            model.addProperty("coverage", modelEntry.getValue().getLastAssessmentCoverage());
            model.addProperty("confidence", modelEntry.getValue().getLastAssessmentConfidence());
            int modelConflicts = 0;
            List<UMLElement> elements = new ArrayList<>();
            elements.addAll(modelEntry.getValue().getClassList());
            elements.addAll(modelEntry.getValue().getAssociationList());
            for (UMLClass umlClass : modelEntry.getValue().getClassList()) {
                elements.addAll(umlClass.getAttributes());
                elements.addAll(umlClass.getMethods());
            }
            for (UMLElement element : elements) {
                boolean modelConflict = this.hasConflict(element.getSimilarityID());
                if (modelConflict) {
                    modelConflicts++;
                }
            }
            model.addProperty("conflicts", modelConflicts);
            model.addProperty("elements", elements.size());
            model.addProperty("classes", elements.stream().filter(umlElement -> umlElement instanceof UMLClass).count());
            model.addProperty("attributes", elements.stream().filter(umlElement -> umlElement instanceof UMLAttribute).count());
            model.addProperty("methods", elements.stream().filter(umlElement -> umlElement instanceof UMLMethod).count());
            model.addProperty("associations", elements.stream().filter(umlElement -> umlElement instanceof UMLClassRelationship).count());
            models.add(modelEntry.getKey().toString(), model);
        }
        jsonObject.add("models", models);

        return jsonObject;
    }

    @Override
    public double getConfidenceForElement(String elementId, long submissionId) {
        UMLClassDiagram model = modelIndex.getModel(submissionId);
        UMLElement element = model.getElementByJSONID(elementId);
        if (element == null) {
            return 0.0;
        }

        Optional<Assessment> optionalAssessment = assessmentIndex.getAssessment(element.getSimilarityID());
        return optionalAssessment.map(assessment -> assessment.getScore(element.getContext()).getConfidence()).orElse(0.0);
    }

    /**
     * Adds the given feedback elements of a manual assessment to the automatic assessment controller where it can be used for automatic assessments.
     *
     * @param modelingAssessment the feedback of a manual assessment
     * @param model              the corresponding model
     */
    private void addNewManualAssessment(List<Feedback> modelingAssessment, UMLClassDiagram model) {
        Map<String, Feedback> feedbackMapping = createElementIdFeedbackMapping(modelingAssessment);
        try {
            automaticAssessmentController.addFeedbacksToAssessment(assessmentIndex, feedbackMapping, model);
        }
        catch (IOException e) {
            log.error("manual assessment for " + model.getName() + " could not be added: " + e.getMessage());
        }
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

    /**
     * Checks if an element with the given id and of the given type exists in the given UML model.
     *
     * @param jsonElementID  the JSON id of the UML element
     * @param umlElementType the type of the UML element
     * @param model          the model to check
     * @return if the element could be found in the given model
     */
    // TODO CZ: move to specific UMLModel class
    private boolean elementExistsInModel(String jsonElementID, String umlElementType, UMLClassDiagram model) {
        if (model == null) {
            return false;
        }
        if (UMLClass.UMLClassType.getTypesAsList().contains(umlElementType)) {
            for (UMLClass umlClass : model.getClassList()) {
                if (umlClass.getJSONElementID().equals(jsonElementID)) {
                    return true;
                }
            }
        }
        else if (umlElementType.equals(UMLAttribute.UML_ATTRIBUTE_TYPE)) {
            for (UMLClass umlClass : model.getClassList()) {
                for (UMLAttribute umlAttribute : umlClass.getAttributes()) {
                    if (umlAttribute.getJSONElementID().equals(jsonElementID)) {
                        return true;
                    }
                }
            }
        }
        else if (umlElementType.equals(UMLMethod.UML_METHOD_TYPE)) {
            for (UMLClass umlClass : model.getClassList()) {
                for (UMLMethod umlMethod : umlClass.getMethods()) {
                    if (umlMethod.getJSONElementID().equals(jsonElementID)) {
                        return true;
                    }
                }
            }
        }
        else if (UMLClassRelationship.UMLRelationType.getTypesAsList().contains(umlElementType)) {
            for (UMLClassRelationship umlClassRelationship : model.getAssociationList()) {
                if (umlClassRelationship.getJSONElementID().equals(jsonElementID)) {
                    return true;
                }
            }
        }
        else if (umlElementType.equals(UMLPackage.UML_PACKAGE_TYPE)) {
            for (UMLPackage umlPackage : model.getPackageList()) {
                if (umlPackage.getJSONElementID().equals(jsonElementID)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasConflict(int elementId) {
        Optional<Assessment> assessment = this.assessmentIndex.getAssessment(elementId);
        if (assessment.isPresent()) {
            for (List<Feedback> feedbacks : assessment.get().getContextFeedbackList().values()) {
                double uniqueScore = -1;
                for (Feedback feedback : feedbacks) {
                    if (uniqueScore != -1 && uniqueScore != feedback.getCredits()) {
                        return true;
                    }
                    uniqueScore = feedback.getCredits();
                }
            }
        }
        return false;
    }

    private boolean scoresAreConsideredEqual(double score1, double score2) {
        return Math.abs(score1 - score2) < Constants.COMPASS_SCORE_EQUALITY_THRESHOLD;
    }

    // Used for internal analysis of metrics
    // TODO MJ unused can be removed??
    void printStatistic() {
        // Variability of solutions
        log.debug("Number of unique elements (without context) == similarity sets: " + this.modelIndex.getNumberOfUniqueElements() + "\n");

        int totalModelElements = 0;
        for (UMLClassDiagram umlModel : this.getUmlModelCollection()) {
            totalModelElements += umlModel.getClassList().size() + umlModel.getAssociationList().size();
            for (UMLClass umlClass : umlModel.getClassList()) {
                totalModelElements += umlClass.getMethods().size() + umlClass.getAttributes().size();
            }
        }

        log.debug("Number of total model elements: " + totalModelElements + "\n");
        double optimalEqu = (totalModelElements * 1.0) / this.getModelMap().size();
        log.debug("Number of optimal similarity sets: " + optimalEqu + "\n");
        log.debug("Variance: " + (this.modelIndex.getNumberOfUniqueElements() - optimalEqu) / (totalModelElements - optimalEqu) + "\n");

        // Total coverage and confidence
        this.automaticAssessmentController.assessModelsAutomatically(modelIndex, assessmentIndex);
        log.debug("Total confidence: " + this.automaticAssessmentController.getTotalConfidence() + "\n");
        log.debug("Total coverage: " + this.automaticAssessmentController.getTotalCoverage() + "\n");

        // Conflicts
        int conflicts = 0;
        double uniqueElementsContext = 0;
        for (Assessment assessment : this.assessmentIndex.getAssessmentsMap().values()) {
            for (List<Feedback> feedbacks : assessment.getContextFeedbackList().values()) {
                uniqueElementsContext++;
                double uniqueScore = -1;
                for (Feedback feedback : feedbacks) {
                    if (uniqueScore != -1 && uniqueScore != feedback.getCredits()) {
                        conflicts++;
                        break;
                    }
                    uniqueScore = feedback.getCredits();
                }
            }
        }
        log.debug("Total conflicts (with context): " + conflicts + "\n");
        log.debug("Relative conflicts (with context): " + (conflicts / uniqueElementsContext) + "\n");
    }
}
