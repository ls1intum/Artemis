package de.tum.in.www1.artemis.service.compass;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.Feedback;
import de.tum.in.www1.artemis.domain.ModelingSubmission;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.enumeration.FeedbackType;
import de.tum.in.www1.artemis.service.compass.assessment.Assessment;
import de.tum.in.www1.artemis.service.compass.assessment.CompassResult;
import de.tum.in.www1.artemis.service.compass.assessment.Score;
import de.tum.in.www1.artemis.service.compass.conflict.Conflict;
import de.tum.in.www1.artemis.service.compass.controller.AssessmentIndex;
import de.tum.in.www1.artemis.service.compass.controller.AutomaticAssessmentController;
import de.tum.in.www1.artemis.service.compass.controller.JSONParser;
import de.tum.in.www1.artemis.service.compass.controller.ModelIndex;
import de.tum.in.www1.artemis.service.compass.controller.ModelSelector;
import de.tum.in.www1.artemis.service.compass.controller.SimilarityDetector;
import de.tum.in.www1.artemis.service.compass.grade.CompassGrade;
import de.tum.in.www1.artemis.service.compass.grade.Grade;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLAssociation;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLAttribute;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLClass;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLElement;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLMethod;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLModel;
import de.tum.in.www1.artemis.service.compass.utils.JSONMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;


public class CompassCalculationEngine implements CalculationEngine {

    private final Logger log = LoggerFactory.getLogger(CompassCalculationEngine.class);

    private ModelIndex modelIndex;
    private AssessmentIndex assessmentIndex;

    private AutomaticAssessmentController automaticAssessmentController;
    private ModelSelector modelSelector;
    private LocalDateTime lastUsed;


    CompassCalculationEngine(Set<ModelingSubmission> submissions) {
        lastUsed = LocalDateTime.now();
        modelIndex = new ModelIndex();
        assessmentIndex = new AssessmentIndex();

        automaticAssessmentController = new AutomaticAssessmentController();
        modelSelector = new ModelSelector(); //TODO MJ fix Bug where on load of exercise no modelsWaitingForAssessment are added ? No differentiation between submitted and saved assessments!

        for (ModelingSubmission submission : submissions) {
            String model = submission.getModel();
            if (model != null) {
                buildModel(submission.getId(), new JsonParser().parse(model).getAsJsonObject());
                buildAssessment(submission);
                modelSelector.addAlreadyAssessedModel(submission.getId());
            }
        }

        assessModelsAutomatically();
    }

    /**
     * @param submissionId       ID of submission the modelingAssessment belongs to
     * @param modelingAssessment assessment to check for conflicts
     * @return a list of conflicts modelingAssessment causes with the current manual assessment data
     */
    public List<Conflict> getConflicts(long submissionId, List<Feedback> modelingAssessment) {
        List<Conflict> conflicts = new ArrayList<>();
        UMLModel model = modelIndex.getModel(submissionId);
        modelingAssessment.forEach(currentFeedback -> {
            UMLElement currentElement = model.getElementByJSONID(currentFeedback.getReferenceElementId()); //TODO MJ return Optional ad throw Exception if no UMLElement found?
            assessmentIndex.getAssessment(currentElement.getElementID()).ifPresent(assessment -> {
                List<Score> scores = assessment.getScores(currentElement.getContext());
                List<Score> scoresInConflict = scores.stream()
                    .filter(score -> !scoresAreConsideredEqual(score.getPoints(), currentFeedback.getCredits()))
                    .collect(Collectors.toList());
                if (!scoresInConflict.isEmpty()) {
                    conflicts.add(new Conflict(currentElement, currentFeedback, scoresInConflict));
                }
            });
        });
        return conflicts;
    }


    private void buildModel(long id, JsonObject jsonModel) {
        try {
            UMLModel model = JSONParser.buildModelFromJSON(jsonModel, id);
            SimilarityDetector.analyzeSimilarity(model, modelIndex);
            modelIndex.addModel(model);
        } catch (IOException e) {
            log.error("Could not load file !", e);
        }
    }


    private void buildAssessment(ModelingSubmission submission) {
        UMLModel model = modelIndex.getModelMap().get(submission.getId());
        if (model == null || submission.getResult() == null || !submission.getResult().getHasFeedback()) {
            log.error("Could not build assessment for submission {}", submission.getId());
            return;
        }
        Map<String, Score> scoreList = getScoresFromFeedbackList(submission.getResult().getFeedbacks(), model);
        addNewManualAssessment(scoreList, model);
        modelSelector.removeModelWaitingForAssessment(model.getModelID());
    }


