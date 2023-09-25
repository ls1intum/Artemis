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
                    AND es.id <> :#{#examSession.id}
                    AND es.studentExam.id <> :#{#examSession.studentExam.id}
                    AND es.ipAddress = :#{#examSession.ipAddress}
                    AND es.browserFingerprintHash = :#{#examSession.browserFingerprintHash}
            """)
    Set<ExamSession> findAllExamSessionsWithTheSameIpAddressAndBrowserFingerprintByExamIdAndExamSession(long examId, @Param("examSession") ExamSession examSession);

    @Query("""
                SELECT es
                FROM ExamSession es
                WHERE es.studentExam.exam.id = :examId
            """)
    Set<ExamSession> findAllExamSessionsByExamId(long examId);

    @Query("""
                SELECT es
                FROM ExamSession es
                WHERE es.studentExam.exam.id = :examId
                    AND es.id <> :#{#examSession.id}
                    AND es.studentExam.id <> :#{#examSession.studentExam.id}
                    AND es.browserFingerprintHash = :#{#examSession.browserFingerprintHash}
            """)
    Set<ExamSession> findAllExamSessionsWithTheSameBrowserFingerprintByExamIdAndExamSession(long examId, @Param("examSession") ExamSession examSession);

    @Query("""
                SELECT es
                FROM ExamSession es
                WHERE es.studentExam.exam.id = :examId
                    AND es.id <> :#{#examSession.id}
                    AND es.studentExam.id <> :#{#examSession.studentExam.id}
                    AND es.ipAddress = :#{#examSession.ipAddress}
            """)
    Set<ExamSession> findAllExamSessionsWithTheSameIpAddressByExamIdAndExamSession(long examId, @Param("examSession") ExamSession examSession);

}
