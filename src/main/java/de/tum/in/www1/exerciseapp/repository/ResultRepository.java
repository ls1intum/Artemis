package de.tum.in.www1.exerciseapp.repository;

import de.tum.in.www1.exerciseapp.domain.Result;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.*;


/**
 * Spring Data JPA repository for the Result entity.
 */
@SuppressWarnings("unused")
@Repository
public interface ResultRepository extends JpaRepository<Result, Long> {

}
