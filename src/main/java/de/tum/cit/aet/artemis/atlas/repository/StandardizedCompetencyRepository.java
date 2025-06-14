package de.tum.cit.aet.artemis.atlas.repository;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;
import de.tum.cit.aet.artemis.atlas.domain.competency.StandardizedCompetency;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

/**
 * Spring Data JPA repository for the {@link StandardizedCompetency} entity.
 */
@Conditional(AtlasEnabled.class)
@Lazy
@Repository
public interface StandardizedCompetencyRepository extends ArtemisJpaRepository<StandardizedCompetency, Long> {
}
