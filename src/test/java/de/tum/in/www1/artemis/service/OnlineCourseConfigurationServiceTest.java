package de.tum.in.www1.artemis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;

import de.tum.in.www1.artemis.config.lti.CustomLti13Configurer;
import de.tum.in.www1.artemis.domain.OnlineCourseConfiguration;
import de.tum.in.www1.artemis.repository.OnlineCourseConfigurationRepository;

class OnlineCourseConfigurationServiceTest {

    @Mock
    private OnlineCourseConfigurationRepository onlineCourseConfigurationRepository;

    private OnlineCourseConfigurationService onlineCourseConfigurationService;

    @Value("${server.url}")
    private String artemisServerUrl;

    @BeforeEach
    void init() {
        onlineCourseConfigurationService = new OnlineCourseConfigurationService(onlineCourseConfigurationRepository);
    }

    @Test
    void getClientRegistrationNullOnlineCourseConfiguration() {

        ClientRegistration clientRegistration = onlineCourseConfigurationService.getClientRegistration(null);

        assertThat(clientRegistration).isNull();
    }

    @Test
    void getClientRegistrationIllegalOnlineCourseConfiguration() {
        OnlineCourseConfiguration onlineCourseConfiguration = new OnlineCourseConfiguration();

        ClientRegistration clientRegistration = onlineCourseConfigurationService.getClientRegistration(onlineCourseConfiguration);

        assertThat(clientRegistration).isNull();
    }

    @Test
    void getClientRegistrationSuccess() {
        OnlineCourseConfiguration onlineCourseConfiguration = new OnlineCourseConfiguration();
        onlineCourseConfiguration.setRegistrationId("reg");
        onlineCourseConfiguration.setClientId("client");
        onlineCourseConfiguration.setAuthorizationUri("auth");
        onlineCourseConfiguration.setTokenUri("token");
        onlineCourseConfiguration.setJwkSetUri("jwk");

        ClientRegistration clientRegistration = onlineCourseConfigurationService.getClientRegistration(onlineCourseConfiguration);

        assertEquals(AuthorizationGrantType.IMPLICIT, clientRegistration.getAuthorizationGrantType());
        assertThat(clientRegistration.getScopes()).hasSize(1).contains("openid");
        assertEquals("reg", clientRegistration.getRegistrationId());
        assertEquals(artemisServerUrl + CustomLti13Configurer.LTI13_LOGIN_REDIRECT_PROXY_PATH, clientRegistration.getRedirectUri());
    }
}
