package de.tum.cit.aet.artemis.lti;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import tools.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.service.ProfileService;
import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.lti.dto.Lti13ClientRegistration;
import de.tum.cit.aet.artemis.lti.dto.Lti13ClientRegistrationFactory;
import de.tum.cit.aet.artemis.lti.dto.Lti13PlatformConfiguration;
import de.tum.cit.aet.artemis.lti.service.LtiDynamicRegistrationService;
import de.tum.cit.aet.artemis.lti.service.OAuth2JWKSService;
import de.tum.cit.aet.artemis.lti.test_repository.LtiPlatformConfigurationTestRepository;

class LtiDynamicRegistrationServiceTest {

    @Mock
    private OAuth2JWKSService oAuth2JWKSService;

    @Mock
    private LtiPlatformConfigurationTestRepository ltiPlatformConfigurationRepository;

    @Mock
    private ProfileService profileService;

    private final JsonMapper jsonMapper = JsonObjectMapper.get();

    private MockRestServiceServer server;

    private LtiDynamicRegistrationService ltiDynamicRegistrationService;

    private String openIdConfigurationUrl;

    private String registrationToken;

    private Lti13PlatformConfiguration platformConfiguration;

    private Lti13ClientRegistration clientRegistrationResponse;

    private AutoCloseable closeable;

    @BeforeEach
    void init() {
        closeable = MockitoAnnotations.openMocks(this);
        SecurityContextHolder.clearContext();
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        ltiDynamicRegistrationService = new LtiDynamicRegistrationService(ltiPlatformConfigurationRepository, oAuth2JWKSService, builder.build(), profileService);
        ReflectionTestUtils.setField(ltiDynamicRegistrationService, "artemisServerUrl", "http://artemis.com");

        Course course = new Course();
        course.setOnlineCourse(true);
        course.setShortName("shortName");
        openIdConfigurationUrl = "https://example.com/.well-known/openid-configuration";
        registrationToken = "token";

        platformConfiguration = new Lti13PlatformConfiguration(null, "https://example.com/token", "https://example.com/auth", "https://example.com/jwks",
                "https://example.com/register");
        clientRegistrationResponse = Lti13ClientRegistrationFactory.createRegistration("http://artemis.com", "artemis-" + UUID.randomUUID());
    }

    @AfterEach
    void tearDown() throws Exception {
        if (closeable != null) {
            closeable.close();
        }
        reset(oAuth2JWKSService, ltiPlatformConfigurationRepository, profileService);
    }

    private void expectPlatformConfiguration(String url, Lti13PlatformConfiguration configuration) {
        server.expect(requestTo(url)).andExpect(method(HttpMethod.GET)).andRespond(withSuccess(jsonMapper.writeValueAsString(configuration), MediaType.APPLICATION_JSON));
    }

    private void expectClientRegistration(String endpoint, String expectedAuthorization) {
        var expectation = server.expect(requestTo(endpoint)).andExpect(method(HttpMethod.POST)).andExpect(header("Content-Type", MediaType.APPLICATION_JSON_VALUE));
        if (expectedAuthorization != null) {
            expectation.andExpect(header("Authorization", expectedAuthorization));
        }
        else {
            expectation.andExpect(headerDoesNotExist("Authorization"));
        }
        expectation.andRespond(withSuccess(jsonMapper.writeValueAsString(clientRegistrationResponse), MediaType.APPLICATION_JSON));
    }

    @Test
    void badRequestWhenGetPlatformConfigurationFails() {
        server.expect(requestTo(openIdConfigurationUrl)).andRespond(withBadRequest());

        assertThatExceptionOfType(BadRequestAlertException.class)
                .isThrownBy(() -> ltiDynamicRegistrationService.performDynamicRegistration(openIdConfigurationUrl, registrationToken));
        server.verify();
    }

    @Test
    void badRequestWhenPlatformConfigurationEmpty() {
        Lti13PlatformConfiguration platformConfiguration = new Lti13PlatformConfiguration(null, null, null, null, null);
        expectPlatformConfiguration(openIdConfigurationUrl, platformConfiguration);

        assertThatExceptionOfType(BadRequestAlertException.class)
                .isThrownBy(() -> ltiDynamicRegistrationService.performDynamicRegistration(openIdConfigurationUrl, registrationToken));
    }

    @Test
    void badRequestWhenRegistrationEndpointEmpty() {
        Lti13PlatformConfiguration platformConfiguration = new Lti13PlatformConfiguration(null, "https://example.com/token", "https://example.com/auth", "https://example.com/jwks",
                null);
        expectPlatformConfiguration(openIdConfigurationUrl, platformConfiguration);

        assertThatExceptionOfType(BadRequestAlertException.class)
                .isThrownBy(() -> ltiDynamicRegistrationService.performDynamicRegistration(openIdConfigurationUrl, registrationToken));
    }

    @Test
    void badRequestWhenGetPostClientRegistrationFails() {
        expectPlatformConfiguration(openIdConfigurationUrl, platformConfiguration);
        server.expect(requestTo(platformConfiguration.registrationEndpoint())).andRespond(withBadRequest());

        assertThatExceptionOfType(BadRequestAlertException.class)
                .isThrownBy(() -> ltiDynamicRegistrationService.performDynamicRegistration(openIdConfigurationUrl, registrationToken));
        server.verify();
    }

