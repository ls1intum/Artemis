package de.tum.in.www1.artemis.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.quiz.QuizQuestion;

/**
 * Spring Data JPA repository for the QuizQuestion entity.
 */
@SuppressWarnings("unused")
@Repository
public interface QuizQuestionRepository extends JpaRepository<QuizQuestion, Long> {

    @Query("""
                    SELECT qq
                    FROM QuizQuestion qq JOIN qq.quizPool qp
                    WHERE qp.id = :quizPoolId
            """)
    List<QuizQuestion> findAllByQuizPoolId(Long quizPoolId);
}