    /**
     * Create a jsonModelId to Score mapping generated from the feedback list of a submission.
     *
     * @param feedbackList the feedbackList
     * @param model the UML model
     * @return a map of elementIds to scores
     */
    private Map<String, Score> getScoresFromFeedbackList(List<Feedback> feedbackList, UMLModel model) {
        Map<String, Score> scoreHashMap = new HashMap<>();

        for (Feedback feedback : feedbackList) {
            String jsonElementId = feedback.getReferenceElementId();
            if (!model.containsElement(jsonElementId)) {
                // This might happen if e.g. the user input was malformed and the compass model parser had to ignore the element
                log.warn("Element " + jsonElementId + " not found in model");
                continue;
            }

            // Ignore misformatted score
            try {
                Score score = new Score(feedback.getCredits(), Collections.singletonList(feedback.getText()), 1.0);
                scoreHashMap.put(jsonElementId, score);
            } catch (Exception e) {
                log.error(e.getMessage(), e);
            }
        }

        return scoreHashMap;
    }


    protected Collection<UMLModel> getUmlModelCollection() {
        return modelIndex.getModelCollection();
    }


    protected Map<Long, UMLModel> getModelMap() {
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


    private void addNewManualAssessment(Map<String, Score> scoreHashMap, UMLModel model) {
        try {
            automaticAssessmentController.addScoresToAssessment(assessmentIndex, scoreHashMap, model);
        } catch (IOException e) {
            log.error("manual assessment for " + model.getName() + " could not be added: " + e.getMessage());
        }
    }


    /**
     * @return id of the next optimal model or null if all models are completely assessed
     */
    @Override
    public Map.Entry<Long, Grade> getNextOptimalModel() {
        lastUsed = LocalDateTime.now();
        Long optimalModelId = modelSelector.selectNextModel(modelIndex);
        if (optimalModelId == null) {
            return null;
        }
        Grade grade = getResultForModel(optimalModelId);
        // Should never happen
        if (grade == null) {
            grade = new CompassGrade();
        }
        return new AbstractMap.SimpleEntry<>(optimalModelId, grade);
    }


    @Override
    public Grade getResultForModel(long modelId) {
        lastUsed = LocalDateTime.now();
        if (!modelIndex.getModelMap().containsKey(modelId)) {
            return null;
        }

        UMLModel model = modelIndex.getModelMap().get(modelId);
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
    public void notifyNewAssessment(List<Feedback> modelingAssessment, long submissionId) {
        lastUsed = LocalDateTime.now();
        modelSelector.addAlreadyAssessedModel(submissionId);
        UMLModel model = modelIndex.getModel(submissionId);
        addNewManualAssessment(modelingAssessment, model);
        modelSelector.removeModelWaitingForAssessment(model.getModelID());
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
    public Map<Long, Grade> getModelsWaitingForAssessment() {
        Map<Long, Grade> optimalModels = new HashMap<>();
        for (long modelId : modelSelector.getModelsWaitingForAssessment()) {
            optimalModels.put(modelId, getResultForModel(modelId));
        }
        return optimalModels;
    }


    @Override
    public void removeModelWaitingForAssessment(long modelId, boolean isAssessed) {
        modelSelector.removeModelWaitingForAssessment(modelId);
        if (!isAssessed && (modelIndex.getModelMap().get(modelId) == null ||
            !modelIndex.getModelMap().get(modelId).isEntirelyAssessed())) {
            modelSelector.removeAlreadyAssessedModel(modelId);
        } else if (isAssessed) {
            modelSelector.addAlreadyAssessedModel(modelId);
        }
    }


    // TODO adapt the parser to support different UML diagrams
    @Override
    public List<Feedback> convertToFeedback(Grade grade, long modelId, Result result) {
        UMLModel model = this.modelIndex.getModelMap().get(modelId);
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

            feedback.setCredits(gradePointsEntry.getValue());
            feedback.setPositive(feedback.getCredits() >= 0);
            feedback.setText(grade.getJsonIdCommentsMapping().getOrDefault(jsonElementID, ""));
            feedback.setReference(buildReferenceString(umlElement, jsonElementID));
            feedback.setType(FeedbackType.AUTOMATIC);
            feedback.setResult(result);

            feedbackList.add(feedback);
        }

        //TODO: in the future we want to store this information as well, but for now we ignore it.
//        jsonObject.addProperty(JSONMapping.assessmentElementConfidence, grade.getConfidence());
//        jsonObject.addProperty(JSONMapping.assessmentElementCoverage, grade.getCoverage());

        return feedbackList;
    }

    /**
     * Creates the reference string to be stored in an Feedback element for modeling submissions. The reference of a
     * modeling Feedback stores the type of the corresponding UML element and its jsonElementId and is of the form
     * "<UmlElementType>:<jsonElementIds>".
     *
     * @param umlElement    the UML model element the Feedback belongs to
     * @param jsonElementId the jsonElementId of the UML model element
     * @return the formatted reference string containing the element type and its jsonElementId
     */
    private String buildReferenceString(UMLElement umlElement, String jsonElementId) {
        // TODO find cleaner solution
        String type = umlElement.getClass().getSimpleName();
        switch (type) {
            case "UMLClass":
                type = JSONMapping.assessmentElementTypeClass;
                break;
            case "UMLAttribute":
                type = JSONMapping.assessmentElementTypeAttribute;
                break;
            case "UMLAssociation":
                type = JSONMapping.assessmentElementTypeRelationship;
                break;
            case "UMLMethod":
                type = JSONMapping.assessmentElementTypeMethod;
                break;
            default:
                type = "";
        }
        return type + ":" + jsonElementId;
    }


    /**
     * format:
     * uniqueElements
     * [{id}
     * name
     * apollonId
     * conflicts]
     * numberModels
     * numberConflicts
     * totalConfidence
     * totalCoverage
     * models
     * [{id}
     * confidence
     * coverage
     * conflicts]
     *
     * @return statistics about the UML model
     */
    //TODO: I don't think we should expose JSONObject to this internal server class. It would be better to return Java objects here
    @Override
    public JsonObject getStatistics() {
        JsonObject jsonObject = new JsonObject();

        JsonObject uniqueElements = new JsonObject();
        int conflicts = 0;
        for (UMLElement umlElement : this.modelIndex.getUniqueElements()) {
            JsonObject uniqueElement = new JsonObject();
            uniqueElement.addProperty("name", umlElement.getName());
            uniqueElement.addProperty("apollonId", umlElement.getJSONElementID());
            boolean conflict = this.hasConflict(umlElement.getElementID());
            if (conflict) {
                conflicts++;
            }
            uniqueElement.addProperty("conflicts", conflict);
            uniqueElements.add(umlElement.getElementID() + "", uniqueElement);
        }
        jsonObject.add("uniqueElements", uniqueElements);

        jsonObject.addProperty("numberModels", this.modelIndex.getModelCollection().size());
        jsonObject.addProperty("numberConflicts", conflicts);
        jsonObject.addProperty("totalConfidence", this.getTotalConfidence());
        jsonObject.addProperty("totalCoverage", this.getTotalCoverage());

        JsonObject models = new JsonObject();
        for (Map.Entry<Long, UMLModel> modelEntry : this.getModelMap().entrySet()) {
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
                boolean modelConflict = this.hasConflict(element.getElementID());
                if (modelConflict) {
                    modelConflicts++;
                }
            }
            model.addProperty("conflicts", modelConflicts);
            model.addProperty("elements", elements.size());
            model.addProperty("classes", elements.stream().filter(umlElement -> umlElement instanceof UMLClass).count());
            model.addProperty("attributes", elements.stream().filter(umlElement -> umlElement instanceof UMLAttribute).count());
            model.addProperty("methods", elements.stream().filter(umlElement -> umlElement instanceof UMLMethod).count());
            model.addProperty("associations", elements.stream().filter(umlElement -> umlElement instanceof UMLAssociation).count());
            models.add(modelEntry.getKey().toString(), model);
        }
        jsonObject.add("models", models);

        return jsonObject;
    }

    private void addNewManualAssessment(List<Feedback> modelingAssessment, UMLModel model) {
        Map<String, Score> scoreList = createScoreList(modelingAssessment, model);
        try {
            automaticAssessmentController.addScoresToAssessment(assessmentIndex, scoreList, model);
        } catch (IOException e) {
            log.error("manual assessment for " + model.getName() + " could not be added: " + e.getMessage());
        }
    }

    /**
     * Checks if each Feedback corresponds to an element in the UMLModel and returns a mapping from each
     * jsonElementID in the Assessment to its Score.
     *
     * @param modelingAssessment the modeling assessment to create the score list of
     * @param model              the UmlModel the modelingAssessment belongs to
     * @return mapping of the jsonElementID of each ModelElement contained in the modelingAssessment to its corresponding score
     */
    private Map<String, Score> createScoreList(List<Feedback> modelingAssessment, UMLModel model) {
        Map<String, Score> scoreHashMap = new HashMap<>();

        for (Feedback feedback : modelingAssessment) {
            String jsonElementID = feedback.getReferenceElementId();
            String umlElementType = feedback.getReferenceElementType();
            if (!elementExistsInModel(jsonElementID, umlElementType, model)) {
                /*
                 * This might happen if e.g. the user input was malformed and the compass model parser had to ignore the element
                 */
                log.warn("Element " + jsonElementID + " of type " + umlElementType + " not in model");
                continue;
            }

            List<String> comment = new ArrayList<>();
            String feedbackText = feedback.getText();
            if (feedbackText != null && !feedbackText.trim().isEmpty()) {
                comment.add(feedbackText);
            }

            // Ignore malformed score
            try {
                Score score = new Score(feedback.getCredits(), comment, 1.0);
                scoreHashMap.put(jsonElementID, score);
            } catch (Exception e) {
                log.error(e.getMessage(), e);
            }
        }
        return scoreHashMap;
    }

    /**
     * Checks if an element with the given id and of the given type exists in the given UML model.
     *
     * @param jsonElementID  the JSON id of the UML element
     * @param umlElementType the type of the UML element
     * @param model          the model to check
     * @return if the element could be found in the given model
     */
    private boolean elementExistsInModel(String jsonElementID, String umlElementType, UMLModel model) {
        boolean elementExists = false;

        switch (umlElementType) {
            case JSONMapping.assessmentElementTypeClass:
                for (UMLClass umlClass : model.getClassList()) {
                    if (umlClass.getJSONElementID().equals(jsonElementID)) {
                        elementExists = true;
                        break;
                    }
                }
                break;
            case JSONMapping.assessmentElementTypeAttribute:
                for (UMLClass umlClass : model.getClassList()) {
                    for (UMLAttribute umlAttribute : umlClass.getAttributes()) {
                        if (umlAttribute.getJSONElementID().equals(jsonElementID)) {
                            elementExists = true;
                            break;
                        }
                    }
                }
                break;
            case JSONMapping.assessmentElementTypeMethod:
                for (UMLClass umlClass : model.getClassList()) {
                    for (UMLMethod umlMethod : umlClass.getMethods()) {
                        if (umlMethod.getJSONElementID().equals(jsonElementID)) {
                            elementExists = true;
                            break;
                        }
                    }
                }
                break;
            case JSONMapping.assessmentElementTypeRelationship:
                for (UMLAssociation umlAssociation : model.getAssociationList()) {
                    if (umlAssociation.getJSONElementID().equals(jsonElementID)) {
                        elementExists = true;
                        break;
                    }
                }
                break;
        }
        return elementExists;
    }


    private boolean hasConflict(int elementId) {
        Optional<Assessment> assessment = this.assessmentIndex.getAssessment(elementId);
        if (assessment.isPresent()) {
            for (List<Score> scores : assessment.get().getContextScoreList().values()) {
                double uniqueScore = -1;
                for (Score score : scores) {
                    if (uniqueScore != -1 && uniqueScore != score.getPoints()) {
                        return true;
                    }
                    uniqueScore = score.getPoints();
                }
            }
        }
        return false;
    }


    private boolean scoresAreConsideredEqual(double score1, double score2) {
        return Math.abs(score1 - score2) < Constants.COMPASS_SCORE_EQUALITY_THRESHOLD;
    }


    // Used for internal analysis of metrics
    void printStatistic() {
        // Variability of solutions
        log.debug("Number of unique elements (without context) == similarity sets: " +
            this.modelIndex.getNumberOfUniqueElements() + "\n");

        int totalModelElements = 0;
        for (UMLModel umlModel : this.getUmlModelCollection()) {
            totalModelElements += umlModel.getClassList().size() + umlModel.getAssociationList().size();
            for (UMLClass umlClass : umlModel.getClassList()) {
                totalModelElements += umlClass.getMethods().size() + umlClass.getAttributes().size();
            }
        }

        log.debug("Number of total model elements: " + totalModelElements + "\n");
        double optimalEqu = (totalModelElements * 1.0) / this.getModelMap().size();
        log.debug("Number of optimal similarity sets: " + optimalEqu + "\n");
        log.debug("Variance: " +
            (this.modelIndex.getNumberOfUniqueElements() - optimalEqu) / (totalModelElements - optimalEqu) + "\n");

        // Total coverage and confidence
        this.automaticAssessmentController.assessModelsAutomatically(modelIndex, assessmentIndex);
        log.debug("Total confidence: " + this.automaticAssessmentController.getTotalConfidence() + "\n");
        log.debug("Total coverage: " + this.automaticAssessmentController.getTotalCoverage() + "\n");

        // Conflicts
        int conflicts = 0;
        double uniqueElementsContext = 0;
        for (Assessment assessment : this.assessmentIndex.getAssessmentsMap().values()) {
            for (List<Score> scores : assessment.getContextScoreList().values()) {
                uniqueElementsContext++;
                double uniqueScore = -1;
                for (Score score : scores) {
                    if (uniqueScore != -1 && uniqueScore != score.getPoints()) {
                        conflicts++;
                        break;
                    }
                    uniqueScore = score.getPoints();
                }
            }
        }
        log.debug("Total conflicts (with context): " + conflicts + "\n");
        log.debug("Relative conflicts (with context): " + (conflicts / uniqueElementsContext) + "\n");
    }
}
