package de.tum.in.www1.artemis.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.quiz.AnswerCounter;

/**
 * Spring Data JPA repository for the AnswerCounter entity.
 */
@SuppressWarnings("unused")
@Repository
public interface AnswerCounterRepository extends JpaRepository<AnswerCounter, Long> {

}
