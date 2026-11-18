package de.tum.cit.aet.artemis.exam.repository;

import java.util.List;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.exam.config.ExamEnabled;
import de.tum.cit.aet.artemis.exam.domain.event.ExamLiveEvent;
import de.tum.cit.aet.artemis.exam.domain.event.ExamWideAnnouncementEvent;

/**
 * Spring Data JPA repository for the ExamLiveEvent entity.
 */
@Conditional(ExamEnabled.class)
@Lazy
@Repository
public interface ExamLiveEventRepository extends ArtemisJpaRepository<ExamLiveEvent, Long> {

    @Query("""
            SELECT COUNT(event)
            FROM ExamLiveEvent event
            WHERE event.examId = :examId
            """)
    long countByExamId(@Param("examId") long examId);

    /**
     * Find all events for the given student exam plus all global events for the exam in reverse creation order.
     *
     * @param examId        the id of the exam
     * @param studentExamId the id of the student exam
     * @return a list of events
     */
    @Query("""
            SELECT DISTINCT event
            FROM ExamLiveEvent event
            WHERE event.studentExamId = :studentExamId
                OR (event.studentExamId IS NULL AND event.examId = :examId)
            ORDER BY event.id DESC
            """)
    List<ExamLiveEvent> findAllByStudentExamIdOrGlobalByExamId(@Param("examId") Long examId, @Param("studentExamId") Long studentExamId);

    /**
     * Returns only exam-wide announcements, newest first, including announcements from past exams.
     *
     * @param examId the exam
     * @return the announcements
     */
    @Query("""
            SELECT event FROM ExamWideAnnouncementEvent event
            WHERE event.examId = :examId AND event.studentExamId IS NULL
            ORDER BY event.createdDate DESC, event.id DESC
            """)
    List<ExamWideAnnouncementEvent> findAnnouncementsByExamId(@Param("examId") long examId);

    /**
     * Delete all events for the given exam.
     *
     * @param examId the id of the exam
     */
    @Modifying
    @Transactional // ok because of delete
    void deleteAllByExamId(long examId);
}
