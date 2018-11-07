package de.tum.in.www1.artemis.repository;

import de.tum.in.www1.artemis.domain.ShortAnswerSubmittedAnswer;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;


/**
 * Spring Data  repository for the ShortAnswerSubmittedAnswer entity.
 */
@SuppressWarnings("unused")
@Repository
public interface ShortAnswerSubmittedAnswerRepository extends JpaRepository<ShortAnswerSubmittedAnswer, Long> {

}
