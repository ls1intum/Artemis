package de.tum.in.www1.artemis.service;

import java.util.Set;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.TextAssessmentKnowledge;
import de.tum.in.www1.artemis.domain.TextExercise;
import de.tum.in.www1.artemis.repository.TextAssessmentKnowledgeRepository;
import de.tum.in.www1.artemis.repository.TextExerciseRepository;

@Service
public class TextAssessmentKnowledgeService {

    private final TextAssessmentKnowledgeRepository textAssessmentKnowledgeRepository;

    private final TextExerciseRepository textExerciseRepository;

    public TextAssessmentKnowledgeService(TextAssessmentKnowledgeRepository textAssessmentKnowledgeRepository, TextExerciseRepository textExerciseRepository) {
        this.textAssessmentKnowledgeRepository = textAssessmentKnowledgeRepository;
        this.textExerciseRepository = textExerciseRepository;
    }

    /**
     * delete only when no other exercise uses the knowledge
     *
     * @param knowledgeId the id of the knowledge which should be deleted
     */
    public void deleteKnowledgeIfUnused(Long knowledgeId) {
        Set<TextExercise> exerciseSet = textExerciseRepository.findAllByKnowledgeId(knowledgeId);
        // If no other exercises use the same knowledge then remove knowledge
        if (exerciseSet.isEmpty()) {
            textAssessmentKnowledgeRepository.deleteById(knowledgeId);
        }
    }

    /**
     * Create new knowledge if exercise is created from scratch
     *
     * @return TextAssessmentKnowledge
     */
    public TextAssessmentKnowledge createNewKnowledge() {
        TextAssessmentKnowledge knowledge = new TextAssessmentKnowledge();
        textAssessmentKnowledgeRepository.save(knowledge);
        return knowledge;
    }

}
