package de.tum.in.www1.artemis.repository;

import de.tum.in.www1.artemis.domain.exam.ExamSession;
import de.tum.in.www1.artemis.domain.exam.StudentExam;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the ExamSession entity.
 */
@Repository
public interface ExamSessionRepository extends JpaRepository<StudentExam, Long> {

    /**
     * Get the current session token for a specific user in the given exam.
     *
     * @param userId the id of the user
     * @param examId the id of the exam
     * @return the current session token
     */
    @Query("SELECT examSession FROM ExamSession examSession WHERE examSession.studentExam.exam.id = :#{#examId} AND examSession.studentExam.user.id = :#{#userId}")
    ExamSession getCurrentExamSessionByUserIdAndExamId(@Param("examId") Long examId, @Param("userId") Long userId);
}
