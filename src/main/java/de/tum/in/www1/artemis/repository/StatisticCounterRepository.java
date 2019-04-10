package de.tum.in.www1.artemis.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.quiz.QuizStatisticCounter;

/**
 * Spring Data JPA repository for the QuizStatisticCounter entity.
 */
@SuppressWarnings("unused")
@Repository
public interface StatisticCounterRepository extends JpaRepository<QuizStatisticCounter, Long> {

}
