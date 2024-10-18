package de.tum.cit.aet.artemis.quiz.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import jakarta.validation.constraints.NotNull;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.exception.NoUniqueQueryException;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;

/**
 * Spring Data JPA repository for the QuizExercise entity.
 */
@Profile(PROFILE_CORE)
@Repository
public interface QuizExerciseRepository extends ArtemisJpaRepository<QuizExercise, Long>, JpaSpecificationExecutor<QuizExercise> {

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

    /**
     * Find all quiz exercises that are planned to start in the future
     *
     * @param now the current date
     * @return the list of quiz exercises
     */
    @Query("""
            SELECT DISTINCT qe
            FROM QuizExercise qe
                LEFT JOIN FETCH qe.quizBatches b
            WHERE qe.releaseDate > :now
                OR b.startTime > :now
                OR qe.dueDate > :now
            """)
    List<QuizExercise> findAllToBeScheduled(@Param("now") ZonedDateTime now);

    @EntityGraph(type = LOAD, attributePaths = { "quizQuestions", "quizPointStatistic", "quizQuestions.quizQuestionStatistic", "categories", "quizBatches" })
    Optional<QuizExercise> findWithEagerQuestionsAndStatisticsById(Long quizExerciseId);

    @EntityGraph(type = LOAD, attributePaths = { "quizQuestions", "quizPointStatistic", "quizQuestions.quizQuestionStatistic", "categories", "competencyLinks.competency",
            "quizBatches", "gradingCriteria" })
    Optional<QuizExercise> findWithEagerQuestionsAndStatisticsAndCompetenciesAndBatchesAndGradingCriteriaById(Long quizExerciseId);

    @EntityGraph(type = LOAD, attributePaths = { "quizQuestions" })
    Optional<QuizExercise> findWithEagerQuestionsById(Long quizExerciseId);

    @EntityGraph(type = LOAD, attributePaths = { "quizQuestions", "competencyLinks.competency" })
    Optional<QuizExercise> findWithEagerQuestionsAndCompetenciesById(Long quizExerciseId);

    @EntityGraph(type = LOAD, attributePaths = { "quizBatches" })
    Optional<QuizExercise> findWithEagerBatchesById(Long quizExerciseId);

    @Query("""
            SELECT q
            FROM QuizExercise q
                LEFT JOIN FETCH q.competencyLinks
            WHERE q.title = :title
                AND q.course.id = :courseId
            """)
    Set<QuizExercise> findAllWithCompetenciesByTitleAndCourseId(@Param("title") String title, @Param("courseId") long courseId);

    /**
     * Finds a quiz exercise by its title and course id and throws a NoUniqueQueryException if multiple exercises are found.
     *
     * @param title    the title of the exercise
     * @param courseId the id of the course
     * @return the exercise with the given title and course id
     * @throws NoUniqueQueryException if multiple exercises are found with the same title
     */
    default Optional<QuizExercise> findUniqueWithCompetenciesByTitleAndCourseId(String title, long courseId) throws NoUniqueQueryException {
        Set<QuizExercise> allExercises = findAllWithCompetenciesByTitleAndCourseId(title, courseId);
        if (allExercises.size() > 1) {
            throw new NoUniqueQueryException("Found multiple exercises with title " + title + " in course with id " + courseId);
        }
        return allExercises.stream().findFirst();
    }

    @NotNull
    default QuizExercise findWithEagerBatchesByIdOrElseThrow(Long quizExerciseId) {
        return getValueElseThrow(findWithEagerBatchesById(quizExerciseId), quizExerciseId);
    }

    /**
     * Get one quiz exercise by id and eagerly load questions
     *
     * @param quizExerciseId the id of the entity
     * @return the entity
     */
    @NotNull
    default QuizExercise findByIdWithQuestionsElseThrow(Long quizExerciseId) {
        return getValueElseThrow(findWithEagerQuestionsById(quizExerciseId), quizExerciseId);
    }

    /**
     * Get one quiz exercise by id and eagerly load questions
     *
     * @param quizExerciseId the id of the entity
     * @return the entity
     */
    @NotNull
    default QuizExercise findByIdWithQuestionsAndCompetenciesElseThrow(Long quizExerciseId) {
        return getValueElseThrow(findWithEagerQuestionsAndCompetenciesById(quizExerciseId), quizExerciseId);
    }

    /**
     * Get one quiz exercise by id and eagerly load batches
     *
     * @param quizExerciseId the id of the entity
     * @return the entity
     */
    @NotNull
    default QuizExercise findByIdWithBatchesElseThrow(Long quizExerciseId) {
        return getValueElseThrow(findWithEagerBatchesById(quizExerciseId), quizExerciseId);
    }

    @NotNull
    default QuizExercise findByIdWithQuestionsAndStatisticsElseThrow(Long quizExerciseId) {
        return getValueElseThrow(findWithEagerQuestionsAndStatisticsById(quizExerciseId), quizExerciseId);
    }

    @NotNull
    default QuizExercise findByIdWithQuestionsAndStatisticsAndCompetenciesAndBatchesAndGradingCriteriaElseThrow(Long quizExerciseId) {
        return getValueElseThrow(findWithEagerQuestionsAndStatisticsAndCompetenciesAndBatchesAndGradingCriteriaById(quizExerciseId), quizExerciseId);
    }
}
