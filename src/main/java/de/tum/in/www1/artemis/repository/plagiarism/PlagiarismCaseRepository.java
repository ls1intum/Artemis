package de.tum.in.www1.artemis.repository.plagiarism;

import java.util.List;
import java.util.Optional;
import java.util.Set;

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

    @Query("""
            SELECT DISTINCT plagiarismCase FROM PlagiarismCase plagiarismCase
            LEFT JOIN FETCH plagiarismCase.post
            LEFT JOIN FETCH plagiarismCase.plagiarismSubmissions plagiarismSubmissions
            LEFT JOIN FETCH plagiarismSubmissions.plagiarismComparison plagiarismComparison
            WHERE plagiarismCase.exercise.course.id = :courseId
            """)
    List<PlagiarismCase> findByCourseIdWithPlagiarismSubmissionsAndComparison(@Param("courseId") Long courseId);

    @Query("""
            SELECT DISTINCT plagiarismCase FROM PlagiarismCase plagiarismCase
            LEFT JOIN FETCH plagiarismCase.post
            LEFT JOIN FETCH plagiarismCase.plagiarismSubmissions plagiarismSubmissions
            LEFT JOIN FETCH plagiarismSubmissions.plagiarismComparison plagiarismComparison
            WHERE plagiarismCase.exercise.exerciseGroup.exam.id = :examId
            """)
    List<PlagiarismCase> findByExamIdWithPlagiarismSubmissionsAndComparison(@Param("examId") Long examId);

    @Query("""
            SELECT DISTINCT plagiarismCase FROM PlagiarismCase plagiarismCase
            LEFT JOIN FETCH plagiarismCase.post p
            WHERE plagiarismCase.exercise.id = :exerciseId
            AND plagiarismCase.student.id = :userId
            AND p.id IS NOT NULL
            """)
    Optional<PlagiarismCase> findByStudentIdAndExerciseIdWithPost(@Param("userId") Long userId, @Param("exerciseId") Long exerciseId);

    @Query("""
            SELECT plagiarismCase FROM PlagiarismCase plagiarismCase
            WHERE plagiarismCase.exercise.exerciseGroup.exam.id = :examId
            """)
    List<PlagiarismCase> findByExamId(@Param("examId") Long examId);

    @Query("""
            SELECT plagiarismCase FROM PlagiarismCase plagiarismCase
            WHERE plagiarismCase.exercise.exerciseGroup.exam.id = :examId AND plagiarismCase.student.id = :studentId
            """)
    List<PlagiarismCase> findByExamIdAndStudentId(@Param("examId") Long examId, @Param("studentId") Long studentId);

    @Query("""
            SELECT DISTINCT plagiarismCase FROM PlagiarismCase plagiarismCase
            LEFT JOIN FETCH plagiarismCase.post p
            WHERE plagiarismCase.exercise.id IN :exerciseIds
            AND plagiarismCase.student.id = :userId
            AND p.id IS NOT NULL
            """)
    List<PlagiarismCase> findByStudentIdAndExerciseIdsWithPost(@Param("userId") Long userId, @Param("exerciseIds") Set<Long> exerciseIds);

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
