package de.tum.cit.aet.artemis.plagiarism.repository;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.Optional;
import java.util.Set;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
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
@Lazy
@Repository
public interface PlagiarismComparisonRepository extends ArtemisJpaRepository<PlagiarismComparison, Long> {

    @Query("""
            SELECT DISTINCT comparison
            FROM PlagiarismComparison comparison
                LEFT JOIN FETCH comparison.submissions
                LEFT JOIN FETCH comparison.plagiarismResult result
                LEFT JOIN FETCH result.exercise exercise
            WHERE comparison.id = :comparisonId
            """)
    Optional<PlagiarismComparison> findByIdWithSubmissions(@Param("comparisonId") long comparisonId);

    default PlagiarismComparison findByIdWithSubmissionsStudentsElseThrow(long comparisonId) {
        return getValueElseThrow(findByIdWithSubmissions(comparisonId), comparisonId);
    }

    @Query("""
            SELECT COALESCE(course.id, examCourse.id)
            FROM PlagiarismComparison comparison
                JOIN comparison.plagiarismResult result
                JOIN result.exercise exercise
                LEFT JOIN exercise.course course
                LEFT JOIN exercise.exerciseGroup exerciseGroup
                LEFT JOIN exerciseGroup.exam exam
                LEFT JOIN exam.course examCourse
            WHERE comparison.id = :comparisonId
            """)
    Optional<Long> findCourseIdById(@Param("comparisonId") long comparisonId);

    default Long findCourseIdByIdElseThrow(long comparisonId) {
        return getArbitraryValueElseThrow(findCourseIdById(comparisonId), String.valueOf(comparisonId));
    }

    @EntityGraph(type = LOAD, attributePaths = { "submissions", "submissions.plagiarismCase" })
    Optional<Set<PlagiarismComparison>> findBySubmissions_SubmissionId(long submissions_submissionId);

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

    @EntityGraph(type = LOAD, attributePaths = "submissions")
    Set<PlagiarismComparison> findAllByPlagiarismResultExerciseId(long exerciseId);

}
