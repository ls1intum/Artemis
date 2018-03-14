package de.tum.in.www1.artemis.repository;

import de.tum.in.www1.artemis.domain.QuizPointStatistic;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


/**
 * Spring Data JPA repository for the QuizPointStatistic entity.
 */
@SuppressWarnings("unused")
@Repository
public interface QuizPointStatisticRepository extends JpaRepository<QuizPointStatistic,Long> {

}
