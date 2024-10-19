package de.tum.cit.aet.artemis.core.repository;

import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.domain.LLMTokenUsageRequest;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

@Repository
public interface LLMTokenUsageRequestRepository extends ArtemisJpaRepository<LLMTokenUsageRequest, Long> {
}
