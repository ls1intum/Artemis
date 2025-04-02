package de.tum.cit.aet.artemis.atlas.repository;

import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;
import de.tum.cit.aet.artemis.atlas.domain.competency.Source;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

/**
 * Spring Data JPA repository for the {@link Source} entity.
 */
@Conditional(AtlasEnabled.class)
@Repository
public interface SourceRepository extends ArtemisJpaRepository<Source, Long> {

}
