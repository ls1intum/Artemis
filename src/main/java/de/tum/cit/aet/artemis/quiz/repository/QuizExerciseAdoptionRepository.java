package de.tum.cit.aet.artemis.quiz.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizMode;

/**
 * Aggregate counts of how quizzes are configured, for the admin feature usage page.
 */
@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface QuizExerciseAdoptionRepository extends ArtemisJpaRepository<QuizExercise, Long> {

    @Query("""
            SELECT COUNT(quiz)
            FROM QuizExercise quiz
            WHERE quiz.quizMode = :quizMode
            """)
    long countByQuizMode(@Param("quizMode") QuizMode quizMode);
}
