package de.tum.in.www1.artemis.repository;

import de.tum.in.www1.artemis.domain.MultipleChoiceQuestionStatistic;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;


/**
 * Spring Data  repository for the MultipleChoiceQuestionStatistic entity.
 */
@SuppressWarnings("unused")
@Repository
public interface MultipleChoiceQuestionStatisticRepository extends JpaRepository<MultipleChoiceQuestionStatistic, Long> {

}
