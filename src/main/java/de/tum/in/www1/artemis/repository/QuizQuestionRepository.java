package de.tum.in.www1.artemis.repository;

import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.quiz.DragAndDropQuestion;
import de.tum.in.www1.artemis.domain.quiz.QuizQuestion;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Spring Data JPA repository for the QuizQuestion entity.
 */
@Repository
public interface QuizQuestionRepository extends JpaRepository<QuizQuestion, Long> {

    @Query("""
            SELECT question
            FROM QuizExercise exercise
                LEFT JOIN  exercise.quizQuestions question
            WHERE exercise.id = :exerciseId
            """)
    Set<QuizQuestion> getQuizQuestionsByExerciseId(@Param("exerciseId") long exerciseId);

    default DragAndDropQuestion findDnDQuestionByIdOrElseThrow(Long questionId) {
        return (DragAndDropQuestion) findById(questionId).orElseThrow(() -> new EntityNotFoundException("DragAndDropQuestion", questionId));
    }

}
