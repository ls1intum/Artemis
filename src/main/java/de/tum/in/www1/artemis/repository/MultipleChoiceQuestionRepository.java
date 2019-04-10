package de.tum.in.www1.artemis.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.quiz.MultipleChoiceQuestion;

/**
 * Spring Data JPA repository for the MultipleChoiceQuestion entity.
 */
@SuppressWarnings("unused")
@Repository
public interface MultipleChoiceQuestionRepository extends JpaRepository<MultipleChoiceQuestion, Long> {

}
