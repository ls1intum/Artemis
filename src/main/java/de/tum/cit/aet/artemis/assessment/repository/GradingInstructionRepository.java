package de.tum.cit.aet.artemis.assessment.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.domain.GradingInstruction;

/**
 * Spring Data JPA repository for the GradingInstruction entity.
 */
@Profile(PROFILE_CORE)
@Repository
public interface GradingInstructionRepository extends ArtemisJpaRepository<GradingInstruction, Long> {

}
