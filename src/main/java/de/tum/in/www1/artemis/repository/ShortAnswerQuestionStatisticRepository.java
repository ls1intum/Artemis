package de.tum.in.www1.artemis.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.quiz.ShortAnswerQuestionStatistic;

/**
 * Spring Data repository for the ShortAnswerQuestionStatistic entity.
 */
@SuppressWarnings("unused")
@Repository
public interface ShortAnswerQuestionStatisticRepository extends JpaRepository<ShortAnswerQuestionStatistic, Long> {

}
