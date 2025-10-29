package de.tum.cit.aet.artemis.quiz.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Optional;
import java.util.Set;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.quiz.domain.QuizQuestionProgress;

@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface QuizQuestionProgressRepository extends ArtemisJpaRepository<QuizQuestionProgress, Long> {

    Optional<QuizQuestionProgress> findByUserIdAndQuizQuestionId(long userId, long quizQuestionId);

    Set<QuizQuestionProgress> findAllByUserIdAndCourseId(long userId, long courseId);

    long countByUserIdAndCourseId(long userId, long courseId);

}
