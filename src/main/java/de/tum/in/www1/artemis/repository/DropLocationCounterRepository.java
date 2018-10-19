package de.tum.in.www1.artemis.repository;

import de.tum.in.www1.artemis.domain.DropLocationCounter;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;


/**
 * Spring Data  repository for the DropLocationCounter entity.
 */
@SuppressWarnings("unused")
@Repository
public interface DropLocationCounterRepository extends JpaRepository<DropLocationCounter, Long> {

}
