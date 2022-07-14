package de.tum.in.www1.artemis.repository.plagiarism;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismCase;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Spring Data JPA repository for the PlagiarismCase entity.
 */
@Repository
public interface PlagiarismCaseRepository extends JpaRepository<PlagiarismCase, Long> {

    @Query("""
                SELECT DISTINCT plagiarismCase FROM PlagiarismCase plagiarismCase
                LEFT JOIN FETCH plagiarismCase.post
                LEFT JOIN FETCH plagiarismCase.plagiarismSubmissions plagiarismSubmission
                WHERE plagiarismCase.student.login = :studentLogin
                AND plagiarismCase.exercise.id = :exerciseId
            """)
    Optional<PlagiarismCase> findByStudentLoginAndExerciseIdWithPlagiarismSubmissions(@Param("studentLogin") String studentLogin, @Param("exerciseId") Long exerciseId);

    // we have a one to one relationship between PlagiarismSubmission and PlagiarismComparison, therefore we use a normal JOIN (== INNER JOIN) and not a left (outer) JOIN
    @Query("""
            SELECT DISTINCT plagiarismCase FROM PlagiarismCase plagiarismCase
            LEFT JOIN FETCH plagiarismCase.post
            LEFT JOIN FETCH plagiarismCase.plagiarismSubmissions plagiarismSubmissions
            JOIN FETCH plagiarismSubmissions.plagiarismComparison plagiarismComparison
            WHERE plagiarismCase.exercise.course.id = :courseId
            """)
    List<PlagiarismCase> findByCourseIdWithPlagiarismSubmissionsAndComparison(@Param("courseId") Long courseId);

    @Query("""
            SELECT DISTINCT plagiarismCase FROM PlagiarismCase plagiarismCase
            WHERE plagiarismCase.exercise.id = :exerciseId
            AND plagiarismCase.student.id = :userId
            AND (plagiarismCase.post IS NOT NULL OR plagiarismCase.verdict IS NOT NULL)
            """)
    Optional<PlagiarismCase> findByStudentIdAndExerciseId(@Param("userId") Long userId, @Param("exerciseId") Long exerciseId);

    @Query("""
            SELECT DISTINCT plagiarismCase FROM PlagiarismCase plagiarismCase
            LEFT JOIN FETCH plagiarismCase.plagiarismSubmissions plagiarismSubmissions
            WHERE plagiarismCase.id = :plagiarismCaseId
            """)
    Optional<PlagiarismCase> findByIdWithPlagiarismSubmissions(@Param("plagiarismCaseId") long plagiarismCaseId);

    default PlagiarismCase findByIdWithPlagiarismSubmissionsElseThrow(long plagiarismCaseId) {
        return findByIdWithPlagiarismSubmissions(plagiarismCaseId).orElseThrow(() -> new EntityNotFoundException("PlagiarismCase", plagiarismCaseId));
    }

    default PlagiarismCase findByIdElseThrow(long plagiarismCaseId) {
        return findById(plagiarismCaseId).orElseThrow(() -> new EntityNotFoundException("PlagiarismCase", plagiarismCaseId));
    }
}
