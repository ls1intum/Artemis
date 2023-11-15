package de.tum.in.www1.artemis.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;

import de.tum.in.www1.artemis.config.lti.CustomLti13Configurer;
import de.tum.in.www1.artemis.domain.LtiPlatformConfiguration;
import de.tum.in.www1.artemis.domain.OnlineCourseConfiguration;
import de.tum.in.www1.artemis.repository.LtiPlatformConfigurationRepository;
import de.tum.in.www1.artemis.repository.OnlineCourseConfigurationRepository;

class OnlineCourseConfigurationServiceTest {

    @Mock
    private OnlineCourseConfigurationRepository onlineCourseConfigurationRepository;

    @Mock
    private LtiPlatformConfigurationRepository ltiPlatformConfigurationRepository;

    private OnlineCourseConfigurationService onlineCourseConfigurationService;

    @Value("${server.url}")
    private String artemisServerUrl;

    @BeforeEach
    void init() {
        onlineCourseConfigurationService = new OnlineCourseConfigurationService(onlineCourseConfigurationRepository, ltiPlatformConfigurationRepository);
    }

    @Test
    void getClientRegistrationNullOnlineCourseConfiguration() {

        ClientRegistration clientRegistration = onlineCourseConfigurationService.getClientRegistration(null);

        assertThat(clientRegistration).isNull();
    }

    @Test
    void getClientRegistrationIllegalOnlineCourseConfiguration() {
        OnlineCourseConfiguration onlineCourseConfiguration = new OnlineCourseConfiguration();

        ClientRegistration clientRegistration = onlineCourseConfigurationService.getClientRegistration(onlineCourseConfiguration.getLtiPlatformConfiguration());

        assertThat(clientRegistration).isNull();
    }

    @Test
    void getClientRegistrationSuccess() {
        OnlineCourseConfiguration onlineCourseConfiguration = new OnlineCourseConfiguration();
        LtiPlatformConfiguration ltiPlatformConfiguration = new LtiPlatformConfiguration();
        ltiPlatformConfiguration.setId(1L);
        ltiPlatformConfiguration.setRegistrationId("reg");
        ltiPlatformConfiguration.setClientId("client");
        ltiPlatformConfiguration.setAuthorizationUri("auth");
        ltiPlatformConfiguration.setTokenUri("token");
        ltiPlatformConfiguration.setJwkSetUri("jwk");
        onlineCourseConfiguration.setLtiPlatformConfiguration(ltiPlatformConfiguration);

        ClientRegistration clientRegistration = onlineCourseConfigurationService.getClientRegistration(onlineCourseConfiguration.getLtiPlatformConfiguration());

        assertThat(clientRegistration.getAuthorizationGrantType()).isEqualTo(AuthorizationGrantType.IMPLICIT);
        assertThat(clientRegistration.getScopes()).hasSize(1).contains("openid");
        assertThat(clientRegistration.getRegistrationId()).isEqualTo("reg");
        assertThat(clientRegistration.getRedirectUri()).isEqualTo(artemisServerUrl + CustomLti13Configurer.LTI13_LOGIN_REDIRECT_PROXY_PATH);
    }
}
