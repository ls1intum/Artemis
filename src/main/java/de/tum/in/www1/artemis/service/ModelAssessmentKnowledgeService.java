package de.tum.in.www1.artemis.service;

import java.util.Set;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.ModelAssessmentKnowledge;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.repository.ModelAssessmentKnowledgeRepository;
import de.tum.in.www1.artemis.repository.ModelingExerciseRepository;

@Service
public class ModelAssessmentKnowledgeService {

    private final ModelAssessmentKnowledgeRepository modelAssessmentKnowledgeRepository;

    private final ModelingExerciseRepository modelingExerciseRepository;

    public ModelAssessmentKnowledgeService(ModelAssessmentKnowledgeRepository modelAssessmentKnowledgeRepository, ModelingExerciseRepository modelingExerciseRepository) {
        this.modelAssessmentKnowledgeRepository = modelAssessmentKnowledgeRepository;
        this.modelingExerciseRepository = modelingExerciseRepository;
    }

    /**
     * delete only when no other exercise uses the knowledge
     *
     * @param knowledgeId the id of the knowledge which should be deleted
     * @param modelingExerciseId used to check that the knowledge is only connected to this exercise
     */
    public void deleteKnowledge(Long knowledgeId, Long modelingExerciseId) {
        Set<ModelingExercise> exerciseSet = modelingExerciseRepository.findAllByKnowledgeId(knowledgeId);
        // If no other exercises use the same knowledge then remove knowledge
        if (exerciseSet.size() == 1 && modelingExerciseId.equals(exerciseSet.iterator().next().getId())) {
            modelAssessmentKnowledgeRepository.deleteById(knowledgeId);
        }
    }

    /**
     * Create new knowledge if exercise is created from scratch
     *
     * @return ModelAssessmentKnowledge
     */
    public ModelAssessmentKnowledge createNewKnowledge() {
        ModelAssessmentKnowledge knowledge = new ModelAssessmentKnowledge();
        modelAssessmentKnowledgeRepository.save(knowledge);
        return knowledge;
    }

}
