package de.tum.cit.aet.artemis.quiz.test_repository;

import java.util.Optional;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.quiz.domain.QuizQuestionProgress;
import de.tum.cit.aet.artemis.quiz.repository.QuizQuestionProgressRepository;

@Lazy
@Repository
@Primary
public interface QuizQuestionProgressTestRepository extends QuizQuestionProgressRepository {

    Optional<QuizQuestionProgress> findByUserIdAndQuizQuestionId(Long userId, Long quizQuestionId);
}
