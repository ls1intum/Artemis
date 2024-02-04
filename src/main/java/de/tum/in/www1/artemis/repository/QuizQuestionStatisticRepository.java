package de.tum.in.www1.artemis.repository;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.quiz.QuizQuestionStatistic;

/**
 * Spring Data JPA repository for the QuizQuestionStatistic entity.
 */
@Profile("core")
@Repository
public interface QuizQuestionStatisticRepository extends JpaRepository<QuizQuestionStatistic, Long> {

}
