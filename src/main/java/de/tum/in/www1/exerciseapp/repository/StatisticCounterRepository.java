package de.tum.in.www1.exerciseapp.repository;

import de.tum.in.www1.exerciseapp.domain.StatisticCounter;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.*;


/**
 * Spring Data JPA repository for the StatisticCounter entity.
 */
@SuppressWarnings("unused")
@Repository
public interface StatisticCounterRepository extends JpaRepository<StatisticCounter,Long> {
    
}
