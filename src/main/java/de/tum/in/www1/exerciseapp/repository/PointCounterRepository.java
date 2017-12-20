package de.tum.in.www1.exerciseapp.repository;

import de.tum.in.www1.exerciseapp.domain.PointCounter;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.*;


/**
 * Spring Data JPA repository for the PointCounter entity.
 */
@SuppressWarnings("unused")
@Repository
public interface PointCounterRepository extends JpaRepository<PointCounter,Long> {
    
}
