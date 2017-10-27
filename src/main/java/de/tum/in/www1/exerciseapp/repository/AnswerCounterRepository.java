package de.tum.in.www1.exerciseapp.repository;

import de.tum.in.www1.exerciseapp.domain.AnswerCounter;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.*;


/**
 * Spring Data JPA repository for the AnswerCounter entity.
 */
@SuppressWarnings("unused")
@Repository
public interface AnswerCounterRepository extends JpaRepository<AnswerCounter,Long> {
    
}
