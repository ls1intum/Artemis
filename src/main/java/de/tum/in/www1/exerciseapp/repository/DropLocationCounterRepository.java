package de.tum.in.www1.exerciseapp.repository;

import de.tum.in.www1.exerciseapp.domain.DropLocationCounter;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.*;


/**
 * Spring Data JPA repository for the DropLocationCounter entity.
 */
@SuppressWarnings("unused")
@Repository
public interface DropLocationCounterRepository extends JpaRepository<DropLocationCounter,Long> {
    
}
