package de.tum.cit.aet.artemis.quiz.repository;

import java.util.Optional;

import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.quiz.domain.QuizQuestionProgress;

@Repository
public interface QuizQuestionProgressRepository extends ArtemisJpaRepository<QuizQuestionProgress, Long> {

    Optional<QuizQuestionProgress> findByUserIdAndQuizQuestionId(Long userId, Long quizQuestionId);

}
