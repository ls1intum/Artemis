package de.tum.in.www1.artemis.repository;

import de.tum.in.www1.artemis.domain.PointCounter;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;


/**
 * Spring Data  repository for the PointCounter entity.
 */
@SuppressWarnings("unused")
@Repository
public interface PointCounterRepository extends JpaRepository<PointCounter, Long> {

}
