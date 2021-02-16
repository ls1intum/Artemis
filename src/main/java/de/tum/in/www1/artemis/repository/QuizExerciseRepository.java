package de.tum.in.www1.artemis.repository;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.quiz.QuizExercise;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Spring Data JPA repository for the QuizExercise entity.
 */
@SuppressWarnings("unused")
@Repository
public interface QuizExerciseRepository extends JpaRepository<QuizExercise, Long> {

    List<QuizExercise> findByCourseId(Long courseId);

    List<QuizExercise> findByIsPlannedToStartAndReleaseDateIsAfter(Boolean plannedToStart, ZonedDateTime earliestReleaseDate);

    @EntityGraph(type = LOAD, attributePaths = { "quizQuestions", "quizPointStatistic", "quizQuestions.quizQuestionStatistic", "categories" })
    Optional<QuizExercise> findWithEagerQuestionsAndStatisticsById(Long quizExerciseId);

    @EntityGraph(type = LOAD, attributePaths = { "quizQuestions" })
    Optional<QuizExercise> findWithEagerQuestionsById(Long quizExerciseId);

    @NotNull
    default QuizExercise findByIdElseThrow(Long quizExerciseId) throws EntityNotFoundException {
        return findById(quizExerciseId).orElseThrow(() -> new EntityNotFoundException("Quiz Exercise", quizExerciseId));
    }

    /**
     * Get one quiz exercise
     *
     * @param quizExerciseId the id of the entity
     * @return the entity
     */
    @Nullable
    default QuizExercise findOne(Long quizExerciseId) {
        return findById(quizExerciseId).orElse(null);
    }

    /**
     * Get one quiz exercise by id and eagerly load questions
     *
     * @param quizExerciseId the id of the entity
     * @return the entity
     */
    @Nullable
    default QuizExercise findOneWithQuestions(Long quizExerciseId) {
        return findWithEagerQuestionsById(quizExerciseId).orElse(null);
    }

    @NotNull
    default QuizExercise findByIdWithQuestionsOrElseThrow(Long quizExerciseId) {
        return findWithEagerQuestionsById(quizExerciseId).orElseThrow(() -> new EntityNotFoundException("Quiz Exercise", quizExerciseId));
    }

    /**
     * Get one quiz exercise by id and eagerly load questions and statistics
     *
     * @param quizExerciseId the id of the entity
     * @return the quiz exercise entity
     */
    @Nullable
    default QuizExercise findOneWithQuestionsAndStatistics(Long quizExerciseId) {
        return findWithEagerQuestionsAndStatisticsById(quizExerciseId).orElse(null);
    }

    @NotNull
    default QuizExercise findByIdWithQuestionsAndStatisticsOrElseThrow(Long quizExerciseId) {
        return findWithEagerQuestionsAndStatisticsById(quizExerciseId).orElseThrow(() -> new EntityNotFoundException("Quiz Exercise", quizExerciseId));
    }

    /**
     * Get all quiz exercises that are planned to start in the future
     *
     * @return the list of quiz exercises
     */
    default List<QuizExercise> findAllPlannedToStartInTheFuture() {
        return findByIsPlannedToStartAndReleaseDateIsAfter(true, ZonedDateTime.now());
    }
}
