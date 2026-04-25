package de.tum.cit.aet.artemis.quiz.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.Set;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.quiz.domain.DragAndDropQuestion;
import de.tum.cit.aet.artemis.quiz.domain.QuizQuestion;

/**
 * Spring Data JPA repository for the QuizQuestion entity.
 */
@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface QuizQuestionRepository extends ArtemisJpaRepository<QuizQuestion, Long> {

    Set<QuizQuestion> findByExercise_Id(long id);

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

    @Query("""
            SELECT q
            FROM QuizQuestion q
            WHERE q.exercise.course.id = :courseId AND q.exercise.dueDate IS NOT NULL AND q.exercise.dueDate < :now AND q.id NOT IN (:ids)
            """)
    Slice<QuizQuestion> findAllQuizQuestionsByCourseIdWithDueDateBeforeNotIn(@Param("ids") Set<Long> ids, @Param("courseId") long courseId, @Param("now") ZonedDateTime now,
            Pageable pageable);

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
}
