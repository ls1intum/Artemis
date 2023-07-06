package de.tum.in.www1.artemis.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.quiz.PointCounter;

/**
 * Spring Data JPA repository for the PointCounter entity.
 */
@Repository
public interface PointCounterRepository extends JpaRepository<PointCounter, Long> {

}
