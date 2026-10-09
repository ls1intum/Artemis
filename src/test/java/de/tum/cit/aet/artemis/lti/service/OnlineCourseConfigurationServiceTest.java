package de.tum.cit.aet.artemis.lti.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.reset;
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

import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.lti.domain.LtiPlatformConfiguration;
import de.tum.cit.aet.artemis.lti.domain.OnlineCourseConfiguration;
import de.tum.cit.aet.artemis.lti.test_repository.LtiPlatformConfigurationTestRepository;
import de.tum.cit.aet.artemis.lti.test_repository.OnlineCourseConfigurationTestRepository;
import uk.ac.ox.ctl.lti13.security.oauth2.client.lti.web.LTIAuthorizationGrantType;

class OnlineCourseConfigurationServiceTest {

    @Mock
    private LtiPlatformConfigurationTestRepository ltiPlatformConfigurationRepository;

    @Mock
    private OnlineCourseConfigurationTestRepository onlineCourseConfigurationRepository;

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

        assertThat(clientRegistration.getAuthorizationGrantType()).isEqualTo(LTIAuthorizationGrantType.IMPLICIT);
        assertThat(clientRegistration.getScopes()).hasSize(1).contains("openid");
        assertThat(clientRegistration.getRegistrationId()).isEqualTo("reg");
        assertThat(clientRegistration.getRedirectUri()).isEqualTo(artemisServerUrl + "/api/lti/public/lti13/auth-callback");
    }

    @Test
    void noLtiConfigurationValidateOnlineCourseConfiguration() {
        LtiPlatformConfiguration ltiPlatformConfiguration = getMockLtiPlatformConfiguration();
        OnlineCourseConfiguration onlineCourseConfiguration = getMockOnlineCourseConfiguration(ltiPlatformConfiguration);
        when(ltiPlatformConfigurationRepository.findById(onlineCourseConfiguration.getLtiPlatformConfiguration().getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> onlineCourseConfigurationService.validateOnlineCourseConfiguration(onlineCourseConfiguration)).isInstanceOf(BadRequestAlertException.class);
    }

    @Test
    void nullPlatformIdValidateOnlineCourseConfiguration() {
        LtiPlatformConfiguration ltiPlatformConfiguration = getMockLtiPlatformConfiguration();
        ltiPlatformConfiguration.setId(null);
        OnlineCourseConfiguration onlineCourseConfiguration = getMockOnlineCourseConfiguration(ltiPlatformConfiguration);

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
