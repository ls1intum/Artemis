package de.tum.cit.aet.artemis.lti;

import static de.tum.cit.aet.artemis.core.util.TestUriParamsUtil.assertUriParamsContain;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.crypto.SecretKey;

import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;

import io.jsonwebtoken.Jwts;

/**
 * LTI 1.3 Exercise Launch
 * Note that Step 1. of the OpenID Connect Third Party intiated login flow is handled entirely by spring-security-lti13
 * which does not require additional testing here.
 * Testing all possible cases of Step 3. of the OpenID Connect Third Party initiated login flow is
 * nearly impossible if spring-security-lti13 is not mocked. Because of that, there is not a full integration test
 * provided here.
 * However, Lti13LaunchFilter is responsible to handle this step and is tested extensively.
 * see <a href="https://www.imsglobal.org/spec/lti/v1p3/#lti-message-general-details">LTI message general details</a>
 * see <a href="https://www.imsglobal.org/spec/security/v1p0/#openid_connect_launch_flow">OpenId Connect launch flow</a>
 */
class Lti13LaunchIntegrationTest extends AbstractLtiIntegrationTest {

    private static final SecretKey SIGNING_KEY = Jwts.SIG.HS256.key().build();

    private static final String VALID_ID_TOKEN = Jwts.builder().expiration(Date.from(Instant.now().plusSeconds(60))).issuer("https://example.com").audience().add("client-id").and()
            .id("1234").signWith(SIGNING_KEY).compact();

    private static final String OUTDATED_TOKEN = Jwts.builder().expiration(Date.from(Instant.now().minusSeconds(60))).issuer("https://example.com").audience().add("client-id")
            .and().id("1234").signWith(SIGNING_KEY).compact();

    private static final String VALID_STATE = "validState";

    private static final String TEST_PREFIX = "lti13launchintegrationtest";

