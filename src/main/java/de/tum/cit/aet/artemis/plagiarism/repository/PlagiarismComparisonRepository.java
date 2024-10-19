package de.tum.cit.aet.artemis.plagiarism.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismComparison;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismStatus;

/**
 * Spring Data JPA repository for the PlagiarismComparison entity.
 */
@Profile(PROFILE_CORE)
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

    @Modifying
    @Transactional // ok because of delete
    void deleteByIdIn(List<Long> ids);

    /**
     * Retrieves a list of unnecessary plagiarism comparison IDs based on the associated course's date range.
     * A plagiarism comparison is considered unnecessary if its status is 'NONE' and the related course's
     * start and end dates fall within the provided range. Also deletes orphan objects.
     *
     * @param deleteFrom The start of the date range for filtering unnecessary plagiarism comparisons.
     * @param deleteTo   The end of the date range for filtering unnecessary plagiarism comparisons.
     * @return A list of Long values representing the IDs of unnecessary plagiarism comparisons.
     */
    @Query("""
            SELECT pc.id
            FROM PlagiarismComparison pc
            JOIN pc.plagiarismResult pr
            JOIN pr.exercise ex
            JOIN ex.course c
            WHERE pc.status = de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismStatus.NONE
                AND c.endDate < :deleteTo
                AND c.startDate > :deleteFrom
            """)
    List<Long> findPlagiarismComparisonIdWithStatusNoneThatBelongToCourseWithDates(@Param("deleteFrom") ZonedDateTime deleteFrom, @Param("deleteTo") ZonedDateTime deleteTo);
}
