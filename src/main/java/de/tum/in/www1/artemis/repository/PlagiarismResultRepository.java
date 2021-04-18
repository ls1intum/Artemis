package de.tum.in.www1.artemis.repository;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismResult;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Spring Data JPA repository for the PlagiarismResult entity.
 */
@Repository
public interface PlagiarismResultRepository extends JpaRepository<PlagiarismResult<?>, Long> {

    @EntityGraph(type = LOAD, attributePaths = { "comparisons" })
    Optional<PlagiarismResult<?>> findFirstByExerciseIdOrderByLastModifiedDateDesc(long exerciseId);

    default PlagiarismResult<?> findFirstByExerciseIdOrderByLastModifiedDateDescElseThrow(long exerciseId) {
        return findFirstByExerciseIdOrderByLastModifiedDateDesc(exerciseId).orElseThrow(() -> new EntityNotFoundException("PlagiarismResult", exerciseId));
    }
}
