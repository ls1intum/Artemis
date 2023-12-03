package de.tum.in.www1.artemis.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.quiz.QuizPool;

/**
 * Spring Data JPA repository for the QuizPool entity.
 */
@SuppressWarnings("unused")
@Repository
public interface QuizPoolRepository extends JpaRepository<QuizPool, Long> {

    /**
     * Get the quiz pool for the given exam id with eager quiz questions.
     *
     * @param examId the id of the exam
     * @return the quiz pool for the given exam id with eager quiz questions
     */
    @Query("""
                    SELECT qp
                    FROM QuizPool qp
                        JOIN qp.exam e
                        LEFT JOIN FETCH qp.quizQuestions qq
                        LEFT JOIN FETCH qq.quizQuestionStatistic
                    WHERE e.id = :examId
            """)
    Optional<QuizPool> findWithEagerQuizQuestionsByExamId(Long examId);

    /**
     * Find the quiz pool for the given exam id
     *
     * @param examId exam id to which the quiz pool belongs to
     * @return quiz pool for the given exam id
     */
    Optional<QuizPool> findByExamId(long examId);
}
