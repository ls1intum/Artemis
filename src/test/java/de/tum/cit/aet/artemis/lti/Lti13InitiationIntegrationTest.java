package de.tum.cit.aet.artemis.lti;

import static de.tum.cit.aet.artemis.core.util.TestUriParamsUtil.assertUriParamsContain;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.junit.jupiter.api.Test;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.test.web.servlet.MvcResult;

import de.tum.cit.aet.artemis.lti.domain.LtiPlatformConfiguration;

/**
 * Server integration tests for Step 1 of the LTI 1.3 OpenID Connect third-party initiated login flow.
 * <p>
 * Covers the initiation endpoint that {@code spring-security-lti13} mounts at
 * {@code /api/lti/public/lti13/initiate-login/{registrationId}}. A platform such as Moodle sends the
 * student's browser to this URL, and Artemis must respond with a 302 redirect back to the platform's
 * {@code authorization_uri} carrying the OIDC parameters. This is exactly the request flow that broke
 * after the Spring Boot 4 upgrade (#12381): the library calls {@code UriComponentsBuilder.fromHttpUrl}
 * inside {@link uk.ac.ox.ctl.lti13.security.oauth2.client.lti.web.OIDCInitiatingLoginRequestResolver},
 * a method removed in Spring Framework 7. The bug surfaced as issue #12739 ("LTI/Moodle Integration broken").
 * <p>
 * Without this test we have no regression coverage of Step 1; the existing {@code Lti13LaunchIntegrationTest}
 * only exercises Step 3 (auth-callback). Any future change that breaks the initiation path silently must
 * surface here.
 */
class Lti13InitiationIntegrationTest extends AbstractLtiIntegrationTest {

    private static final String AUTH_URI = "https://platform.example.com/mod/lti/auth.php";

    private static final String CLIENT_ID = "artemis-test-client";

    @Test
    @WithAnonymousUser
    void initiateLoginRedirectsToPlatformAuthorizationUri() throws Exception {
        String registrationId = "test-platform-" + UUID.randomUUID();
        savePlatform(registrationId);

        MvcResult result = request
                .performMvcRequest(get("/api/lti/public/lti13/initiate-login/{registrationId}", registrationId).param("iss", "https://platform.example.com")
                        .param("login_hint", "user-42").param("target_link_uri", "http://localhost/courses/1"))
                .andExpect(status().isFound()).andExpect(header().string("Location", org.hamcrest.Matchers.startsWith(AUTH_URI))).andReturn();

        URI location = URI.create(result.getResponse().getHeader("Location"));
        List<NameValuePair> params = URLEncodedUtils.parse(location, StandardCharsets.UTF_8);
        assertUriParamsContain(params, "client_id", CLIENT_ID);
        assertUriParamsContain(params, "login_hint", "user-42");
        assertUriParamsContain(params, "response_type", "id_token");
        assertUriParamsContain(params, "scope", "openid");
        assertUriParamsContain(params, "response_mode", "form_post");
        assertUriParamsContain(params, "prompt", "none");

        NameValuePair redirectUri = params.stream().filter(p -> "redirect_uri".equals(p.getName())).findFirst().orElseThrow();
        assertThat(redirectUri.getValue()).endsWith("/api/lti/public/lti13/auth-callback");
    }

    @Test
    @WithAnonymousUser
    void initiateLoginWithUnknownRegistrationReturnsNotFound() throws Exception {
        request.performMvcRequest(get("/api/lti/public/lti13/initiate-login/{registrationId}", "no-such-registration").param("iss", "https://platform.example.com")
                .param("login_hint", "user-42").param("target_link_uri", "http://localhost/courses/1")).andExpect(status().isNotFound());
    }

    @Test
    @WithAnonymousUser
    void initiateLoginWithMissingIssReturnsBadRequest() throws Exception {
        String registrationId = "test-platform-" + UUID.randomUUID();
        savePlatform(registrationId);

        request.performMvcRequest(
                get("/api/lti/public/lti13/initiate-login/{registrationId}", registrationId).param("login_hint", "user-42").param("target_link_uri", "http://localhost/courses/1"))
                .andExpect(status().isBadRequest());
    }

    private void savePlatform(String registrationId) {
        LtiPlatformConfiguration platform = new LtiPlatformConfiguration();
        platform.setRegistrationId(registrationId);
        platform.setClientId(CLIENT_ID);
        platform.setAuthorizationUri(AUTH_URI);
        platform.setTokenUri("https://platform.example.com/mod/lti/token.php");
        platform.setJwkSetUri("https://platform.example.com/mod/lti/certs.php");
        ltiPlatformConfigurationRepository.save(platform);
    }
}
