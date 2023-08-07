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
                SELECT count(es.id)
                FROM ExamSession es
                WHERE es.studentExam.id = :#{#studentExamId}
            """)
    long findExamSessionCountByStudentExamId(@Param("studentExamId") Long studentExamId);

    @Query("""
                SELECT es
                FROM ExamSession es
                WHERE es.studentExam.exam.id = :examId
            """)
    Set<ExamSession> findAllExamSessionsByExamId(long examId);

    /**
     * Find all exam sessions for the given exam id that are suspicious. A session is suspicious if it has the same IP address
     * or same the browser fingerprint or the same user agent as the given one
     *
     * @param examId      the id of the exam
     * @param examSession the exam session to compare with
     * @return the set of suspicious exam sessions in relation to the given exam session
     */
    @Query("""
                SELECT es
                FROM ExamSession es
                WHERE es.studentExam.exam.id = :examId
                    AND es.id <> :#{#examSession.id}
                    AND es.studentExam.id <> :#{#examSession.studentExam.id}
                    AND (es.ipAddress = :#{#examSession.ipAddress}
                        OR es.browserFingerprintHash = :#{#examSession.browserFingerprintHash}
                        OR es.userAgent = :#{#examSession.userAgent})
            """)
    Set<ExamSession> findAllSuspiciousExamSessionsByExamIdAndExamSession(long examId, @Param("examSession") ExamSession examSession);
}
