package de.tum.in.www1.artemis.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.quiz.QuizStatistic;

/**
 * Spring Data JPA repository for the QuizStatistic entity.
 */
@SuppressWarnings("unused")
@Repository
public interface StatisticRepository extends JpaRepository<QuizStatistic, Long> {

}
