package de.tum.in.www1.artemis.repository;

import de.tum.in.www1.artemis.domain.ShortAnswerSubmittedText;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;


/**
 * Spring Data  repository for the ShortAnswerSubmittedText entity.
 */
@SuppressWarnings("unused")
@Repository
public interface ShortAnswerSubmittedTextRepository extends JpaRepository<ShortAnswerSubmittedText, Long> {

}
