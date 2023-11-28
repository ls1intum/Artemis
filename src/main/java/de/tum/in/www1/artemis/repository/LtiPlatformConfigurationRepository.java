package de.tum.in.www1.artemis.repository;

import java.util.Optional;

import javax.validation.constraints.NotNull;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.LtiPlatformConfiguration;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Spring Data JPA repository for the OnlineCourseConfiguration entity.
 */
@Repository
public interface LtiPlatformConfigurationRepository extends JpaRepository<LtiPlatformConfiguration, Long> {

    Optional<LtiPlatformConfiguration> findByRegistrationId(String registrationId);

    @NotNull
    default LtiPlatformConfiguration findByIdElseThrow(Long platformId) throws EntityNotFoundException {
        return findById(platformId).orElseThrow(() -> new EntityNotFoundException("LtiPlatformConfiguration", platformId));
    }

}
