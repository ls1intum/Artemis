package de.tum.in.www1.artemis.service.compass;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.tum.in.www1.artemis.service.compass.assessment.Result;
import de.tum.in.www1.artemis.service.compass.assessment.Score;
import de.tum.in.www1.artemis.service.compass.controller.*;
import de.tum.in.www1.artemis.service.compass.grade.CompassGrade;
import de.tum.in.www1.artemis.service.compass.grade.Grade;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.AbstractMap;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;


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

    Collection<UMLModel> getUmlModelCollection() {
        return modelIndex.getModelCollection();
    }

    double getTotalCoverage() {
        return automaticAssessmentController.getTotalCoverage();
    }

    double getTotalConfidence() {
        return automaticAssessmentController.getTotalConfidence();
    }

    void assessModelsAutomatically() {
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
        Result result = model.getLastAssessmentResult();

        if (result == null) {
            return automaticAssessmentController.assessModelAutomatically(model, assessmentIndex);
        }
        return result;
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
}
