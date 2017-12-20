package de.tum.in.www1.exerciseapp.repository;

import de.tum.in.www1.exerciseapp.domain.QuizPointStatistic;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.*;


/**
 * Spring Data JPA repository for the QuizPointStatistic entity.
 */
@SuppressWarnings("unused")
@Repository
public interface QuizPointStatisticRepository extends JpaRepository<QuizPointStatistic,Long> {
    
}
