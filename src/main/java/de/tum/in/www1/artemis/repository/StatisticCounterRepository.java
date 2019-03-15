package de.tum.in.www1.artemis.repository;

import de.tum.in.www1.artemis.domain.QuizStatisticCounter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


/**
 * Spring Data JPA repository for the QuizStatisticCounter entity.
 */
@SuppressWarnings("unused")
@Repository
public interface StatisticCounterRepository extends JpaRepository<QuizStatisticCounter,Long> {

}
