package de.tum.in.www1.artemis.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.quiz.QuizExamSubmission;

/**
 * Spring Data JPA repository for the QuizExamSubmission entity.
 */
@Repository
public interface QuizExamSubmissionRepository extends JpaRepository<QuizExamSubmission, Long> {

    @Query("""
            SELECT qes
            FROM QuizExamSubmission qes
                LEFT JOIN FETCH qes.results
                LEFT JOIN FETCH qes.studentExam se
                LEFT JOIN se.exam e
            WHERE e.id = :#{#examId}
            """)
    List<QuizExamSubmission> findAllWithStudentExamAndResultByExamId(@Param("examId") Long examId);
}
