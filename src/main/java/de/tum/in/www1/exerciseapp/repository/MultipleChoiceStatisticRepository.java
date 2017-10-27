package de.tum.in.www1.exerciseapp.repository;

import de.tum.in.www1.exerciseapp.domain.MultipleChoiceStatistic;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.*;


/**
 * Spring Data JPA repository for the MultipleChoiceStatistic entity.
 */
@SuppressWarnings("unused")
@Repository
public interface MultipleChoiceStatisticRepository extends JpaRepository<MultipleChoiceStatistic,Long> {
    
}
