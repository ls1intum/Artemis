package de.tum.cit.aet.artemis.core.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_IRIS;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.domain.LLMTokenUsage;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

@Repository
@Profile(PROFILE_IRIS)
public interface LLMTokenUsageRepository extends ArtemisJpaRepository<LLMTokenUsage, Long> {
}
