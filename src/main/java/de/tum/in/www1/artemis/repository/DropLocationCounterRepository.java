package de.tum.in.www1.artemis.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.quiz.DropLocationCounter;

/**
 * Spring Data JPA repository for the DropLocationCounter entity.
 */
@SuppressWarnings("unused")
@Repository
public interface DropLocationCounterRepository extends JpaRepository<DropLocationCounter, Long> {

}
