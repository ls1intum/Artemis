package de.tum.in.www1.artemis.repository;

import de.tum.in.www1.artemis.domain.MultipleChoiceQuestion;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.*;


/**
 * Spring Data JPA repository for the MultipleChoiceQuestion entity.
 */
@SuppressWarnings("unused")
@Repository
public interface MultipleChoiceQuestionRepository extends JpaRepository<MultipleChoiceQuestion, Long> {

}
