package de.tum.in.www1.artemis.repository.plagiarism;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.Optional;

import javax.annotation.Nullable;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismResult;

/**
 * Spring Data JPA repository for the PlagiarismResult entity.
 */
@Repository
public interface PlagiarismResultRepository extends JpaRepository<PlagiarismResult<?>, Long> {

    @EntityGraph(type = LOAD, attributePaths = { "comparisons" })
    Optional<PlagiarismResult<?>> findFirstByExerciseIdOrderByLastModifiedDateDesc(long exerciseId);

    @Nullable
    default PlagiarismResult<?> findFirstByExerciseIdOrderByLastModifiedDateDescOrNull(long exerciseId) {
        return findFirstByExerciseIdOrderByLastModifiedDateDesc(exerciseId).orElse(null);
    }

    /**
     * Store the given TextPlagiarismResult in the database.
     *
     * @param result TextPlagiarismResult to store in the database.
     */
    default void savePlagiarismResultAndRemovePrevious(PlagiarismResult<?> result) {
        Optional<PlagiarismResult<?>> optionalPreviousResult = findFirstByExerciseIdOrderByLastModifiedDateDesc(result.getExercise().getId());
        save(result);
        optionalPreviousResult.ifPresent(this::delete);
    }

    /**
     * Deletes all plagiarism results associated to the given exercise id
     * @param exerciseId Id of exercise with plagiarism results that will be deleted.
     */
    void deletePlagiarismResultsByExerciseId(Long exerciseId);
}
