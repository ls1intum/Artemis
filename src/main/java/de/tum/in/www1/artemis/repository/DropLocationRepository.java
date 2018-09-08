package de.tum.in.www1.artemis.repository;

import de.tum.in.www1.artemis.domain.DropLocation;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;


/**
 * Spring Data  repository for the DropLocation entity.
 */
@SuppressWarnings("unused")
@Repository
public interface DropLocationRepository extends JpaRepository<DropLocation, Long> {

}
