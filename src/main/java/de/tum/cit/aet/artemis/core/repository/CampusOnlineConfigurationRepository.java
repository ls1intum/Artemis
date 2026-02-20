package de.tum.cit.aet.artemis.core.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.domain.CampusOnlineConfiguration;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

/**
 * Spring Data JPA repository for {@link CampusOnlineConfiguration} entities.
 * Provides basic CRUD operations for the CAMPUSOnline configuration linked to Artemis courses.
 */
@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface CampusOnlineConfigurationRepository extends ArtemisJpaRepository<CampusOnlineConfiguration, Long> {

}
