package de.tum.in.www1.artemis.service.compass.controller;

import java.util.*;

import de.tum.in.www1.artemis.service.compass.umlmodel.classdiagram.UMLClassDiagram;

public class ModelSelector {

    private static final int MAX_CANDIDATE_LIST_SIZE = 50;

    /**
     * Tracks which models have been selected for assessment. Typically these models are the ones where Compass learns the most, when they are assessed. All models in this set do
     * not have a complete assessment. Models get removed from this set when they are locked by a tutor for assessment or a manual (complete) assessment exists. The key is the
     * ModelSubmission id.
     */
    private Set<Long> modelsWaitingForAssessment = new HashSet<>();

    /**
     * Tracks which models have already been assessed completely or which have been marked as optimal before (they do not necessarily need to be completely assessed though). Models
     * that are in this set are not considered by the ModelSelector when selecting the next optimal model. The key is the ModelSubmission id.
     */
    private Set<Long> alreadyAssessedModels = new HashSet<>();

    /**
     * Calculate the model which would mean the biggest knowledge gain to support the automatic assessment process The selected model is currently unassessed and not queued into
     * assessment (i.e. in alreadyAssessedModels)
     *
     * @param modelIndex manages the models
     * @return the id of the model which should be assessed next by an assessor
     */
    public Long selectNextModel(ModelIndex modelIndex) {
        double threshold = 0.15;
        int maxCandidateListSize = 10;

        List<UMLClassDiagram> partiallyAssessed = new ArrayList<>();
        for (UMLClassDiagram umlModel : modelIndex.getModelCollection()) {
            if (!alreadyAssessedModels.contains(umlModel.getModelSubmissionId())) {
                partiallyAssessed.add(umlModel);
            }
        }

        // Make sure that the candidate list is not too big
        List<UMLClassDiagram> candidates = partiallyAssessed;

        candidates.sort(Comparator.comparingDouble(UMLClassDiagram::getLastAssessmentCoverage));

        if (!candidates.isEmpty()) {
            double smallestCoverage = candidates.get(0).getLastAssessmentCoverage();

            if (smallestCoverage < 1) {
                while (maxCandidateListSize + 5 < candidates.size() && smallestCoverage > (candidates.get(maxCandidateListSize).getLastAssessmentCoverage() - threshold)
                        && maxCandidateListSize < MAX_CANDIDATE_LIST_SIZE) {
                    maxCandidateListSize += 5;
                }

                candidates = candidates.subList(0, Math.min(candidates.size(), maxCandidateListSize));
            }
        }

        // select a model which covers many other models (= high similarity to others)
        Long selectedCandidateId = null;
        double lastMeanSimilarity = 0;
        for (UMLClassDiagram candidate : candidates) {
            double similarity = 0;
            for (UMLClassDiagram model : partiallyAssessed) {
                similarity += model.similarity(candidate);
            }

            // similarity /= modelIndex.getModelList().size();
            similarity /= partiallyAssessed.size();

            if (similarity > lastMeanSimilarity) {
                selectedCandidateId = candidate.getModelSubmissionId();
                lastMeanSimilarity = similarity;
            }
        }

        if (selectedCandidateId != null) {
            alreadyAssessedModels.add(selectedCandidateId);
            modelsWaitingForAssessment.add(selectedCandidateId);
            return selectedCandidateId;
        }

        // if none exists, select any unassessed model
        for (UMLClassDiagram model : modelIndex.getModelCollection()) {
            if (model.isUnassessed() && !alreadyAssessedModels.contains(model.getModelSubmissionId())) {
                alreadyAssessedModels.add(model.getModelSubmissionId());
                modelsWaitingForAssessment.add(model.getModelSubmissionId());
                return model.getModelSubmissionId();
            }
        }

        // Do not reassess already assessed models as this will lead to confusion
        // if all models are assessed, select any poorly assessed model
        /*
         * for (UMLModel model : modelIndex.getModelCollection()) { if (model.getLastAssessmentConfidence() < CompassConfiguration.POORLY_ASSESSED_MODEL_THRESHOLD &&
         * !alreadyAssessedModels.contains(model.getModelSubmissionId())) { alreadyAssessedModels.add(model.getModelSubmissionId());
         * modelsWaitingForAssessment.add(model.getModelSubmissionId()); return model.getModelSubmissionId(); } }
         */
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
