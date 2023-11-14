package de.tum.in.www1.artemis.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.LtiPlatformConfiguration;

/**
 * Spring Data JPA repository for the OnlineCourseConfiguration entity.
 */
@Repository
public interface LtiPlatformConfigurationRepository extends JpaRepository<LtiPlatformConfiguration, Long> {

    Optional<LtiPlatformConfiguration> findByRegistrationId(String registrationId);

}