    @Test
    void performDynamicRegistrationSuccess() {
        expectPlatformConfiguration(openIdConfigurationUrl, platformConfiguration);
        expectClientRegistration(platformConfiguration.registrationEndpoint(), "Bearer " + registrationToken);

        ltiDynamicRegistrationService.performDynamicRegistration(openIdConfigurationUrl, registrationToken);

        server.verify();
        verify(ltiPlatformConfigurationRepository).save(any());
        verify(oAuth2JWKSService).updateKey(any());
    }

    @Test
    void performDynamicRegistrationSuccessWithoutToken() {
        expectPlatformConfiguration(openIdConfigurationUrl, platformConfiguration);
        expectClientRegistration(platformConfiguration.registrationEndpoint(), null);

        ltiDynamicRegistrationService.performDynamicRegistration(openIdConfigurationUrl, null);

        server.verify();
        verify(ltiPlatformConfigurationRepository).save(any());
        verify(oAuth2JWKSService).updateKey(any());
    }

    @Test
    void performDynamicRegistrationAllowsLocalhostHttpInDevProfile() {
        when(profileService.isDevActive()).thenReturn(true);
        String localhostOpenIdConfigurationUrl = "http://localhost:8086/.well-known/openid-configuration";
        Lti13PlatformConfiguration localhostPlatformConfiguration = new Lti13PlatformConfiguration(null, "http://localhost:8086/token", "http://localhost:8086/auth",
                "http://localhost:8086/jwks", "http://localhost:8086/register");

        expectPlatformConfiguration(localhostOpenIdConfigurationUrl, localhostPlatformConfiguration);
        expectClientRegistration(localhostPlatformConfiguration.registrationEndpoint(), "Bearer " + registrationToken);

        ltiDynamicRegistrationService.performDynamicRegistration(localhostOpenIdConfigurationUrl, registrationToken);

        server.verify();
        verify(ltiPlatformConfigurationRepository).save(any());
        verify(oAuth2JWKSService).updateKey(any());
    }

    @Test
    void badRequestWhenLocalhostUrlOutsideDevProfile() {
        assertThatExceptionOfType(BadRequestAlertException.class)
                .isThrownBy(() -> ltiDynamicRegistrationService.performDynamicRegistration("http://localhost:8086/.well-known/openid-configuration", registrationToken))
                .satisfies(ex -> assertThat(ex.getErrorKey()).isEqualTo("invalidUrl"));
    }

    @Test
    void badRequestWhenLocalhostHttpsUrlOutsideDevProfile() {
        assertThatExceptionOfType(BadRequestAlertException.class)
                .isThrownBy(() -> ltiDynamicRegistrationService.performDynamicRegistration("https://localhost/.well-known/openid-configuration", registrationToken))
                .satisfies(ex -> assertThat(ex.getErrorKey()).isEqualTo("invalidUrl"));
    }

    @Test
    void badRequestWhenUrlIsNotHttps() {
        assertThatExceptionOfType(BadRequestAlertException.class)
                .isThrownBy(() -> ltiDynamicRegistrationService.performDynamicRegistration("http://example.com/config", registrationToken))
                .satisfies(ex -> assertThat(ex.getErrorKey()).isEqualTo("invalidUrl"));
    }

    @Test
    void badRequestWhenLocalhostUrlUsesUnsupportedSchemeInDevProfile() {
        when(profileService.isDevActive()).thenReturn(true);
        assertThatExceptionOfType(BadRequestAlertException.class)
                .isThrownBy(() -> ltiDynamicRegistrationService.performDynamicRegistration("ftp://localhost/config", registrationToken))
                .satisfies(ex -> assertThat(ex.getErrorKey()).isEqualTo("invalidUrl"));
    }

    @Test
    void badRequestWhenUrlPointsToLoopback() {
        assertThatExceptionOfType(BadRequestAlertException.class)
                .isThrownBy(() -> ltiDynamicRegistrationService.performDynamicRegistration("https://127.0.0.1/config", registrationToken))
                .satisfies(ex -> assertThat(ex.getErrorKey()).isEqualTo("invalidUrl"));
    }

    @Test
    void badRequestWhenUrlPointsToPrivateNetwork() {
        assertThatExceptionOfType(BadRequestAlertException.class)
                .isThrownBy(() -> ltiDynamicRegistrationService.performDynamicRegistration("https://192.168.1.1/config", registrationToken))
                .satisfies(ex -> assertThat(ex.getErrorKey()).isEqualTo("invalidUrl"));
    }

    @Test
    void badRequestWhenUrlPointsToIPv6Loopback() {
        assertThatExceptionOfType(BadRequestAlertException.class)
                .isThrownBy(() -> ltiDynamicRegistrationService.performDynamicRegistration("https://[::1]/config", registrationToken))
                .satisfies(ex -> assertThat(ex.getErrorKey()).isEqualTo("invalidUrl"));
    }

    @Test
    void badRequestWhenUrlPointsToSharedAddressSpace() {
        assertThatExceptionOfType(BadRequestAlertException.class)
                .isThrownBy(() -> ltiDynamicRegistrationService.performDynamicRegistration("https://100.64.0.1/config", registrationToken))
                .satisfies(ex -> assertThat(ex.getErrorKey()).isEqualTo("invalidUrl"));
    }
}
