package de.tum.cit.aet.artemis.repo.repository;

import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.lti.domain.OnlineCourseConfiguration;

/**
 * Spring Data JPA repository for the OnlineCourseConfiguration entity.
 */
@Repository
public interface OnlineCourseConfigurationRepository extends ArtemisJpaRepository<OnlineCourseConfiguration, Long> {
    // This interface is intentionally left blank. Spring Data JPA generates the implementation at runtime.
}
