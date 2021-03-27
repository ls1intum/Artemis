package de.tum.in.www1.artemis.service.compass.controller;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import de.tum.in.www1.artemis.service.compass.umlmodel.UMLDiagram;

public class ModelSelector {

    /**
     * Maximal number of models that are considered for calculating the next optimal models that need to be assessed, i.e. we take the MAX_CANDIDATE_LIST_SIZE models with the
     * lowest coverage as candidates for the next optimal models at max. These candidates are then further processed (see selectNextModels method). We use this limit to prevent
     * that the selection of the next optimal models takes too much time.
     */
    private static final int MAX_CANDIDATE_LIST_SIZE = 50;

    /**
     * This is used when calculating the models with the highest similarity to all other models. The models are stored together with their calculated similarity in a
     * SortedMap<Double, Long> that maps similarity -> modelId (see computeModelsWithHighestSimilarity method). We add this small amount to every similarity to prevent duplicates.
     * E.g if all models are exactly the same, their similarity is exactly the same as well. This would result in only one element in the sorted map as duplicate keys are not
     * permitted. So we add a small epsilon value that does not impact the order of the similarities, but prevents these duplicate similarity values.
     */
    private static final double EPSILON = 0.0000001;

    /**
     * Tracks which models have been selected for assessment. Typically these models are the ones where Compass learns the most, when they are assessed. All models in this set do
     * not have a complete assessment. Models get removed from this set when they are locked by a tutor for assessment or a manual (complete) assessment exists. The key is the
     * ModelSubmission id.
     */
    private Set<Long> modelsWaitingForAssessment = ConcurrentHashMap.newKeySet();

    /**
     * Tracks which models have already been assessed completely or which have been marked as optimal before (they do not necessarily need to be completely assessed though).
     * Basically this set contains all modelsWaitingForAssessment + all models that are already assessed. Models that are in this set are not considered by the ModelSelector when
     * selecting the next optimal model. The key is the ModelSubmission id.
     */
    private Set<Long> alreadyHandledModels = ConcurrentHashMap.newKeySet();

    private AutomaticAssessmentController automaticAssessmentController;

    public ModelSelector(AutomaticAssessmentController automaticAssessmentController) {
        this.automaticAssessmentController = automaticAssessmentController;
    }

    /**
     * Calculate the given number of models which would mean the biggest knowledge gain to support the automatic assessment process. The selected models are currently unassessed
     * and not queued for assessment (i.e. in alreadyHandledModels). Which models mean the biggest knowledge gain is decided based on the coverage and the mean similarity of the
     * models, i.e. models that have a low coverage but a high mean similarity with reference to all other models are considered "optimal" and will be returned.
     *
     * @param modelIndex     contains and manages all the models
     * @param numberOfModels the number of models that should be loaded
     * @return the ids of the models which should be assessed next by an assessor, or an empty list if there are no unhandled models
     */
    public List<Long> selectNextModels(ModelIndex modelIndex, int numberOfModels) {
        double threshold = 0.15;
        int maxCandidateListSize = 10;

        // Get all models that have not already been handled by the model selector
        List<UMLDiagram> unhandledModels = new ArrayList<>();
        for (UMLDiagram umlModel : modelIndex.getModelCollection()) {
            if (!alreadyHandledModels.contains(umlModel.getModelSubmissionId())) {
                unhandledModels.add(umlModel);
            }
        }

        List<UMLDiagram> candidates = unhandledModels;
        candidates.sort(Comparator.comparingDouble(model -> automaticAssessmentController.getLastAssessmentCoverage(model.getModelSubmissionId())));
        // Make sure that the candidate list is not too big
        if (!candidates.isEmpty()) {
            double smallestCoverage = automaticAssessmentController.getLastAssessmentCoverage(candidates.get(0).getModelSubmissionId());

            if (smallestCoverage < 1) {
                while (maxCandidateListSize + 5 < candidates.size()
                        && smallestCoverage > (automaticAssessmentController.getLastAssessmentCoverage(candidates.get(maxCandidateListSize).getModelSubmissionId()) - threshold)
                        && maxCandidateListSize < MAX_CANDIDATE_LIST_SIZE) {
                    maxCandidateListSize += 5;
                }
            }

            candidates = candidates.subList(0, Math.min(candidates.size(), maxCandidateListSize));
        }

        List<Long> nextOptimalModels = computeModelsWithHighestSimilarity(numberOfModels, candidates, unhandledModels);

        if (!nextOptimalModels.isEmpty()) {
            alreadyHandledModels.addAll(nextOptimalModels);
            modelsWaitingForAssessment.addAll(nextOptimalModels);

            return nextOptimalModels;
        }

        // Fallback: if no optimal models could be determined by similarity, select any unassessed models
        for (UMLDiagram model : modelIndex.getModelCollection()) {
            if (automaticAssessmentController.isUnassessed(model.getModelSubmissionId()) && !alreadyHandledModels.contains(model.getModelSubmissionId())) {
                alreadyHandledModels.add(model.getModelSubmissionId());
                modelsWaitingForAssessment.add(model.getModelSubmissionId());

                return Collections.singletonList(model.getModelSubmissionId());
            }
        }

        return new ArrayList<>();
    }

    /**
     * Computes and returns the given number of candidate models with the highest mean similarity, i.e. for every model in the given list of candidate models, it calculates the
     * mean similarity compared to all models in the given list of unhandled models and sorts the candidate models according to the calculated mean similarity. I then returns the
     * given number of candidate models with the highest mean similarity.
     *
     * @param numberOfModels  the number of models that should be returned
     * @param candidates      the candidate models for which to calculate the mean similarity
     * @param unhandledModels the unhandled models used to calculate the mean similarity of the candidate models
     * @return the given number of candidate models with the highest mean similarity
     */
    private List<Long> computeModelsWithHighestSimilarity(int numberOfModels, List<UMLDiagram> candidates, List<UMLDiagram> unhandledModels) {
        if (numberOfModels == 0 || candidates == null || candidates.isEmpty() || unhandledModels == null || unhandledModels.isEmpty()) {
            return new ArrayList<>();
        }

        // Map similarity -> submissionId that is sorted by the similarity. Note, that the map is sorted in reverse order, i.e. highest similarity comes first.
        SortedMap<Double, Long> sortedSimilarityMap = new TreeMap<>(Collections.reverseOrder());
        double epsilon = EPSILON;

        for (UMLDiagram candidate : candidates) {
            double similarity = 0;

            for (UMLDiagram model : unhandledModels) {
                similarity += model.similarity(candidate);
            }

            similarity /= unhandledModels.size();
            // We add a small amount to every similarity to prevent duplicates. E.g if all models are exactly the same, their similarity is exactly the same as well. This would
            // result in only one element in the sorted map as duplicate keys are not permitted. So we add a small amount that does not impact the order of the similarities.
            similarity += epsilon;
            sortedSimilarityMap.put(similarity, candidate.getModelSubmissionId());

            epsilon += EPSILON;
        }

        return sortedSimilarityMap.values().stream().limit(numberOfModels).collect(Collectors.toList());
    }

    public List<Long> getModelsWaitingForAssessment() {
        return new ArrayList<>(modelsWaitingForAssessment);
    }

    public void addAlreadyHandledModel(long modelId) {
        alreadyHandledModels.add(modelId);
    }

    public void removeModelWaitingForAssessment(long modelId) {
        modelsWaitingForAssessment.remove(modelId);
    }

    public void removeAlreadyHandledModel(long modelId) {
        alreadyHandledModels.remove(modelId);
    }
}
