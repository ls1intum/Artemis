package de.tum.in.www1.artemis.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.exam.ExamSession;

/**
 * Spring Data JPA repository for the ExamSession entity.
 */
@Repository
public interface ExamSessionRepository extends JpaRepository<ExamSession, Long> {

    @Query("""
        SELECT count(es.id)
        FROM ExamSession es
        WHERE es.studentExam.id = :#{#studentExamId}
    """)
    long findExamSessionCountByStudentExamId(@Param("studentExamId") Long studentExamId);

}
