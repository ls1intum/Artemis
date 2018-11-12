package de.tum.in.www1.artemis.repository;

import de.tum.in.www1.artemis.domain.ShortAnswerQuestionStatistic;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


/**
 * Spring Data  repository for the ShortAnswerQuestionStatistic entity.
 */
@SuppressWarnings("unused")
@Repository
public interface ShortAnswerQuestionStatisticRepository extends JpaRepository<ShortAnswerQuestionStatistic, Long> {

}
