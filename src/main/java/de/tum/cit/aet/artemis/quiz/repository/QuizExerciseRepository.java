package de.tum.cit.aet.artemis.quiz.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.jspecify.annotations.NonNull;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.calendar.dto.QuizExerciseCalendarEventDTO;
import de.tum.cit.aet.artemis.core.exception.NoUniqueQueryException;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.quiz.domain.DragAndDropQuestion;
import de.tum.cit.aet.artemis.quiz.domain.MultipleChoiceQuestion;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizQuestion;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerQuestion;

/**
 * Spring Data JPA repository for the QuizExercise entity.
 */
@Profile(PROFILE_CORE)
@Lazy
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
    @Query("""
            SELECT qe
            FROM QuizExercise qe
            WHERE qe.id = :quizExerciseId
            """)
    Optional<QuizExercise> findBaseWithEagerQuestionsAndStatisticsById(@Param("quizExerciseId") Long quizExerciseId);

    @Transactional(readOnly = true)
    default Optional<QuizExercise> findWithEagerQuestionsAndStatisticsById(Long quizExerciseId) {
        return withEagerQuestionChildCollections(findBaseWithEagerQuestionsAndStatisticsById(quizExerciseId));
    }

    @EntityGraph(type = LOAD, attributePaths = { "quizQuestions", "quizPointStatistic", "quizQuestions.quizQuestionStatistic", "categories", "competencyLinks.competency",
            "quizBatches", "gradingCriteria" })
    @Query("""
            SELECT qe
            FROM QuizExercise qe
            WHERE qe.id = :quizExerciseId
            """)
    Optional<QuizExercise> findBaseWithEagerQuestionsAndStatisticsAndCompetenciesAndBatchesAndGradingCriteriaById(@Param("quizExerciseId") Long quizExerciseId);

    @Transactional(readOnly = true)
    default Optional<QuizExercise> findWithEagerQuestionsAndStatisticsAndCompetenciesAndBatchesAndGradingCriteriaById(Long quizExerciseId) {
        return withEagerQuestionChildCollections(findBaseWithEagerQuestionsAndStatisticsAndCompetenciesAndBatchesAndGradingCriteriaById(quizExerciseId));
    }

    @EntityGraph(type = LOAD, attributePaths = { "quizQuestions" })
    @Query("""
            SELECT qe
            FROM QuizExercise qe
            WHERE qe.id = :quizExerciseId
            """)
    Optional<QuizExercise> findBaseWithEagerQuestionsById(@Param("quizExerciseId") Long quizExerciseId);

    @Transactional(readOnly = true)
    default Optional<QuizExercise> findWithEagerQuestionsById(Long quizExerciseId) {
        return withEagerQuestionChildCollections(findBaseWithEagerQuestionsById(quizExerciseId));
    }

    @EntityGraph(type = LOAD, attributePaths = { "quizQuestions", "competencyLinks.competency" })
    @Query("""
            SELECT qe
            FROM QuizExercise qe
            WHERE qe.id = :quizExerciseId
            """)
    Optional<QuizExercise> findBaseWithEagerQuestionsAndCompetenciesById(@Param("quizExerciseId") Long quizExerciseId);

    @Transactional(readOnly = true)
    default Optional<QuizExercise> findWithEagerQuestionsAndCompetenciesById(Long quizExerciseId) {
        return withEagerQuestionChildCollections(findBaseWithEagerQuestionsAndCompetenciesById(quizExerciseId));
    }

    @Query("""
            SELECT DISTINCT question
            FROM MultipleChoiceQuestion question
                LEFT JOIN FETCH question.answerOptions
            WHERE question.exercise.id = :quizExerciseId
            """)
    List<MultipleChoiceQuestion> findMultipleChoiceQuestionsWithAnswerOptionsByExerciseId(@Param("quizExerciseId") Long quizExerciseId);

    @Query("""
            SELECT DISTINCT question
            FROM DragAndDropQuestion question
                LEFT JOIN FETCH question.dropLocations
            WHERE question.exercise.id = :quizExerciseId
            """)
    List<DragAndDropQuestion> findDragAndDropQuestionsWithDropLocationsByExerciseId(@Param("quizExerciseId") Long quizExerciseId);

    @Query("""
            SELECT DISTINCT question
            FROM DragAndDropQuestion question
                LEFT JOIN FETCH question.dragItems
            WHERE question.exercise.id = :quizExerciseId
            """)
    List<DragAndDropQuestion> findDragAndDropQuestionsWithDragItemsByExerciseId(@Param("quizExerciseId") Long quizExerciseId);

    @Query("""
            SELECT DISTINCT question
            FROM DragAndDropQuestion question
                LEFT JOIN FETCH question.correctMappings mapping
                LEFT JOIN FETCH mapping.dragItem
                LEFT JOIN FETCH mapping.dropLocation
            WHERE question.exercise.id = :quizExerciseId
            """)
    List<DragAndDropQuestion> findDragAndDropQuestionsWithCorrectMappingsByExerciseId(@Param("quizExerciseId") Long quizExerciseId);

    @Query("""
            SELECT DISTINCT question
            FROM ShortAnswerQuestion question
                LEFT JOIN FETCH question.spots
            WHERE question.exercise.id = :quizExerciseId
            """)
    List<ShortAnswerQuestion> findShortAnswerQuestionsWithSpotsByExerciseId(@Param("quizExerciseId") Long quizExerciseId);

    @Query("""
            SELECT DISTINCT question
            FROM ShortAnswerQuestion question
                LEFT JOIN FETCH question.solutions
            WHERE question.exercise.id = :quizExerciseId
            """)
    List<ShortAnswerQuestion> findShortAnswerQuestionsWithSolutionsByExerciseId(@Param("quizExerciseId") Long quizExerciseId);

    @Query("""
            SELECT DISTINCT question
            FROM ShortAnswerQuestion question
                LEFT JOIN FETCH question.correctMappings mapping
                LEFT JOIN FETCH mapping.spot
                LEFT JOIN FETCH mapping.solution
            WHERE question.exercise.id = :quizExerciseId
            """)
    List<ShortAnswerQuestion> findShortAnswerQuestionsWithCorrectMappingsByExerciseId(@Param("quizExerciseId") Long quizExerciseId);

    private Optional<QuizExercise> withEagerQuestionChildCollections(Optional<QuizExercise> quizExercise) {
        quizExercise.ifPresent(this::loadQuizQuestionChildCollections);
        return quizExercise;
    }

    private void loadQuizQuestionChildCollections(QuizExercise quizExercise) {
        if (quizExercise.getQuizQuestions() == null) {
            return;
        }

        boolean hasMultipleChoiceQuestion = false;
        boolean hasDragAndDropQuestion = false;
        boolean hasShortAnswerQuestion = false;
        for (QuizQuestion quizQuestion : quizExercise.getQuizQuestions()) {
            hasMultipleChoiceQuestion = hasMultipleChoiceQuestion || quizQuestion instanceof MultipleChoiceQuestion;
            hasDragAndDropQuestion = hasDragAndDropQuestion || quizQuestion instanceof DragAndDropQuestion;
            hasShortAnswerQuestion = hasShortAnswerQuestion || quizQuestion instanceof ShortAnswerQuestion;
        }

        Long quizExerciseId = quizExercise.getId();
        if (hasMultipleChoiceQuestion) {
            findMultipleChoiceQuestionsWithAnswerOptionsByExerciseId(quizExerciseId);
        }
        if (hasDragAndDropQuestion) {
            findDragAndDropQuestionsWithDropLocationsByExerciseId(quizExerciseId);
            findDragAndDropQuestionsWithDragItemsByExerciseId(quizExerciseId);
            findDragAndDropQuestionsWithCorrectMappingsByExerciseId(quizExerciseId);
        }
        if (hasShortAnswerQuestion) {
            findShortAnswerQuestionsWithSpotsByExerciseId(quizExerciseId);
            findShortAnswerQuestionsWithSolutionsByExerciseId(quizExerciseId);
            findShortAnswerQuestionsWithCorrectMappingsByExerciseId(quizExerciseId);
        }
    }

    @EntityGraph(type = LOAD, attributePaths = { "quizBatches" })
    Optional<QuizExercise> findWithEagerBatchesById(Long quizExerciseId);

    @Query("""
            SELECT q
            FROM QuizExercise q
                LEFT JOIN FETCH q.competencyLinks cl
                LEFT JOIN FETCH cl.competency
            WHERE q.title = :title
                AND q.course.id = :courseId
            """)
    Set<QuizExercise> findAllWithCompetenciesByTitleAndCourseId(@Param("title") String title, @Param("courseId") long courseId);

    @Query("""
            SELECT new de.tum.cit.aet.artemis.calendar.dto.QuizExerciseCalendarEventDTO(
                exercise.id,
                exercise.quizMode,
                exercise.title,
                exercise.releaseDate,
                exercise.dueDate,
                batch.startTime,
                exercise.duration
            )
            FROM QuizExercise exercise
                LEFT JOIN exercise.quizBatches batch ON exercise.quizMode = de.tum.cit.aet.artemis.quiz.domain.QuizMode.SYNCHRONIZED
            WHERE exercise.course.id = :courseId
            """)
    Set<QuizExerciseCalendarEventDTO> getQuizExerciseCalendarEventDTOsForCourseId(@Param("courseId") long courseId);

    /**
     * Finds a QuizExercise with minimal data necessary for exercise versioning.
     * Only includes core configuration data, NOT submissions, results, or statistics.
     * This includes: quizQuestions (without specific answer options to avoid polymorphic issues)
     *
     * @param exerciseId the id of the exercise to fetch
     * @return {@link QuizExercise}
     */
    @EntityGraph(type = LOAD, attributePaths = { "quizQuestions", "competencyLinks", "categories", "teamAssignmentConfig", "gradingCriteria", "plagiarismDetectionConfig" })
    Optional<QuizExercise> findForVersioningById(Long exerciseId);

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

    @NonNull
    default QuizExercise findWithEagerBatchesByIdOrElseThrow(Long quizExerciseId) {
        return getValueElseThrow(findWithEagerBatchesById(quizExerciseId), quizExerciseId);
    }

    /**
     * Get one quiz exercise by id and eagerly load questions
     *
     * @param quizExerciseId the id of the entity
     * @return the entity
     */
    @NonNull
    default QuizExercise findByIdWithQuestionsElseThrow(Long quizExerciseId) {
        return getValueElseThrow(findWithEagerQuestionsById(quizExerciseId), quizExerciseId);
    }

    /**
     * Get one quiz exercise by id and eagerly load questions
     *
     * @param quizExerciseId the id of the entity
     * @return the entity
     */
    @NonNull
    default QuizExercise findByIdWithQuestionsAndCompetenciesElseThrow(Long quizExerciseId) {
        return getValueElseThrow(findWithEagerQuestionsAndCompetenciesById(quizExerciseId), quizExerciseId);
    }

    /**
     * Get one quiz exercise by id and eagerly load batches
     *
     * @param quizExerciseId the id of the entity
     * @return the entity
     */
    @NonNull
    default QuizExercise findByIdWithBatchesElseThrow(Long quizExerciseId) {
        return getValueElseThrow(findWithEagerBatchesById(quizExerciseId), quizExerciseId);
    }

    @NonNull
    default QuizExercise findByIdWithQuestionsAndStatisticsElseThrow(Long quizExerciseId) {
        return getValueElseThrow(findWithEagerQuestionsAndStatisticsById(quizExerciseId), quizExerciseId);
    }

    @NonNull
    default QuizExercise findByIdWithQuestionsAndStatisticsAndCompetenciesAndBatchesAndGradingCriteriaElseThrow(Long quizExerciseId) {
        return getValueElseThrow(findWithEagerQuestionsAndStatisticsAndCompetenciesAndBatchesAndGradingCriteriaById(quizExerciseId), quizExerciseId);
    }

    /**
     * Targeted UPDATE of releaseDate + dueDate, used by START_NOW. Issues a single-row UPDATE on
     * {@code QuizExercise} instead of {@code saveAndFlush(quizExercise)}.
     *
     * <p>
     * The original motivation was to bypass the DELETE+INSERT cascade on the unidirectional
     * {@code @OneToMany + @JoinColumn + @OrderColumn} child collections — that bug class is fixed at the mapping
     * level now (issues #12574 / #12584, bidirectional {@code mappedBy}), but this targeted UPDATE is retained
     * because it remains the cheapest correct option for the lifecycle action: it avoids loading the full quiz
     * graph, skips the {@code @PrePersist}/{@code @PreUpdate} hooks on every child question, and produces a
     * single-row UPDATE that is atomic and easy to reason about.
     *
     * @param id          the id of the quiz exercise to update
     * @param releaseDate the new release date (may be {@code null})
     * @param dueDate     the new due date
     */
    @Transactional // ok because of modifying query
    @Modifying
    @Query("""
            UPDATE QuizExercise qe
            SET qe.releaseDate = :releaseDate,
                qe.dueDate = :dueDate
            WHERE qe.id = :id
            """)
    void updateReleaseAndDueDate(@Param("id") Long id, @Param("releaseDate") ZonedDateTime releaseDate, @Param("dueDate") ZonedDateTime dueDate);

    /**
     * Targeted UPDATE of releaseDate, used by SET_VISIBLE. See {@link #updateReleaseAndDueDate} for why this
     * is preferred over a full {@code saveAndFlush(quizExercise)}.
     *
     * @param id          the id of the quiz exercise to update
     * @param releaseDate the new release date
     */
    @Transactional // ok because of modifying query
    @Modifying
    @Query("""
            UPDATE QuizExercise qe
            SET qe.releaseDate = :releaseDate
            WHERE qe.id = :id
            """)
    void updateReleaseDate(@Param("id") Long id, @Param("releaseDate") ZonedDateTime releaseDate);

    /**
     * Targeted UPDATE of dueDate, used by END_NOW. See {@link #updateReleaseAndDueDate} for why this is
     * preferred over a full {@code saveAndFlush(quizExercise)}.
     *
     * @param id      the id of the quiz exercise to update
     * @param dueDate the new due date
     */
    @Transactional // ok because of modifying query
    @Modifying
    @Query("""
            UPDATE QuizExercise qe
            SET qe.dueDate = :dueDate
            WHERE qe.id = :id
            """)
    void updateDueDate(@Param("id") Long id, @Param("dueDate") ZonedDateTime dueDate);
}
