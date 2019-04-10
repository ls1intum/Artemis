package de.tum.in.www1.artemis.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.quiz.ShortAnswerSubmittedText;

/**
 * Spring Data repository for the ShortAnswerSubmittedText entity.
 */
@SuppressWarnings("unused")
@Repository
public interface ShortAnswerSubmittedTextRepository extends JpaRepository<ShortAnswerSubmittedText, Long> {

}
