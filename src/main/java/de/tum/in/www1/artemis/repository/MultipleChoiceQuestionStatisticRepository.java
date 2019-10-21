package de.tum.in.www1.artemis.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.quiz.MultipleChoiceQuestionStatistic;

/**
 * Spring Data JPA repository for the MultipleChoiceQuestionStatistic entity.
 */
@SuppressWarnings("unused")
@Repository
public interface MultipleChoiceQuestionStatisticRepository extends JpaRepository<MultipleChoiceQuestionStatistic, Long> {

}
