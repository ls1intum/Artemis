package de.tum.cit.aet.artemis.lti.test_repository;

import java.util.Optional;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.lti.domain.LtiPlatformConfiguration;
import de.tum.cit.aet.artemis.lti.repository.LtiPlatformConfigurationRepository;

@Repository
@Primary
public interface LtiPlatformConfigurationTestRepository extends LtiPlatformConfigurationRepository {

    /**
     * Finds an LTI platform configuration by its client ID.
     *
     * @param clientId The registration ID.
     * @return Optional of LtiPlatformConfiguration.
     */
    Optional<LtiPlatformConfiguration> findByClientId(String clientId);
}
