package de.tum.in.www1.artemis.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.quiz.ShortAnswerSubmittedAnswer;

/**
 * Spring Data repository for the ShortAnswerSubmittedAnswer entity.
 */
@SuppressWarnings("unused")
@Repository
public interface ShortAnswerSubmittedAnswerRepository extends JpaRepository<ShortAnswerSubmittedAnswer, Long> {

}
