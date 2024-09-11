package de.tum.cit.aet.artemis.quiz.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.domain.quiz.QuizQuestionStatistic;

/**
 * Spring Data JPA repository for the QuizQuestionStatistic entity.
 */
@Profile(PROFILE_CORE)
@Repository
public interface QuizQuestionStatisticRepository extends ArtemisJpaRepository<QuizQuestionStatistic, Long> {

}
