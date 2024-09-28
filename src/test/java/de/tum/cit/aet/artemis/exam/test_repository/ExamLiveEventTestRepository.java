package de.tum.cit.aet.artemis.exam.test_repository;

import java.util.List;

import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.exam.domain.event.ExamLiveEvent;
import de.tum.cit.aet.artemis.exam.repository.ExamLiveEventRepository;

@Repository
@Primary
public interface ExamLiveEventTestRepository extends ExamLiveEventRepository {

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
}
