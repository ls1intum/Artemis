package de.tum.cit.aet.artemis.quiz.test_repository;

import java.util.Set;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.quiz.domain.QuizSubmission;
import de.tum.cit.aet.artemis.quiz.domain.SubmittedAnswer;
import de.tum.cit.aet.artemis.quiz.repository.SubmittedAnswerRepository;

@Lazy
@Repository
@Primary
public interface SubmittedAnswerTestRepository extends SubmittedAnswerRepository {

    Set<SubmittedAnswer> findBySubmission(QuizSubmission submission);
}
