package de.tum.in.www1.artemis.repository;

import de.tum.in.www1.artemis.domain.ShortAnswerSpotCounter;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;


/**
 * Spring Data  repository for the ShortAnswerSpotCounter entity.
 */
@SuppressWarnings("unused")
@Repository
public interface ShortAnswerSpotCounterRepository extends JpaRepository<ShortAnswerSpotCounter, Long> {

}
