package de.tum.in.www1.artemis.service;

import java.util.Set;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.TextAssessmentKnowledge;
import de.tum.in.www1.artemis.domain.TextExercise;
import de.tum.in.www1.artemis.repository.TextAssesmentKnowledgeRepository;
import de.tum.in.www1.artemis.repository.TextExerciseRepository;

@Service
public class TextAssessmentKnowledgeService {

    private final TextAssesmentKnowledgeRepository textAssesmentKnowledgeRepository;

    private final TextExerciseRepository textExerciseRepository;

    public TextAssessmentKnowledgeService(TextAssesmentKnowledgeRepository textAssesmentKnowledgeRepository, TextExerciseRepository textExerciseRepository) {
        this.textAssesmentKnowledgeRepository = textAssesmentKnowledgeRepository;
        this.textExerciseRepository = textExerciseRepository;
    }

    /**
     * delete only when no exercises use the knowledge
     * @param knowledgeId
     */
    public void deleteKnowledge(Long knowledgeId) {
        Set<TextExercise> exerciseSet = textExerciseRepository.findAllByKnowledgeId(knowledgeId);
        // If no other exercises use the same knowledge then remove knowledge
        if (exerciseSet.isEmpty()) {
            textAssesmentKnowledgeRepository.deleteById(knowledgeId);
        }
    }

    /**
     * Create new knowledge if exercise is created from scratch
     *
     * @return TextAssessmentKnowledge
     */
    public TextAssessmentKnowledge createNewKnowledge() {
        TextAssessmentKnowledge knowledge = new TextAssessmentKnowledge();
        textAssesmentKnowledgeRepository.save(knowledge);
        return knowledge;
    }

}
