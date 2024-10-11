package de.tum.cit.aet.artemis.core.repository;

import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.domain.LLMTokenUsage;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

@Repository
public interface LLMTokenUsageRepository extends ArtemisJpaRepository<LLMTokenUsage, Long> {
}
