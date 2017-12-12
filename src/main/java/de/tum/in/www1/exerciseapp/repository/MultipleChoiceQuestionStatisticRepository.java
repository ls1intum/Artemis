package de.tum.in.www1.exerciseapp.repository;

import de.tum.in.www1.exerciseapp.domain.MultipleChoiceQuestionStatistic;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.*;


/**
 * Spring Data JPA repository for the MultipleChoiceQuestionStatistic entity.
 */
@SuppressWarnings("unused")
@Repository
public interface MultipleChoiceQuestionStatisticRepository extends JpaRepository<MultipleChoiceQuestionStatistic,Long> {
    
}
