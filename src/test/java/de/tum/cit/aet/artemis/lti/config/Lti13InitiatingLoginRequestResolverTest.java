package de.tum.cit.aet.artemis.lti.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

import uk.ac.ox.ctl.lti13.security.oauth2.client.lti.web.InvalidInitiationRequestException;
import uk.ac.ox.ctl.lti13.security.oauth2.client.lti.web.LTIAuthorizationGrantType;
import uk.ac.ox.ctl.lti13.security.oauth2.client.lti.web.Lti13InitiatingLoginRequestResolver;

/**
 * Pure unit tests for {@link Lti13InitiatingLoginRequestResolver}, the Spring 7-compatible
 * replacement for the upstream {@code OIDCInitiatingLoginRequestResolver} that called the removed
 * {@code UriComponentsBuilder.fromHttpUrl(String)} (issue #12739).
 * <p>
 * These tests do not need a Spring context or Testcontainers and run in a few milliseconds. They are
 * specifically designed to catch the regression class: any future change that reintroduces the
 * {@code fromHttpUrl} call (or any other Spring 7-removed API on the Step 1 path) will fail here
 * with {@link NoSuchMethodError}.
 */
class Lti13InitiatingLoginRequestResolverTest {

    private static final String REGISTRATION_ID = "test-registration";

    private static final String CLIENT_ID = "test-client";

    private static final String AUTH_URI = "https://platform.example.com/mod/lti/auth.php";

    private static final String ARTEMIS_REDIRECT_URI = "http://localhost/api/lti/public/lti13/auth-callback";

    private static final String INITIATION_BASE_URI = "/api/lti/public/lti13/initiate-login";

    @Mock
    private ClientRegistrationRepository clientRegistrationRepository;

    private Lti13InitiatingLoginRequestResolver resolver;

    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        resolver = new Lti13InitiatingLoginRequestResolver(clientRegistrationRepository, new Lti13PathRegistrationResolver(INITIATION_BASE_URI));
        when(clientRegistrationRepository.findByRegistrationId(eq(REGISTRATION_ID))).thenReturn(clientRegistration());
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    void resolveBuildsAuthorizationRequestForValidInitiation() {
        MockHttpServletRequest request = initiationRequest(REGISTRATION_ID);

        OAuth2AuthorizationRequest result = resolver.resolve(request);

        assertThat(result).isNotNull();
        assertThat(result.getAuthorizationUri()).isEqualTo(AUTH_URI);
        assertThat(result.getClientId()).isEqualTo(CLIENT_ID);
        assertThat(result.getRedirectUri()).isEqualTo(ARTEMIS_REDIRECT_URI);
        assertThat(result.getScopes()).containsExactly("openid");
        assertThat(result.getAdditionalParameters()).containsEntry("response_type", "id_token").containsEntry("login_hint", "user-42").containsEntry("response_mode", "form_post")
                .containsEntry("prompt", "none").containsKey("nonce");
        assertThat(result.getState()).isNotBlank();
    }

    /**
     * Direct regression test for the bug behind issue #12739. The upstream resolver calls
     * {@code UriComponentsBuilder.fromHttpUrl(String)}, which Spring Framework 7 removed and the JVM
     * surfaces as {@link NoSuchMethodError}. This test exercises the shim's {@code expandRedirectUri}
     * code path directly and catches a re-introduction of {@code fromHttpUrl} inside this class —
     * either by accident during a future merge or by an attempted "verbatim re-sync" with upstream
     * 0.3.4 — at unit-test speed. The complementary regression guard for a wiring-level revert
     * (i.e. {@code CustomLti13Configurer} swapping back to the upstream class) lives in
     * {@code Lti13InitiationIntegrationTest}, which exercises the full Spring Security filter chain.
     */
    @Test
    void resolveDoesNotThrowNoSuchMethodErrorFromRemovedSpringApi() {
        MockHttpServletRequest request = initiationRequest(REGISTRATION_ID);

        // Explicitly assert no Error escapes resolve() — covers Errors as well as Exceptions.
        OAuth2AuthorizationRequest result = resolver.resolve(request);
        assertThat(result).isNotNull();
    }

    @Test
    void resolveReturnsNullWhenPathDoesNotMatchInitiationBase() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/lti/public/lti13/somewhere-else");
        request.setScheme("http");
        request.setServerName("localhost");
        request.setServerPort(80);

        OAuth2AuthorizationRequest result = resolver.resolve(request);

        assertThat(result).isNull();
    }

    @Test
    void resolveThrowsWhenIssParameterMissing() {
        MockHttpServletRequest request = baseInitiationRequest(REGISTRATION_ID);
        request.setParameter("login_hint", "user-42");
        request.setParameter("target_link_uri", "http://localhost/courses/1");

        assertThatThrownBy(() -> resolver.resolve(request)).isInstanceOf(InvalidInitiationRequestException.class).hasMessageContaining("iss");
    }

    /**
     * Artemis-specific tightening on top of upstream (see class header in the resolver): blank / whitespace values
     * for required initiation parameters must be rejected, not just nulls.
     */
    @Test
    void resolveThrowsWhenIssParameterBlank() {
        MockHttpServletRequest request = baseInitiationRequest(REGISTRATION_ID);
        request.setParameter("iss", "   ");
        request.setParameter("login_hint", "user-42");
        request.setParameter("target_link_uri", "http://localhost/courses/1");

        assertThatThrownBy(() -> resolver.resolve(request)).isInstanceOf(InvalidInitiationRequestException.class).hasMessageContaining("iss");
    }

    @Test
    void resolveThrowsWhenLoginHintBlank() {
        MockHttpServletRequest request = baseInitiationRequest(REGISTRATION_ID);
        request.setParameter("iss", "https://platform.example.com");
        request.setParameter("login_hint", "");
        request.setParameter("target_link_uri", "http://localhost/courses/1");

        assertThatThrownBy(() -> resolver.resolve(request)).isInstanceOf(InvalidInitiationRequestException.class).hasMessageContaining("login_hint");
    }

    @Test
    void resolveThrowsWhenTargetLinkUriBlank() {
        MockHttpServletRequest request = baseInitiationRequest(REGISTRATION_ID);
        request.setParameter("iss", "https://platform.example.com");
        request.setParameter("login_hint", "user-42");
        request.setParameter("target_link_uri", "\t");

        assertThatThrownBy(() -> resolver.resolve(request)).isInstanceOf(InvalidInitiationRequestException.class).hasMessageContaining("target_link_uri");
    }

    private MockHttpServletRequest initiationRequest(String registrationId) {
        MockHttpServletRequest request = baseInitiationRequest(registrationId);
        request.setParameter("iss", "https://platform.example.com");
        request.setParameter("login_hint", "user-42");
        request.setParameter("target_link_uri", "http://localhost/courses/1");
        return request;
    }

    private MockHttpServletRequest baseInitiationRequest(String registrationId) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", INITIATION_BASE_URI + "/" + registrationId);
        request.setScheme("http");
        request.setServerName("localhost");
        request.setServerPort(80);
        return request;
    }

    private ClientRegistration clientRegistration() {
        return ClientRegistration.withRegistrationId(REGISTRATION_ID).clientId(CLIENT_ID).authorizationUri(AUTH_URI).tokenUri("https://platform.example.com/mod/lti/token.php")
                .jwkSetUri("https://platform.example.com/mod/lti/certs.php").redirectUri(ARTEMIS_REDIRECT_URI).scope("openid")
                .authorizationGrantType(LTIAuthorizationGrantType.IMPLICIT).build();
    }
}
