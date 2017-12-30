package de.tum.in.www1.exerciseapp.repository;

import de.tum.in.www1.exerciseapp.domain.PointCounter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


/**
 * Spring Data JPA repository for the PointCounter entity.
 */
@SuppressWarnings("unused")
@Repository
public interface PointCounterRepository extends JpaRepository<PointCounter,Long> {

}
