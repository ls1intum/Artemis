package de.tum.in.www1.artemis.repository;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.quiz.QuizExercise;

/**
 * Spring Data JPA repository for the QuizExercise entity.
 */
@SuppressWarnings("unused")
@Repository
public interface QuizExerciseRepository extends JpaRepository<QuizExercise, Long> {

    List<QuizExercise> findByCourseId(Long courseId);

    @Query("""
            SELECT qe
            FROM QuizExercise qe
            WHERE qe.exerciseGroup.exam.id = :#{#examId}
            """)
    List<QuizExercise> findByExamId(Long examId);

    List<QuizExercise> findByIsPlannedToStartAndReleaseDateIsAfter(Boolean plannedToStart, ZonedDateTime earliestReleaseDate);

    @EntityGraph(type = LOAD, attributePaths = { "quizQuestions", "quizPointStatistic", "quizQuestions.quizQuestionStatistic", "categories" })
    Optional<QuizExercise> findWithEagerQuestionsAndStatisticsById(Long quizExerciseId);

    @EntityGraph(type = LOAD, attributePaths = { "quizQuestions" })
    Optional<QuizExercise> findWithEagerQuestionsById(Long quizExerciseId);
}
