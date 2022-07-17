package de.tum.in.www1.artemis.repository.plagiarism;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.Optional;

import javax.annotation.Nullable;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

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
     * @return the saved result
     */
    default PlagiarismResult<?> savePlagiarismResultAndRemovePrevious(PlagiarismResult<?> result) {
        Optional<PlagiarismResult<?>> optionalPreviousResult = findFirstByExerciseIdOrderByLastModifiedDateDesc(result.getExercise().getId());
        result = save(result);
        optionalPreviousResult.ifPresent(this::delete);
        return result;
    }

    /**
     * prepare the result for sending it as JSON to the client by removing unnecessary values
     * @param plagiarismResult the result for which a couple of related objects should be set to null
     */
    default void prepareResultForClient(PlagiarismResult<?> plagiarismResult) {
        if (plagiarismResult != null) {
            for (var comparison : plagiarismResult.getComparisons()) {
                comparison.setPlagiarismResult(null);
                // avoid circular dependency during serialization
                comparison.getSubmissionA().setPlagiarismComparison(null);
                comparison.getSubmissionB().setPlagiarismComparison(null);
            }
        }
    }

    /**
     * Deletes all plagiarism results associated to the given exercise id
     * @param exerciseId ID of exercise with plagiarism results that will be deleted.
     */
    @Modifying
    @Transactional // ok because of modifying query
    void deletePlagiarismResultsByExerciseId(Long exerciseId);

    /**
     * Deletes all plagiarism results associated to the given exercise id except the one with the given plagiarism result id
     * @param plagiarismResultId ID of the plagiarism result that won't be deleted.
     * @param exerciseId ID of exercise with plagiarism results that will be deleted.
     */
    @Modifying
    @Transactional // ok because of modifying query
    void deletePlagiarismResultsByIdNotAndExerciseId(Long plagiarismResultId, Long exerciseId);
}
