package de.tum.in.www1.exerciseapp.repository;

import de.tum.in.www1.exerciseapp.domain.DropLocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


/**
 * Spring Data JPA repository for the DropLocation entity.
 */
@SuppressWarnings("unused")
@Repository
public interface DropLocationRepository extends JpaRepository<DropLocation, Long> {

}
