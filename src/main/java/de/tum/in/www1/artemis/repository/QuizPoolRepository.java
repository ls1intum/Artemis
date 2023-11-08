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

    @Query("""
                    SELECT qe
                    FROM QuizPool qe
                        JOIN qe.exam e
                        LEFT JOIN FETCH qe.quizQuestions qeq
                        LEFT JOIN FETCH qeq.quizQuestionStatistic
                    WHERE e.id = :examId
            """)
    Optional<QuizPool> findWithEagerQuizQuestionsByExamId(Long examId);
}
