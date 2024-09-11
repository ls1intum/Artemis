package de.tum.cit.aet.artemis.repository;

import static de.tum.cit.aet.artemis.config.Constants.PROFILE_CORE;

import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.domain.quiz.QuizPool;
import de.tum.cit.aet.artemis.repository.base.ArtemisJpaRepository;

/**
 * Spring Data JPA repository for the QuizPool entity.
 */
@Profile(PROFILE_CORE)
@SuppressWarnings("unused")
@Repository
public interface QuizPoolRepository extends ArtemisJpaRepository<QuizPool, Long> {

    @Query("""
            SELECT qe
            FROM QuizPool qe
                JOIN qe.exam e
                LEFT JOIN FETCH qe.quizQuestions qeq
                LEFT JOIN FETCH qeq.quizQuestionStatistic
            WHERE e.id = :examId
            """)
    Optional<QuizPool> findWithEagerQuizQuestionsByExamId(@Param("examId") Long examId);

    /**
     * Find the quiz pool for the given exam id
     *
     * @param examId exam id to which the quiz pool belongs to
     * @return quiz pool for the given exam id
     */
    Optional<QuizPool> findByExamId(long examId);
}
