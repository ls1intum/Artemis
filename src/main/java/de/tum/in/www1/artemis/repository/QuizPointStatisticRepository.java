package de.tum.in.www1.artemis.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.quiz.QuizPointStatistic;

/**
 * Spring Data JPA repository for the QuizPointStatistic entity.
 */
@Repository
public interface QuizPointStatisticRepository extends JpaRepository<QuizPointStatistic, Long> {

}