    @BeforeEach
    void init() {
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 0, 1);
        var user = userTestRepository.findUserWithGroupsAndAuthoritiesByLogin(TEST_PREFIX + "student1").orElseThrow();
        user.setInternal(false);
        userTestRepository.save(user);
    }

    @Test
    @WithAnonymousUser
    void redirectProxy() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("id_token", VALID_ID_TOKEN);
        body.put("state", VALID_STATE);

        URI header = request.postForm("/api/lti/public/lti13/auth-callback", body, HttpStatus.FOUND);

        validateRedirect(header, VALID_ID_TOKEN);
    }

    @Test
    @WithAnonymousUser
    void redirectProxyNoState() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("id_token", VALID_ID_TOKEN);

        request.postFormWithoutLocation("/api/lti/public/lti13/auth-callback", body, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithAnonymousUser
    void redirectProxyNoToken() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("state", VALID_STATE);

        request.postFormWithoutLocation("/api/lti/public/lti13/auth-callback", body, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithAnonymousUser
    void redirectProxyInvalidToken() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("state", VALID_STATE);
        body.put("id_token", "invalid-token");

        request.postFormWithoutLocation("/api/lti/public/lti13/auth-callback", body, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithAnonymousUser
    void redirectProxyOutdatedToken() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("state", VALID_STATE);
        body.put("id_token", OUTDATED_TOKEN);

        request.postFormWithoutLocation("/api/lti/public/lti13/auth-callback", body, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithAnonymousUser
    void redirectProxyTokenInvalidSignature() throws Exception {
        // We can't validate the signature, hence we ignore it.
        String invalidSignatureToken = VALID_ID_TOKEN.substring(0, VALID_ID_TOKEN.lastIndexOf(".")) + OUTDATED_TOKEN.substring(OUTDATED_TOKEN.lastIndexOf("."));
        Map<String, Object> body = new HashMap<>();
        body.put("state", VALID_STATE);
        body.put("id_token", invalidSignatureToken);

        URI header = request.postForm("/api/lti/public/lti13/auth-callback", body, HttpStatus.FOUND);
        validateRedirect(header, invalidSignatureToken);
    }

    // --- Step 3a (deep-link variant of the redirect proxy) ----------------------------------------------------------
    // PublicLtiResource.lti13LaunchRedirect handles BOTH /auth-callback and /deep-link; the existing redirectProxy*
    // tests only cover /auth-callback, leaving the second mapping untested. The same code path must accept deep-link
    // redirects from platforms that POST the deep-linking response there.

    @Test
    @WithAnonymousUser
    void deepLinkRedirectProxy() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("id_token", VALID_ID_TOKEN);
        body.put("state", VALID_STATE);

        URI header = request.postForm("/api/lti/public/lti13/deep-link", body, HttpStatus.FOUND);

        validateRedirect(header, VALID_ID_TOKEN);
    }

    @Test
    @WithAnonymousUser
    void deepLinkRedirectProxyNoState() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("id_token", VALID_ID_TOKEN);

        request.postFormWithoutLocation("/api/lti/public/lti13/deep-link", body, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithAnonymousUser
    void deepLinkRedirectProxyNoToken() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("state", VALID_STATE);

        request.postFormWithoutLocation("/api/lti/public/lti13/deep-link", body, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithAnonymousUser
    void deepLinkRedirectProxyInvalidToken() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("state", VALID_STATE);
        body.put("id_token", "not-a-jwt");

        request.postFormWithoutLocation("/api/lti/public/lti13/deep-link", body, HttpStatus.BAD_REQUEST);
    }

    // --- Step 3b (auth-login filter wiring) -------------------------------------------------------------------------
    // The /auth-login path routes through Lti13LaunchFilter, which delegates to the upstream OAuth2LoginAuthenticationFilter
    // (and transitively OidcLaunchFlowAuthenticationProvider + NimbusJwtDecoder). We cannot reach the JWT validation
    // branch without seeding a cached OAuth2AuthorizationRequest and a signed JWT whose JWKS is reachable — that requires
    // either refactoring the upstream provider's lazy private JwtDecoder map or standing up a JWKS HTTP fixture. Both are
    // out of scope here.
    //
    // What these tests DO guarantee, however, is that the filter chain still includes Lti13LaunchFilter at the right path
    // for the production-shaped HTTP verbs (GET + POST per OIDC form_post). A future Spring Security upgrade that breaks
    // path matching or filter installation surfaces here as a 404 instead of the expected 500.

    @Test
    @WithMockUser(value = "student1", roles = "USER")
    void oidcFlowFails_noRequestCached() throws Exception {
        String ltiLaunchUri = "/api/lti/public/lti13/auth-login?id_token=some-token&state=some-state";
        request.get(ltiLaunchUri, HttpStatus.INTERNAL_SERVER_ERROR, Object.class);
    }

    @Test
    @WithMockUser(value = "student1", roles = "USER")
    void oidcFlowFails_postRequest_noRequestCached() throws Exception {
        // LTI 1.3 platforms use response_mode=form_post; the auth-login leg arrives as a POST. The filter must accept
        // it and Artemis's error path must still trigger the 500 we expect.
        Map<String, Object> body = new HashMap<>();
        body.put("id_token", "some-token");
        body.put("state", "some-state");

        request.postFormWithoutLocation("/api/lti/public/lti13/auth-login", body, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    @WithAnonymousUser
    void oidcFlowFails_anonymousUser_noRequestCached() throws Exception {
        // The OIDC initiation flow originates from an unauthenticated browser. The filter must still process the
        // request rather than be short-circuited by the auth gate at /api/lti/public/**.
        String ltiLaunchUri = "/api/lti/public/lti13/auth-login?id_token=some-token&state=some-state";
        request.get(ltiLaunchUri, HttpStatus.INTERNAL_SERVER_ERROR, Object.class);
    }

    private void validateRedirect(URI locationHeader, String token) {
        assertThat(locationHeader.getPath()).isEqualTo("/lti/launch");

        List<NameValuePair> params = URLEncodedUtils.parse(locationHeader, StandardCharsets.UTF_8);
        assertUriParamsContain(params, "id_token", token);
        assertUriParamsContain(params, "state", VALID_STATE);
    }

}
