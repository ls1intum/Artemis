package de.tum.in.www1.artemis.service.compass;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.tum.in.www1.artemis.service.compass.assessment.*;
import de.tum.in.www1.artemis.service.compass.controller.*;
import de.tum.in.www1.artemis.service.compass.grade.*;
import de.tum.in.www1.artemis.service.compass.umlmodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;


public class CompassCalculationEngine implements CalculationEngine {

    private final Logger log = LoggerFactory.getLogger(CompassCalculationEngine.class);

    private ModelIndex modelIndex;
    private AssessmentIndex assessmentIndex;

    private AutomaticAssessmentController automaticAssessmentController;
    private ModelSelector modelSelector;
    private LocalDateTime lastUsed;

    CompassCalculationEngine(Map<Long, JsonObject> models, Map<Long, JsonObject> assessments) {
        lastUsed = LocalDateTime.now();
        modelIndex = new ModelIndex();
        assessmentIndex = new AssessmentIndex();

        automaticAssessmentController = new AutomaticAssessmentController();
        modelSelector = new ModelSelector();

        for (Map.Entry<Long, JsonObject> entry: models.entrySet()) {
            buildModel(entry.getKey(), entry.getValue());
        }
        for (Map.Entry<Long, JsonObject> entry: assessments.entrySet()) {
            buildAssessment(entry.getKey(), entry.getValue());
            modelSelector.addAlreadyAssessedModel(entry.getKey());
        }

        assessModelsAutomatically();
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

    private void buildAssessment(long id, JsonObject jsonAssessment) {
        UMLModel model = modelIndex.getModelMap().get(id);
        if (model == null) {
            return;
        }
        Map<String, Score> scoreList = JSONParser.getScoresFromJSON(jsonAssessment, model);
        this.addNewManualAssessment(scoreList, model);
        modelSelector.removeModelWaitingForAssessment(model.getModelID());
    }

    private Collection<UMLModel> getUmlModelCollection() {
        return modelIndex.getModelCollection();
    }

    private Map<Long, UMLModel> getModelMap() {
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
     *
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
    public void notifyNewAssessment(String assessment, long modelId) {
        lastUsed = LocalDateTime.now();
        modelSelector.addAlreadyAssessedModel(modelId);
        buildAssessment(modelId, new JsonParser().parse(assessment).getAsJsonObject());
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
        for(long modelId: modelSelector.getModelsWaitingForAssessment()) {
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

    @Override
    public JsonObject exportToJson(Grade grade, long modelId) {
        UMLModel model = this.modelIndex.getModelMap().get(modelId);
        if (model == null) {
            return null;
        }
        return JSONParser.exportToJSON(grade, model);
    }

    /**
     * format:
     *  uniqueElements
     *      [{id}
     *          name
     *          apollonId
     *          conflict]
     *  numberModels
     *  numberConflicts
     *  totalConfidence
     *  totalCoverage
     *  models
     *      [{id}
     *          confidence
     *          coverage
     *          conflicts]
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
                conflicts ++;
            }
            uniqueElement.addProperty("conflict", conflict);
            uniqueElements.add(umlElement.getElementID() + "", uniqueElement);
        }
        jsonObject.add("uniqueElements", uniqueElements);

        jsonObject.addProperty("numberModels", this.modelIndex.getModelCollection().size());
        jsonObject.addProperty("numberConflicts", conflicts);
        jsonObject.addProperty("totalConfidence", this.getTotalConfidence());
        jsonObject.addProperty("totalCoverage", this.getTotalCoverage());

        JsonObject models = new JsonObject();
        for (Map.Entry<Long, UMLModel> modelEntry: this.getModelMap().entrySet()) {
            JsonObject model = new JsonObject();
            model.addProperty("coverage", modelEntry.getValue().getLastAssessmentCoverage());
            model.addProperty("confidence", modelEntry.getValue().getLastAssessmentCoverage());
            int modelConflicts = 0;
            List<UMLElement> elements = new ArrayList<>();
            elements.addAll(modelEntry.getValue().getClassList());
            elements.addAll(modelEntry.getValue().getAssociationList());
            for (UMLClass umlClass: modelEntry.getValue().getClassList()) {
                elements.addAll(umlClass.getAttributeList());
                elements.addAll(umlClass.getMethodList());
            }
            for (UMLElement element: elements) {
                boolean modelConflict = this.hasConflict(element.getElementID());
                if (modelConflict) {
                    modelConflicts++;
                }
            }
            model.addProperty("conflicts", modelConflicts);
            model.addProperty("elements", elements.size());
            model.addProperty("classes", elements.stream().filter(umlElement -> umlElement instanceof  UMLClass).count());
            model.addProperty("attributes", elements.stream().filter(umlElement -> umlElement instanceof UMLAttribute).count());
            model.addProperty("methods", elements.stream().filter(umlElement -> umlElement instanceof UMLMethod).count());
            model.addProperty("associations", elements.stream().filter(umlElement -> umlElement instanceof UMLAssociation).count());
            models.add(modelEntry.getKey().toString(), model);
        }
        jsonObject.add("models", models);

        return jsonObject;
    }

    private boolean hasConflict(int elementId) {
        boolean conflict = false;
        Assessment assessment = this.assessmentIndex.getAssessmentsMap().get(elementId);
        if (assessment == null) {
            return false;
        }
        for (List<Score> scores: this.assessmentIndex.getAssessmentsMap().get(elementId).getContextScoreList().values()) {
            double uniqueScore = -1;
            for (Score score: scores) {
                if (uniqueScore != -1 && uniqueScore != score.getPoints()) {
                    conflict = true;
                    break;
                }
                uniqueScore = score.getPoints();
            }
        }
        return conflict;
    }

    // Used for internal analysis of metrics
    void printStatistic() {
        // Variability of solutions
        log.debug("Number of unique elements (without context) == similarity sets: " +
            this.modelIndex.getNumberOfUniqueElements() + "\n");

        int totalModelElements = 0;
        for (UMLModel umlModel: this.getUmlModelCollection()) {
            totalModelElements += umlModel.getClassList().size() + umlModel.getAssociationList().size();
            for (UMLClass umlClass: umlModel.getClassList()) {
                totalModelElements += umlClass.getMethodList().size() + umlClass.getAttributeList().size();
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
        for(Assessment assessment: this.assessmentIndex.getAssessmentsMap().values()) {
            for(List<Score> scores: assessment.getContextScoreList().values()) {
                uniqueElementsContext ++;
                double uniqueScore = -1;
                for (Score score: scores) {
                    if (uniqueScore != -1 && uniqueScore != score.getPoints()) {
                        conflicts ++;
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
