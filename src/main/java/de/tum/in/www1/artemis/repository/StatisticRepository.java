package de.tum.in.www1.artemis.repository;

import de.tum.in.www1.artemis.domain.QuizStatistic;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


/**
 * Spring Data JPA repository for the QuizStatistic entity.
 */
@SuppressWarnings("unused")
@Repository
public interface StatisticRepository extends JpaRepository<QuizStatistic,Long> {

}
