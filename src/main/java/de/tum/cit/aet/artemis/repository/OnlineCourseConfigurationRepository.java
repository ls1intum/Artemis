package de.tum.cit.aet.artemis.repository;

import static de.tum.cit.aet.artemis.config.Constants.PROFILE_CORE;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.domain.OnlineCourseConfiguration;
import de.tum.cit.aet.artemis.repository.base.ArtemisJpaRepository;

/**
 * Spring Data JPA repository for the OnlineCourseConfiguration entity.
 */
@Profile(PROFILE_CORE)
@Repository
public interface OnlineCourseConfigurationRepository extends ArtemisJpaRepository<OnlineCourseConfiguration, Long> {
    // This interface is intentionally left blank. Spring Data JPA generates the implementation at runtime.
}
