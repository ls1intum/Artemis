package de.tum.in.www1.artemis.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.StudentQuestion;

/**
 * Spring Data repository for the StudentQuestion entity.
 */
@SuppressWarnings("unused")
@Repository
public interface StudentQuestionRepository extends JpaRepository<StudentQuestion, Long> {

    @Query("SELECT student_question FROM StudentQuestion student_question WHERE student_question.author.login = :#{#login}")
    List<StudentQuestion> findAllByAuthorWithLogin(@Param("login") String login);

    @Query("SELECT student_question FROM StudentQuestion student_question WHERE student_question.exercise.id = :#{#exerciseId}")
    List<StudentQuestion> findAllByExerciseId(@Param("exerciseId") Long exerciseId);

    @Query("SELECT student_question FROM StudentQuestion student_question WHERE student_question.lecture.id = :#{#lectureId}")
    List<StudentQuestion> findAllByLectureId(@Param("lectureId") Long lectureId);

}
