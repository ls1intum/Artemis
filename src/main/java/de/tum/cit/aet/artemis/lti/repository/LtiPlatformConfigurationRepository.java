package de.tum.cit.aet.artemis.lti.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.Optional;

import jakarta.validation.constraints.NotNull;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.lti.domain.LtiPlatformConfiguration;
import de.tum.cit.aet.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Repository for managing LtiPlatformConfiguration entities.
 */
@Profile(PROFILE_CORE)
@Repository
public interface LtiPlatformConfigurationRepository extends ArtemisJpaRepository<LtiPlatformConfiguration, Long> {

    /**
     * Finds an LTI platform configuration by its registration ID.
     *
     * @param registrationId The registration ID.
     * @return Optional of LtiPlatformConfiguration.
     */
    Optional<LtiPlatformConfiguration> findByRegistrationId(String registrationId);

    /**
     * Fetches an {@link LtiPlatformConfiguration} with its associated online courses eagerly loaded.
     *
     * @param id The ID of the LtiPlatformConfiguration.
     * @return {@link LtiPlatformConfiguration} with eager-loaded courses, or {@code null} if not found.
     * @throws EntityNotFoundException if no entity with the given ID is found.
     */
    @NotNull
    default LtiPlatformConfiguration findLtiPlatformConfigurationWithEagerLoadedCoursesByIdElseThrow(long id) throws EntityNotFoundException {
        return getValueElseThrow(Optional.ofNullable(findWithEagerOnlineCourseConfigurationsById(id)), id);
    }

    /**
     * Retrieves an {@link LtiPlatformConfiguration} by ID with eager-loaded online courses.
     * Intended for internal use with {@link EntityGraph} for optimized fetching.
     *
     * @param platformId The ID of the LtiPlatformConfiguration.
     * @return {@link LtiPlatformConfiguration} with eager-loaded courses, or {@code null} if not found.
     */
    @EntityGraph(type = LOAD, attributePaths = { "onlineCourseConfigurations" })
    LtiPlatformConfiguration findWithEagerOnlineCourseConfigurationsById(long platformId);

    /**
     * Finds an LTI platform configuration by its client ID.
     *
     * @param clientId The registration ID.
     * @return Optional of LtiPlatformConfiguration.
     */
    Optional<LtiPlatformConfiguration> findByClientId(String clientId);
}
