package de.tum.in.www1.artemis.repository;

import java.util.Optional;
import java.util.Set;

import javax.validation.constraints.NotNull;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.quiz.QuizBatch;
import de.tum.in.www1.artemis.domain.quiz.QuizExercise;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Spring Data JPA repository for the QuizBatch entity.
 */
@SuppressWarnings("unused")
@Repository
public interface QuizBatchRepository extends JpaRepository<QuizBatch, Long> {

    Set<QuizBatch> findAllByQuizExercise(QuizExercise quizExercise);

    Set<QuizBatch> findAllByQuizExerciseAndCreator(QuizExercise quizExercise, Long creator);

    Optional<QuizBatch> findByQuizExerciseAndPassword(QuizExercise quizExercise, String password);

    Optional<QuizBatch> findFirstByQuizExercise(QuizExercise quizExercise);

    @NotNull
    default QuizBatch findByIdElseThrow(Long quizBatchId) throws EntityNotFoundException {
        return findById(quizBatchId).orElseThrow(() -> new EntityNotFoundException("Quiz Batch", quizBatchId));
    }
}
