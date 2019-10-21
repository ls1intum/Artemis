package de.tum.in.www1.artemis.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.quiz.ShortAnswerSpotCounter;

/**
 * Spring Data repository for the ShortAnswerSpotCounter entity.
 */
@SuppressWarnings("unused")
@Repository
public interface ShortAnswerSpotCounterRepository extends JpaRepository<ShortAnswerSpotCounter, Long> {

}
