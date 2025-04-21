package de.tum.cit.aet.artemis.plagiarism.repository;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.Optional;
import java.util.Set;

import org.springframework.context.annotation.Conditional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.plagiarism.config.PlagiarismEnabled;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismComparison;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismStatus;

/**
 * Spring Data JPA repository for the PlagiarismComparison entity.
 */
@Conditional(PlagiarismEnabled.class)
@Repository
public interface PlagiarismComparisonRepository extends ArtemisJpaRepository<PlagiarismComparison<?>, Long> {

    @Query("""
            SELECT DISTINCT comparison
            FROM PlagiarismComparison comparison
                LEFT JOIN FETCH comparison.submissionA submissionA
                LEFT JOIN FETCH comparison.submissionB submissionB
                LEFT JOIN FETCH comparison.plagiarismResult result
                LEFT JOIN FETCH result.exercise exercise
                LEFT JOIN FETCH exercise.course
            WHERE comparison.id = :comparisonId
            """)
    Optional<PlagiarismComparison<?>> findByIdWithSubmissions(@Param("comparisonId") long comparisonId);

    default PlagiarismComparison<?> findByIdWithSubmissionsStudentsElseThrow(long comparisonId) {
        return getValueElseThrow(findByIdWithSubmissions(comparisonId), comparisonId);
    }

    @Query("""
            SELECT DISTINCT comparison
            FROM PlagiarismComparison comparison
                LEFT JOIN FETCH comparison.submissionA submissionA
                LEFT JOIN FETCH submissionA.elements elementsA
                LEFT JOIN FETCH comparison.plagiarismResult result
                LEFT JOIN FETCH result.exercise exercise
                LEFT JOIN FETCH exercise.course
            WHERE comparison.id = :comparisonId
            """)
    Optional<PlagiarismComparison<?>> findByIdWithSubmissionsAndElementsA(@Param("comparisonId") long comparisonId);

    default PlagiarismComparison<?> findByIdWithSubmissionsStudentsAndElementsAElseThrow(long comparisonId) {
        return getValueElseThrow(findByIdWithSubmissionsAndElementsA(comparisonId), comparisonId);
    }

    @Query("""
            SELECT DISTINCT comparison
            FROM PlagiarismComparison comparison
                LEFT JOIN FETCH comparison.submissionB submissionB
                LEFT JOIN FETCH submissionB.elements elementsB
            WHERE comparison.id = :comparisonId
            """)
    Optional<PlagiarismComparison<?>> findByIdWithSubmissionsAndElementsB(@Param("comparisonId") long comparisonId);

    default PlagiarismComparison<?> findByIdWithSubmissionsStudentsAndElementsBElseThrow(long comparisonId) {
        return getValueElseThrow(findByIdWithSubmissionsAndElementsB(comparisonId), comparisonId);
    }

    @EntityGraph(type = LOAD, attributePaths = { "submissionA", "submissionA.plagiarismCase", "submissionB", "submissionB.plagiarismCase" })
    Optional<Set<PlagiarismComparison<?>>> findBySubmissionA_SubmissionIdOrSubmissionB_SubmissionId(long submissionA_submissionId, long submissionB_submissionId);

    @Modifying
    @Transactional // ok because of delete
    void deletePlagiarismComparisonsByPlagiarismResultIdAndStatus(Long plagiarismResultId, PlagiarismStatus plagiarismStatus);

    // we can't simply call save() on plagiarismComparisons because the plagiarismComparisonMatches have no id
    // and would be recreated. Therefore, we need some update methods:

    @Modifying
    @Transactional // ok because of modifying query
    @Query("""
            UPDATE PlagiarismComparison plagiarismComparison
            SET plagiarismComparison.status = :status
            WHERE plagiarismComparison.id = :plagiarismComparisonId
            """)
    void updatePlagiarismComparisonStatus(@Param("plagiarismComparisonId") Long plagiarismComparisonId, @Param("status") PlagiarismStatus status);

    Set<PlagiarismComparison<?>> findAllByPlagiarismResultExerciseId(long exerciseId);

}
