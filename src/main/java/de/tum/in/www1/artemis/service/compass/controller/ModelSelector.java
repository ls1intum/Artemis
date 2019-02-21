package de.tum.in.www1.artemis.service.compass.controller;

import de.tum.in.www1.artemis.service.compass.umlmodel.UMLModel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

public class ModelSelector {

    private static final int MAX_CANDIDATE_LIST_SIZE = 50;

    private HashSet<Long> modelsWaitingForAssessment = new HashSet<>();
    private HashSet<Long> alreadyAssessedModels = new HashSet<>();

    /**
     * Calculate the model which would mean the biggest knowledge gain to support the automatic assessment process
     * The selected model is currently unassessed and not queued into assessment
     *
     * @param modelIndex manages the models
     * @return the id of the model which should be assessed next by an assessor
     */
    public Long selectNextModel(ModelIndex modelIndex) {
        double threshold = 0.15;
        int maxCandidateListSize = 10;

        List<UMLModel> partiallyAssessed = new ArrayList<>();
        for (UMLModel umlModel: modelIndex.getModelCollection()) {
            if (!alreadyAssessedModels.contains(umlModel.getModelID()) && !umlModel.isEntirelyAssessed()) {
                partiallyAssessed.add(umlModel);
            }
        }

        // Make sure that the candidate list is not too big
        List<UMLModel> candidates = partiallyAssessed;

        candidates.sort(Comparator.comparingDouble(UMLModel::getLastAssessmentCoverage));

        if (!candidates.isEmpty()) {
            double smallestCoverage = candidates.get(0).getLastAssessmentCoverage();

            if (smallestCoverage < 1) {
                while (maxCandidateListSize + 5 < candidates.size()
                    && smallestCoverage > (candidates.get(maxCandidateListSize).getLastAssessmentCoverage() - threshold)
                    && maxCandidateListSize < MAX_CANDIDATE_LIST_SIZE) {
                    maxCandidateListSize += 5;
                }

                candidates = candidates.subList(0, Math.min(candidates.size(), maxCandidateListSize));
            }
        }

        // select a model which covers many other models (= high similarity to others)
        Long selectedCandidateId = null;
        double lastMeanSimilarity = 0;
        for (UMLModel candidate : candidates) {
            double similarity = 0;
            for (UMLModel model : partiallyAssessed) {
                similarity += model.similarity(candidate);
            }

            //similarity /= modelIndex.getModelList().size();
            similarity /= partiallyAssessed.size();

            if (similarity > lastMeanSimilarity) {
                selectedCandidateId = candidate.getModelID();
                lastMeanSimilarity = similarity;
            }
        }

        if (selectedCandidateId != null) {
            alreadyAssessedModels.add(selectedCandidateId);
            modelsWaitingForAssessment.add(selectedCandidateId);
            return selectedCandidateId;
        }

        // if none exists, select any unassessed model
        for (UMLModel model : modelIndex.getModelCollection()) {
            if (model.isUnassessed() && !alreadyAssessedModels.contains(model.getModelID())) {
                alreadyAssessedModels.add(model.getModelID());
                modelsWaitingForAssessment.add(model.getModelID());
                return model.getModelID();
            }
        }

        // Do not reassess already assessed models as this will lead to confusion
        // if all models are assessed, select any poorly assessed model
        /*for (UMLModel model : modelIndex.getModelCollection()) {
            if (model.getLastAssessmentConfidence() < CompassConfiguration.POORLY_ASSESSED_MODEL_THRESHOLD &&
                !alreadyAssessedModels.contains(model.getModelID())) {
                alreadyAssessedModels.add(model.getModelID());
                modelsWaitingForAssessment.add(model.getModelID());
                return model.getModelID();
            }
        }*/
        return null;
    }

    public List<Long> getModelsWaitingForAssessment() {
        return new ArrayList<>(modelsWaitingForAssessment);
    }

    public void addAlreadyAssessedModel(long modelId) {
        alreadyAssessedModels.add(modelId);
    }

    public void removeModelWaitingForAssessment(long modelId) {
        modelsWaitingForAssessment.remove(modelId);
    }

    public void removeAlreadyAssessedModel(long modelId) {
        alreadyAssessedModels.remove(modelId);
    }

}
