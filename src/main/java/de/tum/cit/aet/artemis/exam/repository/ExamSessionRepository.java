package de.tum.cit.aet.artemis.exam.repository;

import java.util.Set;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.exam.config.ExamEnabled;
import de.tum.cit.aet.artemis.exam.domain.ExamSession;

/**
 * Spring Data JPA repository for the ExamSession entity.
 */
@Conditional(ExamEnabled.class)
@Lazy
@Repository
public interface ExamSessionRepository extends ArtemisJpaRepository<ExamSession, Long> {

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

    /**
     * Deletes all exam sessions for a given exam.
     * This must be called before deleting student exams to avoid foreign key constraint violations.
     *
     * @param examId the ID of the exam whose sessions should be deleted
     */
    @Modifying
    @Transactional // ok because of delete
    @Query("DELETE FROM ExamSession es WHERE es.studentExam.exam.id = :examId")
    void deleteAllByExamId(@Param("examId") long examId);
}
