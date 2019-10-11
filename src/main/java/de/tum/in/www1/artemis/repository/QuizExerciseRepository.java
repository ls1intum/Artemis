package de.tum.in.www1.artemis.repository;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.quiz.QuizExercise;

/**
 * Spring Data JPA repository for the QuizExercise entity.
 */
@SuppressWarnings("unused")
@Repository
public interface QuizExerciseRepository extends JpaRepository<QuizExercise, Long> {

    List<QuizExercise> findByCourseId(Long courseId);

    List<QuizExercise> findByIsPlannedToStartAndReleaseDateIsAfter(Boolean plannedToStart, ZonedDateTime earliestReleaseDate);

    @EntityGraph(attributePaths = { "quizQuestions", "quizPointStatistic", "quizQuestions.quizQuestionStatistic" })
    Optional<QuizExercise> findWithEagerQuestionsAndStatisticsById(Long quizExerciseId);

    @EntityGraph(attributePaths = { "quizQuestions" })
    Optional<QuizExercise> findWithEagerQuestionsById(Long quizExerciseId);
}
