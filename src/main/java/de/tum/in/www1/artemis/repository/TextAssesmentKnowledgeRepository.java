package de.tum.in.www1.artemis.repository;

import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.TextAssessmentKnowledge;
import de.tum.in.www1.artemis.domain.TextExercise;

/**
 * Spring Data JPA repository for the TextAssessmentKnowledge entity.
 */
@Repository
public interface TextAssesmentKnowledgeRepository extends JpaRepository<TextAssessmentKnowledge, Long> {

    @Query("""
                SELECT e FROM TextExercise e
                JOIN e.knowledge ON e.id = e.knowledge.id
                WHERE e.knowledge.id = :#{#assessmentKnowledgeId}
            """)
    Set<TextExercise> findAllExerciseByAssessmentKnowledgeId(@Param("assessmentKnowledgeId") Long assessmentKnowledgeId);

    @Query("""
                SELECT tak FROM TextAssessmentKnowledge tak
                WHERE tak.id = :#{#assessmentKnowledgeId}
            """)
    TextAssessmentKnowledge findTextAssessmentKnowledgeById(@Param("assessmentKnowledgeId") Long assessmentKnowledgeId);
}
