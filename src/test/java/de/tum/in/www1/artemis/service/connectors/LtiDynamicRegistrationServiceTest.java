package de.tum.in.www1.artemis.service.connectors;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.OnlineCourseConfiguration;
import de.tum.in.www1.artemis.domain.lti.Lti13ClientRegistration;
import de.tum.in.www1.artemis.domain.lti.Lti13PlatformConfiguration;
import de.tum.in.www1.artemis.repository.OnlineCourseConfigurationRepository;
import de.tum.in.www1.artemis.security.OAuth2JWKSService;
import de.tum.in.www1.artemis.service.OnlineCourseConfigurationService;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;

class LtiDynamicRegistrationServiceTest {

    @Mock
    private OAuth2JWKSService oAuth2JWKSService;

    @Mock
    private OnlineCourseConfigurationService onlineCourseConfigurationService;

    @Mock
    private OnlineCourseConfigurationRepository onlineCourseConfigurationRepository;

    @Mock
    private RestTemplate restTemplate;

    private LtiDynamicRegistrationService ltiDynamicRegistrationService;

    private Course course;

    private String openIdConfigurationUrl;

    private String registrationToken;

    private Lti13PlatformConfiguration platformConfiguration;

    private Lti13ClientRegistration clientRegistrationResponse;

    @BeforeEach
    void init() {
        MockitoAnnotations.openMocks(this);
        SecurityContextHolder.clearContext();
        ltiDynamicRegistrationService = new LtiDynamicRegistrationService(onlineCourseConfigurationService, onlineCourseConfigurationRepository, oAuth2JWKSService, restTemplate);
        ReflectionTestUtils.setField(ltiDynamicRegistrationService, "artemisServerUrl", "http://artemis.com");

        course = new Course();
        course.setOnlineCourseConfiguration(new OnlineCourseConfiguration());
        course.setOnlineCourse(true);
        course.setShortName("shortName");
        openIdConfigurationUrl = "url";
        registrationToken = "token";

        platformConfiguration = new Lti13PlatformConfiguration();
        platformConfiguration.setTokenEndpoint("token");
        platformConfiguration.setAuthorizationEndpoint("auth");
        platformConfiguration.setRegistrationEndpoint("register");
        platformConfiguration.setJwksUri("jwks");

        clientRegistrationResponse = new Lti13ClientRegistration();
    }

    @Test
    void badRequestWhenNotOnlineCourse() {

        course.setOnlineCourse(false);

        assertThrows(BadRequestAlertException.class, () -> ltiDynamicRegistrationService.performDynamicRegistration(course, openIdConfigurationUrl, registrationToken));
    }

    @Test
    void badRequestWhenNoOnlineCourseConfiguration() {

        course.setOnlineCourseConfiguration(null);

        assertThrows(BadRequestAlertException.class, () -> ltiDynamicRegistrationService.performDynamicRegistration(course, openIdConfigurationUrl, registrationToken));
    }

    @Test
    void badRequestWhenGetPlatformConfigurationFails() {

        doThrow(HttpClientErrorException.class).when(restTemplate).getForEntity(openIdConfigurationUrl, Lti13PlatformConfiguration.class);

        assertThrows(BadRequestAlertException.class, () -> ltiDynamicRegistrationService.performDynamicRegistration(course, openIdConfigurationUrl, registrationToken));
    }

    @Test
    void badRequestWhenPlatformConfigurationEmpty() {
        Lti13PlatformConfiguration platformConfiguration = new Lti13PlatformConfiguration();
        when(restTemplate.getForEntity(openIdConfigurationUrl, Lti13PlatformConfiguration.class)).thenReturn(ResponseEntity.accepted().body(platformConfiguration));

        assertThrows(BadRequestAlertException.class, () -> ltiDynamicRegistrationService.performDynamicRegistration(course, openIdConfigurationUrl, registrationToken));
    }

    @Test
    void badRequestWhenRegistrationEndpointEmpty() {
        Lti13PlatformConfiguration platformConfiguration = new Lti13PlatformConfiguration();
        platformConfiguration.setAuthorizationEndpoint("auth");
        platformConfiguration.setJwksUri("uri");
        platformConfiguration.setTokenEndpoint("token");

        when(restTemplate.getForEntity(openIdConfigurationUrl, Lti13PlatformConfiguration.class)).thenReturn(ResponseEntity.accepted().body(platformConfiguration));

        assertThrows(BadRequestAlertException.class, () -> ltiDynamicRegistrationService.performDynamicRegistration(course, openIdConfigurationUrl, registrationToken));
    }

    @Test
    void badRequestWhenGetPostClientRegistrationFails() {

        when(restTemplate.getForEntity(eq(openIdConfigurationUrl), any())).thenReturn(ResponseEntity.accepted().body(platformConfiguration));

        doThrow(HttpClientErrorException.class).when(restTemplate).postForEntity(eq(platformConfiguration.getRegistrationEndpoint()), any(), eq(Lti13ClientRegistration.class));

        assertThrows(BadRequestAlertException.class, () -> ltiDynamicRegistrationService.performDynamicRegistration(course, openIdConfigurationUrl, registrationToken));
    }

    @Test
    void performDynamicRegistrationSuccess() {

        when(restTemplate.getForEntity(openIdConfigurationUrl, Lti13PlatformConfiguration.class)).thenReturn(ResponseEntity.accepted().body(platformConfiguration));
        when(restTemplate.postForEntity(eq(platformConfiguration.getRegistrationEndpoint()), any(), eq(Lti13ClientRegistration.class)))
                .thenReturn(ResponseEntity.accepted().body(clientRegistrationResponse));

        ltiDynamicRegistrationService.performDynamicRegistration(course, openIdConfigurationUrl, registrationToken);

        verify(onlineCourseConfigurationRepository).save(any());
        verify(oAuth2JWKSService).updateKey(any());
    }

    @Test
    void performDynamicRegistrationSuccessWithoutToken() {

        when(restTemplate.getForEntity(openIdConfigurationUrl, Lti13PlatformConfiguration.class)).thenReturn(ResponseEntity.accepted().body(platformConfiguration));
        when(restTemplate.postForEntity(eq(platformConfiguration.getRegistrationEndpoint()), any(), eq(Lti13ClientRegistration.class)))
                .thenReturn(ResponseEntity.accepted().body(clientRegistrationResponse));

        ltiDynamicRegistrationService.performDynamicRegistration(course, openIdConfigurationUrl, null);

        verify(onlineCourseConfigurationRepository).save(any());
        verify(oAuth2JWKSService).updateKey(any());
    }
}
