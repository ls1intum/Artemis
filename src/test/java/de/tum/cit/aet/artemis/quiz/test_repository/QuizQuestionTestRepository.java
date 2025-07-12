package de.tum.cit.aet.artemis.quiz.test_repository;

import java.util.Set;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.quiz.domain.QuizQuestion;
import de.tum.cit.aet.artemis.quiz.repository.QuizQuestionRepository;

@Lazy
@Repository
@Primary
public interface QuizQuestionTestRepository extends QuizQuestionRepository {

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
    Set<QuizQuestion> findAllQuizQuestionsByCourseId(@Param("courseId") Long courseId);
}
