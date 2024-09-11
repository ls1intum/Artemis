package de.tum.cit.aet.artemis.repository.plagiarism;

import static de.tum.cit.aet.artemis.config.Constants.PROFILE_CORE;
import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.Optional;

import jakarta.annotation.Nullable;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.domain.plagiarism.PlagiarismResult;
import de.tum.cit.aet.artemis.repository.base.ArtemisJpaRepository;

/**
 * Spring Data JPA repository for the PlagiarismResult entity.
 */
@Profile(PROFILE_CORE)
@Repository
public interface PlagiarismResultRepository extends ArtemisJpaRepository<PlagiarismResult<?>, Long> {

    Optional<PlagiarismResult<?>> findFirstByExerciseIdOrderByLastModifiedDateDesc(long exerciseId);

    @EntityGraph(type = LOAD, attributePaths = "comparisons")
    PlagiarismResult<?> findPlagiarismResultById(long plagiarismResultId);

    /**
     * Finds the first plagiarism result by exercise ID, including its comparisons, ordered by last modified date in descending order.
     * If no plagiarism result is found, this method returns null. This method avoids in-memory paging by retrieving the result directly from the database.
     *
     * @param exerciseId the ID of the exercise to find the plagiarism result for
     * @return the first {@code PlagiarismResult} with comparisons, ordered by last modified date in descending order,
     *         or null if no result is found
     */
    @Nullable
    default PlagiarismResult<?> findFirstWithComparisonsByExerciseIdOrderByLastModifiedDateDescOrNull(long exerciseId) {
        var plagiarismResultIdOrEmpty = findFirstByExerciseIdOrderByLastModifiedDateDesc(exerciseId);
        if (plagiarismResultIdOrEmpty.isEmpty()) {
            return null;
        }
        var id = plagiarismResultIdOrEmpty.get().getId();
        return findPlagiarismResultById(id);
    }

    /**
     * Store the given TextPlagiarismResult in the database.
     *
     * @param result TextPlagiarismResult to store in the database.
     * @return the saved result
     */
    default PlagiarismResult<?> savePlagiarismResultAndRemovePrevious(PlagiarismResult<?> result) {
        Optional<PlagiarismResult<?>> optionalPreviousResult = Optional
                .ofNullable(findFirstWithComparisonsByExerciseIdOrderByLastModifiedDateDescOrNull(result.getExercise().getId()));
        result = save(result);
        optionalPreviousResult.ifPresent(this::delete);
        return result;
    }

    /**
     * prepare the result for sending it as JSON to the client by removing unnecessary values
     *
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
     *
     * @param exerciseId ID of exercise with plagiarism results that will be deleted.
     */
    @Modifying
    @Transactional // ok because of modifying query
    void deletePlagiarismResultsByExerciseId(Long exerciseId);

    /**
     * Deletes all plagiarism results associated to the given exercise id except the one with the given plagiarism result id
     *
     * @param plagiarismResultId ID of the plagiarism result that won't be deleted.
     * @param exerciseId         ID of exercise with plagiarism results that will be deleted.
     */
    @Modifying
    @Transactional // ok because of modifying query
    void deletePlagiarismResultsByIdNotAndExerciseId(Long plagiarismResultId, Long exerciseId);
}
