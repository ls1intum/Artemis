package de.tum.cit.aet.artemis.quiz.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Optional;
import java.util.Set;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    /**
     * Finds all quiz question from a course that are open for practice.
     *
     * @param courseId of the course
     * @return a set of quiz questions
     */
    @Query("""
            SELECT q
            FROM QuizQuestion q
            WHERE q.exercise.course.id = :courseId AND q.exercise.isOpenForPractice = TRUE
            """)
    Page<QuizQuestion> findAllPracticeQuizQuestionsByCourseId(@Param("courseId") Long courseId, Pageable pageable);

    Page<QuizQuestion> findAllById(Set<Long> ids, Pageable pageable);

    @Query("""
            SELECT COUNT(q) > 0
            FROM QuizQuestion q
            WHERE q.exercise.course.id = :courseId AND q.exercise.isOpenForPractice = TRUE
            """)
    boolean areQuizQuestionsAvailableForPractice(@Param("courseId") Long courseId);

    default DragAndDropQuestion findDnDQuestionByIdOrElseThrow(Long questionId) {
        return getValueElseThrow(findDnDQuestionById(questionId), questionId);
    }
}
