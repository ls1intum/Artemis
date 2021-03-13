package de.tum.in.www1.artemis.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismComparison;

/**
 * Spring Data JPA repository for the PlagiarismComparison entity.
 */
@Repository
public interface PlagiarismComparisonRepository extends JpaRepository<PlagiarismComparison, Long> {

}
