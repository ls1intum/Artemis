package de.tum.in.www1.artemis.service;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.config.lti.CustomLti13Configurer;
import de.tum.in.www1.artemis.domain.OnlineCourseConfiguration;
import de.tum.in.www1.artemis.repository.OnlineCourseConfigurationRepository;

/**
 * Service Implementation for OnlineCourseConfiguration.
 */
@Service
public class OnlineCourseConfigurationService implements ClientRegistrationRepository {

    private final OnlineCourseConfigurationRepository onlineCourseConfigurationRepository;

    @Value("${server.url}")
    private String artemisServerUrl;

    public OnlineCourseConfigurationService(OnlineCourseConfigurationRepository onlineCourseConfigurationRepository) {
        this.onlineCourseConfigurationRepository = onlineCourseConfigurationRepository;
    }

    public List<ClientRegistration> getAllClientRegistrations() {
        return onlineCourseConfigurationRepository.findAll().stream().map(this::getClientRegistration).toList();
    }

    @Override
    public ClientRegistration findByRegistrationId(String registrationId) {
        Optional<OnlineCourseConfiguration> onlineCourseConfiguration = onlineCourseConfigurationRepository.findByRegistrationId(registrationId);
        return onlineCourseConfiguration.map(this::getClientRegistration).orElse(null);
    }

    public ClientRegistration getClientRegistration(OnlineCourseConfiguration onlineCourseConfiguration) {
        return ClientRegistration.withRegistrationId(onlineCourseConfiguration.getRegistrationId()) // formatting
                .clientId(onlineCourseConfiguration.getClientId()) //
                .authorizationUri(onlineCourseConfiguration.getAuthorizationUri()) //
                .jwkSetUri(onlineCourseConfiguration.getJwkSetUri()) //
                .tokenUri(onlineCourseConfiguration.getTokenUri()) //
                .redirectUri(artemisServerUrl + CustomLti13Configurer.LTI13_LOGIN_REDIRECT_PROXY_PATH) //
                .scope("openid") //
                .authorizationGrantType(AuthorizationGrantType.IMPLICIT) //
                .build();
    }
}
