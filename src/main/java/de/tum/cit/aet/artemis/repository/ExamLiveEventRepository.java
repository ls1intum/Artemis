package de.tum.cit.aet.artemis.repository;

import static de.tum.cit.aet.artemis.config.Constants.PROFILE_CORE;

import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.domain.exam.event.ExamLiveEvent;
import de.tum.cit.aet.artemis.repository.base.ArtemisJpaRepository;

/**
 * Spring Data JPA repository for the ExamLiveEvent entity.
 */
@Profile(PROFILE_CORE)
@Repository
public interface ExamLiveEventRepository extends ArtemisJpaRepository<ExamLiveEvent, Long> {

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
