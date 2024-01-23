package de.tum.in.www1.artemis.repository;

import java.util.Set;

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
            SELECT COUNT(es.id)
            FROM ExamSession es
            WHERE es.studentExam.id = :studentExamId
            """)
    long findExamSessionCountByStudentExamId(@Param("studentExamId") Long studentExamId);

    @Query("""
            SELECT es
            FROM ExamSession es
            WHERE es.studentExam.exam.id = :examId
                AND es.id <> :sessionId
                AND es.studentExam.id <> :studentExamId
                AND (:ipAddress IS NULL OR es.ipAddress = :ipAddress)
                AND (:browserFingerprintHash IS NULL OR es.browserFingerprintHash = :browserFingerprintHash)
            """)
    Set<ExamSession> findAllExamSessionsWithTheSameIpAddressAndBrowserFingerprintByExamIdAndExamSession(@Param("examId") Long examId, @Param("sessionId") Long sessionId,
            @Param("studentExamId") Long studentExamId, @Param("ipAddress") String ipAddress, @Param("browserFingerprintHash") String browserFingerprintHash);

    @Query("""
                SELECT es
                FROM ExamSession es
                WHERE es.studentExam.exam.id = :examId
            """)
    Set<ExamSession> findAllExamSessionsByExamId(@Param("examId") long examId);
}
