package de.tum.cit.aet.artemis.lti.repository;

import java.util.Optional;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.lti.config.LtiEnabled;
import de.tum.cit.aet.artemis.lti.domain.LtiPlatformConfiguration;

/**
 * Repository for managing LtiPlatformConfiguration entities.
 */
@Conditional(LtiEnabled.class)
@Lazy
@Repository
public interface LtiPlatformConfigurationRepository extends ArtemisJpaRepository<LtiPlatformConfiguration, Long> {

    /**
     * Finds an LTI platform configuration by its registration ID.
     *
     * @param registrationId The registration ID.
     * @return Optional of LtiPlatformConfiguration.
     */
    Optional<LtiPlatformConfiguration> findByRegistrationId(String registrationId);

}
