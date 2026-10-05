package de.tum.cit.aet.artemis.lti.test_repository;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.Optional;

import org.jspecify.annotations.NonNull;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.lti.domain.LtiPlatformConfiguration;
import de.tum.cit.aet.artemis.lti.repository.LtiPlatformConfigurationRepository;

@Lazy
@Repository
@Primary
public interface LtiPlatformConfigurationTestRepository extends LtiPlatformConfigurationRepository {

    /**
     * Fetches an {@link LtiPlatformConfiguration} with its associated online courses eagerly loaded.
     *
     * @param id The ID of the LtiPlatformConfiguration.
     * @return {@link LtiPlatformConfiguration} with eager-loaded courses, or {@code null} if not found.
     * @throws EntityNotFoundException if no entity with the given ID is found.
     */
    @NonNull
    default LtiPlatformConfiguration findLtiPlatformConfigurationWithEagerLoadedCoursesByIdElseThrow(long id) throws EntityNotFoundException {
        return getValueElseThrow(Optional.ofNullable(findWithEagerOnlineCourseConfigurationsById(id)), id);
    }

    /**
     * Finds an LTI platform configuration by its client ID.
     *
     * @param clientId The registration ID.
     * @return Optional of LtiPlatformConfiguration.
     */
    Optional<LtiPlatformConfiguration> findByClientId(String clientId);

    /**
     * Retrieves an {@link LtiPlatformConfiguration} by ID with eager-loaded online courses.
     * Intended for internal use with {@link EntityGraph} for optimized fetching.
     *
     * @param platformId The ID of the LtiPlatformConfiguration.
     * @return {@link LtiPlatformConfiguration} with eager-loaded courses, or {@code null} if not found.
     */
    @EntityGraph(type = LOAD, attributePaths = { "onlineCourseConfigurations" })
    LtiPlatformConfiguration findWithEagerOnlineCourseConfigurationsById(long platformId);
}
