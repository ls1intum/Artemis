package de.tum.in.www1.artemis.repository;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.exam.Exam;

/**
 * Spring Data JPA repository for the ExamRepository entity.
 */
@Repository
public interface ExamRepository extends JpaRepository<Exam, Long> {

    List<Exam> findByCourseId(Long courseId);

    @EntityGraph(type = LOAD, attributePaths = { "exerciseGroups" })
    Optional<Exam> findWithExerciseGroupsById(Long id);

    @EntityGraph(type = LOAD, attributePaths = { "registeredUsers" })
    Optional<Exam> findWithRegisteredUsersById(Long id);

    @EntityGraph(type = LOAD, attributePaths = { "studentExams" })
    Optional<Exam> findWithStudentExamsById(Long id);

    @Query("select distinct exam from Exam exam left join fetch exam.studentExams studentExams left join fetch exam.exerciseGroups exerciseGroups left join fetch exerciseGroups.exercises where (exam.id = :#{#examId})")
    Exam findOneWithEagerExercisesGroupsAndStudentExams(@Param("examId") long examId);

    @EntityGraph(type = LOAD, attributePaths = { "exerciseGroups", "exerciseGroups.exercises", "registeredUsers", "studentExams" })
    Optional<Exam> findWithExercisesRegisteredUsersStudentExamsById(Long id);

    /**
     * Checks if the user is registered for the exam.
     *
     * @param examId the id of the exam
     * @param userId the id of the user
     * @return true if the user is registered for the exam
     */
    @Query("SELECT CASE WHEN COUNT(exam) > 0 THEN true ELSE false END FROM Exam exam LEFT JOIN exam.registeredUsers registeredUsers WHERE exam.id = :#{#examId} AND registeredUsers.id = :#{#userId}")
    boolean isUserRegisteredForExam(@Param("examId") long examId, @Param("userId") long userId);
}
