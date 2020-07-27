package de.tum.in.www1.artemis.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.TextAssessmentConflict;

/**
 * Spring Data JPA repository for the TextAssessmentConflict entity.
 */
@Repository
public interface TextAssessmentConflictRepository extends JpaRepository<TextAssessmentConflict, Long> {
}
