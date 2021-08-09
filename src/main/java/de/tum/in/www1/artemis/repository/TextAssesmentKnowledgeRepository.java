package de.tum.in.www1.artemis.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.TextAssessmentKnowledge;

/**
 * Spring Data JPA repository for the TextAssessmentKnowledge entity.
 */
@Repository
public interface TextAssesmentKnowledgeRepository extends JpaRepository<TextAssessmentKnowledge, Long> {
}
