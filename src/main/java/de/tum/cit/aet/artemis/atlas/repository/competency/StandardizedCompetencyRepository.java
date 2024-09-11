package de.tum.cit.aet.artemis.atlas.repository.competency;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.domain.competency.StandardizedCompetency;

/**
 * Spring Data JPA repository for the {@link StandardizedCompetency} entity.
 */
@Profile(PROFILE_CORE)
@Repository
public interface StandardizedCompetencyRepository extends ArtemisJpaRepository<StandardizedCompetency, Long> {
}
