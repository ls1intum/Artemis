package de.tum.in.www1.artemis.repository;

import de.tum.in.www1.artemis.domain.AnswerCounter;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;


/**
 * Spring Data  repository for the AnswerCounter entity.
 */
@SuppressWarnings("unused")
@Repository
public interface AnswerCounterRepository extends JpaRepository<AnswerCounter, Long> {

}
