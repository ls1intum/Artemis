package de.tum.in.www1.artemis.repository;

import de.tum.in.www1.artemis.domain.AnswerCounter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


/**
 * Spring Data JPA repository for the AnswerCounter entity.
 */
@SuppressWarnings("unused")
@Repository
public interface AnswerCounterRepository extends JpaRepository<AnswerCounter,Long> {

}
