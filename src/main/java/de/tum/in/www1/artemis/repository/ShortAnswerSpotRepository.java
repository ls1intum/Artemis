package de.tum.in.www1.artemis.repository;

import de.tum.in.www1.artemis.domain.ShortAnswerSpot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


/**
 * Spring Data  repository for the ShortAnswerSpot entity.
 */
@SuppressWarnings("unused")
@Repository
public interface ShortAnswerSpotRepository extends JpaRepository<ShortAnswerSpot, Long> {

}
