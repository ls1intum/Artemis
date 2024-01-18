package de.tum.in.www1.artemis.repository;

import java.util.Optional;

import javax.validation.constraints.NotNull;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.LtiPlatformConfiguration;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Repository for managing LtiPlatformConfiguration entities.
 */
@Repository
public interface LtiPlatformConfigurationRepository extends JpaRepository<LtiPlatformConfiguration, Long> {

    /**
     * Finds an LTI platform configuration by its registration ID.
     *
     * @param registrationId The registration ID.
     * @return Optional of LtiPlatformConfiguration.
     */
    Optional<LtiPlatformConfiguration> findByRegistrationId(String registrationId);

    /**
     * Finds an LTI platform configuration by ID or throws EntityNotFoundException if not found.
     *
     * @param platformId The ID of the configuration.
     * @return LtiPlatformConfiguration if found.
     * @throws EntityNotFoundException if not found.
     */
    @NotNull
    default LtiPlatformConfiguration findByIdElseThrow(Long platformId) throws EntityNotFoundException {
        return findById(platformId).orElseThrow(() -> new EntityNotFoundException("LtiPlatformConfiguration", platformId));
    }

}
