package de.tum.in.www1.artemis.repository;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;
import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.quiz.QuizExercise;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Spring Data JPA repository for the QuizExercise entity.
 */
@Profile(PROFILE_CORE)
@Repository
public interface QuizExerciseRepository extends JpaRepository<QuizExercise, Long>, JpaSpecificationExecutor<QuizExercise> {

    @Query("""
            SELECT DISTINCT e
            FROM QuizExercise e
                LEFT JOIN FETCH e.categories
            WHERE e.course.id = :courseId
            """)
    List<QuizExercise> findByCourseIdWithCategories(@Param("courseId") Long courseId);

    @Query("""
            SELECT qe
            FROM QuizExercise qe
            WHERE qe.exerciseGroup.exam.id = :examId
            """)
    List<QuizExercise> findByExamId(@Param("examId") Long examId);

    @Query("""
            SELECT DISTINCT qe
            FROM QuizExercise qe
                LEFT JOIN qe.quizBatches b
            WHERE b.startTime > :earliestReleaseDate
            """)
    List<QuizExercise> findAllPlannedToStartAfter(@Param("earliestReleaseDate") ZonedDateTime earliestReleaseDate);

    @EntityGraph(type = LOAD, attributePaths = { "quizQuestions", "quizPointStatistic", "quizQuestions.quizQuestionStatistic", "categories", "quizBatches" })
    Optional<QuizExercise> findWithEagerQuestionsAndStatisticsById(Long quizExerciseId);

    @EntityGraph(type = LOAD, attributePaths = { "quizQuestions", "quizPointStatistic", "quizQuestions.quizQuestionStatistic", "categories", "competencies", "quizBatches" })
    Optional<QuizExercise> findWithEagerQuestionsAndStatisticsAndCompetenciesById(Long quizExerciseId);

    @EntityGraph(type = LOAD, attributePaths = { "quizQuestions" })
    Optional<QuizExercise> findWithEagerQuestionsById(Long quizExerciseId);

    @EntityGraph(type = LOAD, attributePaths = { "quizBatches" })
    Optional<QuizExercise> findWithEagerBatchesById(Long quizExerciseId);

    @NotNull
    default QuizExercise findByIdElseThrow(Long quizExerciseId) throws EntityNotFoundException {
        return findById(quizExerciseId).orElseThrow(() -> new EntityNotFoundException("Quiz Exercise", quizExerciseId));
    }

    @NotNull
    default QuizExercise findWithEagerQuestionsByIdOrElseThrow(Long quizExerciseId) {
        return findWithEagerQuestionsById(quizExerciseId).orElseThrow(() -> new EntityNotFoundException("QuizExercise", quizExerciseId));
    }

    @NotNull
    default QuizExercise findWithEagerBatchesByIdOrElseThrow(Long quizExerciseId) {
        return findWithEagerBatchesById(quizExerciseId).orElseThrow(() -> new EntityNotFoundException("QuizExercise", quizExerciseId));
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
    @NotNull
    default QuizExercise findByIdWithQuestionsElseThrow(Long quizExerciseId) {
        return findWithEagerQuestionsById(quizExerciseId).orElseThrow(() -> new EntityNotFoundException("Quiz Exercise", quizExerciseId));
    }

    /**
     * Get one quiz exercise by id and eagerly load batches
     *
     * @param quizExerciseId the id of the entity
     * @return the entity
     */
    @NotNull
    default QuizExercise findByIdWithBatchesElseThrow(Long quizExerciseId) {
        return findWithEagerBatchesById(quizExerciseId).orElseThrow(() -> new EntityNotFoundException("Quiz Exercise", quizExerciseId));
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
    default QuizExercise findByIdWithQuestionsAndStatisticsElseThrow(Long quizExerciseId) {
        return findWithEagerQuestionsAndStatisticsById(quizExerciseId).orElseThrow(() -> new EntityNotFoundException("Quiz Exercise", quizExerciseId));
    }

    @NotNull
    default QuizExercise findByIdWithQuestionsAndStatisticsAndCompetenciesElseThrow(Long quizExerciseId) {
        return findWithEagerQuestionsAndStatisticsAndCompetenciesById(quizExerciseId).orElseThrow(() -> new EntityNotFoundException("Quiz Exercise", quizExerciseId));
    }

    default List<QuizExercise> findAllPlannedToStartInTheFuture() {
        return findAllPlannedToStartAfter(ZonedDateTime.now());
    }

}
