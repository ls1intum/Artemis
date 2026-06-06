package de.tum.cit.aet.artemis.quiz.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.quiz.domain.DragAndDropQuestion;
import de.tum.cit.aet.artemis.quiz.domain.MultipleChoiceQuestion;
import de.tum.cit.aet.artemis.quiz.domain.QuizQuestion;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerQuestion;

/**
 * Spring Data JPA repository for the QuizQuestion entity.
 */
@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface QuizQuestionRepository extends ArtemisJpaRepository<QuizQuestion, Long> {

    Set<QuizQuestion> findByExercise_Id(long id);

    /**
     * Finds all quiz questions for an exercise with their type-specific child collections initialized.
     *
     * @param exerciseId the id of the quiz exercise
     * @return the quiz questions with initialized child collections
     */
    @Transactional(readOnly = true)
    default Set<QuizQuestion> findByExerciseIdWithChildCollections(long exerciseId) {
        Set<QuizQuestion> questions = findByExercise_Id(exerciseId);
        questions.forEach(this::initializeChildCollections);
        return questions;
    }

    @Query("""
            SELECT question
            FROM QuizQuestion question
            WHERE question.exercise.id IN :exerciseIds
            """)
    Set<QuizQuestion> findAllByExerciseIds(@Param("exerciseIds") Set<Long> exerciseIds);

    @Query("""
            SELECT question
            FROM DragAndDropQuestion question
            WHERE question.id = :questionId
            """)
    Optional<DragAndDropQuestion> findDnDQuestionById(@Param("questionId") long questionId);

    @Query("""
            SELECT q
            FROM QuizQuestion q
            WHERE q.exercise.course.id = :courseId AND q.exercise.dueDate IS NOT NULL AND q.exercise.dueDate < :now
            """)
    Slice<QuizQuestion> findAllQuizQuestionsByCourseIdWithDueDateBefore(@Param("courseId") long courseId, @Param("now") ZonedDateTime now, Pageable pageable);

    @Transactional(readOnly = true)
    default Slice<QuizQuestion> findAllWithChildCollectionsByCourseIdWithDueDateBefore(long courseId, ZonedDateTime now, Pageable pageable) {
        Slice<QuizQuestion> slice = findAllQuizQuestionsByCourseIdWithDueDateBefore(courseId, now, pageable);
        slice.getContent().forEach(this::initializeChildCollections);
        return slice;
    }

    @Query("""
            SELECT q
            FROM QuizQuestion q
            WHERE q.exercise.course.id = :courseId AND q.exercise.dueDate IS NOT NULL AND q.exercise.dueDate < :now AND q.id NOT IN (:ids)
            """)
    Slice<QuizQuestion> findAllQuizQuestionsByCourseIdWithDueDateBeforeNotIn(@Param("ids") Set<Long> ids, @Param("courseId") long courseId, @Param("now") ZonedDateTime now,
            Pageable pageable);

    @Transactional(readOnly = true)
    default Slice<QuizQuestion> findAllWithChildCollectionsByCourseIdWithDueDateBeforeNotIn(Set<Long> ids, long courseId, ZonedDateTime now, Pageable pageable) {
        Slice<QuizQuestion> slice = findAllQuizQuestionsByCourseIdWithDueDateBeforeNotIn(ids, courseId, now, pageable);
        slice.getContent().forEach(this::initializeChildCollections);
        return slice;
    }

    @Query("""
            SELECT COUNT(q) > 0
            FROM QuizQuestion q
            WHERE q.exercise.course.id = :courseId AND q.exercise.dueDate IS NOT NULL AND q.exercise.dueDate < :now
            """)
    boolean areQuizExercisesWithDueDateBefore(@Param("courseId") long courseId, @Param("now") ZonedDateTime now);

    @Query("""
            SELECT COUNT(q)
            FROM QuizQuestion q
            WHERE q.exercise.course.id = :courseId AND q.exercise.dueDate IS NOT NULL AND q.exercise.dueDate < :now
            """)
    long countAllQuizQuestionsByCourseIdBefore(@Param("courseId") long courseId, @Param("now") ZonedDateTime now);

    default DragAndDropQuestion findDnDQuestionByIdOrElseThrow(Long questionId) {
        return getValueElseThrow(findDnDQuestionById(questionId), questionId);
    }

    @Query("""
            SELECT q
            FROM QuizQuestion q
            WHERE q.id = :questionId AND q.exercise.course.id = :courseId
            """)
    Optional<QuizQuestion> findByIdAndCourseId(@Param("questionId") long questionId, @Param("courseId") long courseId);

    /**
     * Find a quiz question by id, scoped to a specific course. Use this in training-mode endpoints to ensure
     * a student cannot submit (and receive solutions for) a question from a course they are not enrolled in,
     * even if they pass that course's id in the path.
     *
     * @param questionId the id of the quiz question
     * @param courseId   the id of the course the question's exercise must belong to
     * @return the quiz question
     * @throws de.tum.cit.aet.artemis.core.exception.EntityNotFoundException if no question with the given id exists in the course
     */
    default QuizQuestion findByIdAndCourseIdElseThrow(long questionId, long courseId) {
        return getValueElseThrow(findByIdAndCourseId(questionId, courseId), questionId);
    }

    @Query("""
            SELECT DISTINCT q
            FROM MultipleChoiceQuestion q
                LEFT JOIN FETCH q.answerOptions
            WHERE q.id = :questionId
            """)
    Optional<MultipleChoiceQuestion> findMultipleChoiceWithAnswerOptionsById(@Param("questionId") long questionId);

    @Query("""
            SELECT DISTINCT q
            FROM DragAndDropQuestion q
                LEFT JOIN FETCH q.dropLocations
                LEFT JOIN FETCH q.dragItems
            WHERE q.id = :questionId
            """)
    List<DragAndDropQuestion> findDragAndDropWithDropLocationsAndDragItemsById(@Param("questionId") long questionId);

    @Query("""
            SELECT DISTINCT q
            FROM DragAndDropQuestion q
                LEFT JOIN FETCH q.correctMappings mapping
                LEFT JOIN FETCH mapping.dragItem
                LEFT JOIN FETCH mapping.dropLocation
            WHERE q.id = :questionId
            """)
    Optional<DragAndDropQuestion> findDragAndDropWithCorrectMappingsById(@Param("questionId") long questionId);

    @Query("""
            SELECT DISTINCT q
            FROM ShortAnswerQuestion q
                LEFT JOIN FETCH q.spots
                LEFT JOIN FETCH q.solutions
            WHERE q.id = :questionId
            """)
    List<ShortAnswerQuestion> findShortAnswerWithSpotsAndSolutionsById(@Param("questionId") long questionId);

    @Query("""
            SELECT DISTINCT q
            FROM ShortAnswerQuestion q
                LEFT JOIN FETCH q.correctMappings mapping
                LEFT JOIN FETCH mapping.spot
                LEFT JOIN FETCH mapping.solution
            WHERE q.id = :questionId
            """)
    Optional<ShortAnswerQuestion> findShortAnswerWithCorrectMappingsById(@Param("questionId") long questionId);

    /**
     * Find a quiz question by id, scoped to a specific course, with all lazy child collections initialized.
     * Required for training-mode submission endpoints where OSIV is disabled and child collections
     * (answer options, drag items, spots, etc.) must be available for scoring.
     *
     * @param questionId the id of the quiz question
     * @param courseId   the id of the course the question's exercise must belong to
     * @return the quiz question with initialized child collections
     * @throws de.tum.cit.aet.artemis.core.exception.EntityNotFoundException if no question with the given id exists in the course
     */
    @Transactional(readOnly = true)
    default QuizQuestion findByIdAndCourseIdWithChildCollectionsElseThrow(long questionId, long courseId) {
        QuizQuestion question = findByIdAndCourseIdElseThrow(questionId, courseId);
        initializeChildCollections(question);
        return question;
    }

    /**
     * Initializes the type-specific lazy child collections of the given managed quiz question.
     *
     * @param question the quiz question whose child collections should be initialized
     */
    default void initializeChildCollections(QuizQuestion question) {
        long questionId = question.getId();
        switch (question) {
            case MultipleChoiceQuestion ignored -> findMultipleChoiceWithAnswerOptionsById(questionId);
            case DragAndDropQuestion ignored -> {
                findDragAndDropWithDropLocationsAndDragItemsById(questionId);
                findDragAndDropWithCorrectMappingsById(questionId);
            }
            case ShortAnswerQuestion ignored -> {
                findShortAnswerWithSpotsAndSolutionsById(questionId);
                findShortAnswerWithCorrectMappingsById(questionId);
            }
            default -> {
            }
        }
    }
}
