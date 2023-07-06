package de.tum.in.www1.artemis.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.quiz.MultipleChoiceSubmittedAnswer;

/**
 * Spring Data JPA repository for the MultipleChoiceSubmittedAnswer entity.
 */
@Repository
public interface MultipleChoiceSubmittedAnswerRepository extends JpaRepository<MultipleChoiceSubmittedAnswer, Long> {
}
