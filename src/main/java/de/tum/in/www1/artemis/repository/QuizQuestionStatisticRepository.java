package de.tum.in.www1.artemis.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.quiz.QuizQuestionStatistic;

/**
 * Spring Data JPA repository for the QuizQuestionStatistic entity.
 */
@Repository
public interface QuizQuestionStatisticRepository extends JpaRepository<QuizQuestionStatistic, Long> {

}
