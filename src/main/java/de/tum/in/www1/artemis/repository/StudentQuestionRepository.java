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

    @Query("select student_question from StudentQuestion student_question where student_question.author.login = ?#{principal.username}")
    List<StudentQuestion> findByAuthorIsCurrentUser();

    @Query("select student_question from StudentQuestion student_question where student_question.exercise.id = :#{#exerciseId}")
    List<StudentQuestion> findStudentQuestionsForExercise(@Param("exerciseId") Long exerciseId);

}
