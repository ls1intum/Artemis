package de.tum.in.www1.artemis.repository;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismResult;

/**
 * Spring Data JPA repository for the PlagiarismResult entity.
 */
@Repository
public interface PlagiarismResultRepository extends JpaRepository<PlagiarismResult, Long> {

    @EntityGraph(type = LOAD, attributePaths = { "comparisons" })
    Optional<PlagiarismResult> findFirstByExerciseIdOrderByLastModifiedDateDesc(Long exerciseId);
}
