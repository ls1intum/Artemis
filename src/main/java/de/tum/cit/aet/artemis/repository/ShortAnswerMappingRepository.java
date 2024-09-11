package de.tum.cit.aet.artemis.repository;

import static de.tum.cit.aet.artemis.config.Constants.PROFILE_CORE;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.domain.quiz.ShortAnswerMapping;
import de.tum.cit.aet.artemis.repository.base.ArtemisJpaRepository;

/**
 * Spring Data JPA repository for the ShortAnswerMapping entity.
 */
@Profile(PROFILE_CORE)
@Repository
public interface ShortAnswerMappingRepository extends ArtemisJpaRepository<ShortAnswerMapping, Long> {

}
