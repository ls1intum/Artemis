package de.tum.in.www1.artemis.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismResult;

/**
 * Spring Data JPA repository for the PlagiarismResult entity.
 */
@Repository
public interface PlagiarismResultRepository extends JpaRepository<PlagiarismResult, Long> {
}
