package de.tum.in.www1.artemis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;

import de.tum.in.www1.artemis.config.lti.CustomLti13Configurer;
import de.tum.in.www1.artemis.domain.LtiPlatformConfiguration;
import de.tum.in.www1.artemis.domain.OnlineCourseConfiguration;
import de.tum.in.www1.artemis.repository.LtiPlatformConfigurationRepository;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

class OnlineCourseConfigurationServiceTest {

    @Mock
    private LtiPlatformConfigurationRepository ltiPlatformConfigurationRepository;

    private OnlineCourseConfigurationService onlineCourseConfigurationService;

    @Value("${server.url}")
    private String artemisServerUrl;

    private AutoCloseable closeable;

    @BeforeEach
    void init() {
        closeable = MockitoAnnotations.openMocks(this);
        SecurityContextHolder.clearContext();
        onlineCourseConfigurationService = new OnlineCourseConfigurationService(ltiPlatformConfigurationRepository);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (closeable != null) {
            closeable.close();
        }
        reset(ltiPlatformConfigurationRepository);
    }

    @Test
    void getClientRegistrationNullOnlineCourseConfiguration() {

        ClientRegistration clientRegistration = onlineCourseConfigurationService.getClientRegistration(null);

        assertThat(clientRegistration).isNull();
    }

    @Test
    void getClientRegistrationSuccess() {
        LtiPlatformConfiguration ltiPlatformConfiguration = getMockLtiPlatformConfiguration();

        ClientRegistration clientRegistration = onlineCourseConfigurationService.getClientRegistration(ltiPlatformConfiguration);

        assertThat(clientRegistration.getAuthorizationGrantType()).isEqualTo(AuthorizationGrantType.AUTHORIZATION_CODE);
        assertThat(clientRegistration.getScopes()).hasSize(1).contains("openid");
        assertThat(clientRegistration.getRegistrationId()).isEqualTo("reg");
        assertThat(clientRegistration.getRedirectUri()).isEqualTo(artemisServerUrl + CustomLti13Configurer.LTI13_LOGIN_REDIRECT_PROXY_PATH);
    }

    @Test
    void addOnlineCourseConfigurationToLtiConfigurationsSuccess() {
        LtiPlatformConfiguration ltiPlatformConfiguration = getMockLtiPlatformConfiguration();
        when(ltiPlatformConfigurationRepository.findLtiPlatformConfigurationWithEagerLoadedCoursesByIdElseThrow(ltiPlatformConfiguration.getId()))
                .thenReturn(ltiPlatformConfiguration);
        OnlineCourseConfiguration onlineCourseConfiguration = getMockOnlineCourseConfiguration(ltiPlatformConfiguration);

        onlineCourseConfigurationService.addOnlineCourseConfigurationToLtiConfigurations(onlineCourseConfiguration);

        verify(ltiPlatformConfigurationRepository).findLtiPlatformConfigurationWithEagerLoadedCoursesByIdElseThrow(ltiPlatformConfiguration.getId());
        assertThat(ltiPlatformConfiguration.getOnlineCourseConfigurations()).contains(onlineCourseConfiguration);
        assertThat(onlineCourseConfiguration.getLtiPlatformConfiguration()).isEqualTo(ltiPlatformConfiguration);
    }

    @Test
    void addOnlineCourseConfigurationToLtiConfigurationsThrowsEntityNotFound() {
        LtiPlatformConfiguration ltiPlatformConfiguration = getMockLtiPlatformConfiguration();
        when(ltiPlatformConfigurationRepository.findLtiPlatformConfigurationWithEagerLoadedCoursesByIdElseThrow(ltiPlatformConfiguration.getId()))
                .thenThrow(new EntityNotFoundException("LtiPlatformConfiguration", ltiPlatformConfiguration.getId()));
        OnlineCourseConfiguration onlineCourseConfiguration = getMockOnlineCourseConfiguration(ltiPlatformConfiguration);

        assertThatThrownBy(() -> onlineCourseConfigurationService.addOnlineCourseConfigurationToLtiConfigurations(onlineCourseConfiguration))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void noLtiConfigurationValidateOnlineCourseConfiguration() {
        LtiPlatformConfiguration ltiPlatformConfiguration = getMockLtiPlatformConfiguration();
        OnlineCourseConfiguration onlineCourseConfiguration = getMockOnlineCourseConfiguration(ltiPlatformConfiguration);
        when(ltiPlatformConfigurationRepository.findByRegistrationId(onlineCourseConfiguration.getLtiPlatformConfiguration().getRegistrationId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> onlineCourseConfigurationService.validateOnlineCourseConfiguration(onlineCourseConfiguration)).isInstanceOf(BadRequestAlertException.class);
    }

    @Test
    void invalidRegistrationIdValidateOnlineCourseConfiguration() {
        LtiPlatformConfiguration ltiPlatformConfiguration = getMockLtiPlatformConfiguration();
        ltiPlatformConfiguration.setId(2L);
        OnlineCourseConfiguration onlineCourseConfiguration = getMockOnlineCourseConfiguration(getMockLtiPlatformConfiguration());

        when(ltiPlatformConfigurationRepository.findByRegistrationId(onlineCourseConfiguration.getLtiPlatformConfiguration().getRegistrationId()))
                .thenReturn(Optional.of(ltiPlatformConfiguration));

        assertThatThrownBy(() -> onlineCourseConfigurationService.validateOnlineCourseConfiguration(onlineCourseConfiguration)).isInstanceOf(BadRequestAlertException.class);
    }

    private LtiPlatformConfiguration getMockLtiPlatformConfiguration() {
        LtiPlatformConfiguration ltiPlatformConfiguration = new LtiPlatformConfiguration();
        ltiPlatformConfiguration.setId(1L);
        ltiPlatformConfiguration.setRegistrationId("reg");
        ltiPlatformConfiguration.setClientId("client");
        ltiPlatformConfiguration.setAuthorizationUri("auth");
        ltiPlatformConfiguration.setTokenUri("token");
        ltiPlatformConfiguration.setJwkSetUri("jwk");
        return ltiPlatformConfiguration;
    }

    private OnlineCourseConfiguration getMockOnlineCourseConfiguration(LtiPlatformConfiguration ltiPlatformConfiguration) {
        OnlineCourseConfiguration onlineCourseConfiguration = new OnlineCourseConfiguration();
        onlineCourseConfiguration.setCourse(null);
        onlineCourseConfiguration.setUserPrefix("ltiCourse");
        onlineCourseConfiguration.setLtiPlatformConfiguration(ltiPlatformConfiguration);
        return onlineCourseConfiguration;
    }
}
