package de.tum.in.www1.artemis.repository;

import de.tum.in.www1.artemis.domain.Result;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.*;


/**
 * Spring Data JPA repository for the Result entity.
 */
@SuppressWarnings("unused")
@Repository
public interface ResultRepository extends JpaRepository<Result, Long> {

}
