package de.tum.in.www1.artemis.repository;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.quiz.ShortAnswerMapping;
import de.tum.in.www1.artemis.repository.base.ArtemisJpaRepository;

/**
 * Spring Data JPA repository for the ShortAnswerMapping entity.
 */
@Profile(PROFILE_CORE)
@Repository
public interface ShortAnswerMappingRepository extends ArtemisJpaRepository<ShortAnswerMapping, Long> {

}
