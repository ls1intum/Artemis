package de.tum.cit.aet.artemis.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.domain.quiz.QuizPointStatistic;
import de.tum.cit.aet.artemis.repository.base.ArtemisJpaRepository;

/**
 * Spring Data JPA repository for the QuizPointStatistic entity.
 */
@Profile(PROFILE_CORE)
@Repository
public interface QuizPointStatisticRepository extends ArtemisJpaRepository<QuizPointStatistic, Long> {

}
