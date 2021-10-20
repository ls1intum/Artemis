package de.tum.in.www1.artemis.service;

import java.util.Set;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.ModelAssessmentKnowledge;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.repository.ModelAssesmentKnowledgeRepository;
import de.tum.in.www1.artemis.repository.ModelingExerciseRepository;

@Service
public class ModelAssessmentKnowledgeService {

    private final ModelAssesmentKnowledgeRepository modelAssesmentKnowledgeRepository;

    private final ModelingExerciseRepository modelingExerciseRepository;

    public ModelAssessmentKnowledgeService(ModelAssesmentKnowledgeRepository modelAssesmentKnowledgeRepository, ModelingExerciseRepository modelingExerciseRepository) {
        this.modelAssesmentKnowledgeRepository = modelAssesmentKnowledgeRepository;
        this.modelingExerciseRepository = modelingExerciseRepository;
    }

    /**
     * delete only when no exercises use the knowledge
     *
     * @param knowledgeId
     */
    public void deleteKnowledge(Long knowledgeId) {
        Set<ModelingExercise> exerciseSet = modelingExerciseRepository.findAllByKnowledgeId(knowledgeId);
        // If no other exercises use the same knowledge then remove knowledge
        if (exerciseSet.isEmpty()) {
            modelAssesmentKnowledgeRepository.deleteById(knowledgeId);
        }
    }

    /**
     * Create new knowledge if exercise is created from scratch
     *
     * @return ModelAssessmentKnowledge
     */
    public ModelAssessmentKnowledge createNewKnowledge() {
        ModelAssessmentKnowledge knowledge = new ModelAssessmentKnowledge();
        modelAssesmentKnowledgeRepository.save(knowledge);
        return knowledge;
    }

}
