package de.tum.in.www1.artemis.repository;

import java.util.*;

import javax.transaction.Transactional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.exam.event.ExamLiveEvent;

/**
 * Spring Data JPA repository for the ExamLiveEvent entity.
 */
@Repository
public interface ExamLiveEventRepository extends JpaRepository<ExamLiveEvent, Long> {

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
            WHERE event.studentExamId = :studentExamId OR (event.studentExamId IS NULL AND event.examId = :examId)
            ORDER BY event.id DESC
            """)
    List<ExamLiveEvent> findAllByStudentExamIdOrGlobalByExamId(@Param("examId") Long examId, @Param("studentExamId") Long studentExamId);

    /**
     * Find all events for the given student exam in reverse creation order.
     *
     * @param studentExamId the id of the student exam
     * @return a list of events
     */
    @Query("""
            SELECT event
            FROM ExamLiveEvent event
            WHERE event.studentExamId = :studentExamId
            ORDER BY event.id DESC
            """)
    List<ExamLiveEvent> findAllByStudentExamId(@Param("studentExamId") Long studentExamId);

    /**
     * Delete all events for the given exam.
     *
     * @param examId the id of the exam
     */
    @Transactional // delete
    void deleteAllByExamId(Long examId);
}
