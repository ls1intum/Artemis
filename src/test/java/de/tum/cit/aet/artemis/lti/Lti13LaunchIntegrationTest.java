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

import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;
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
class Lti13LaunchIntegrationTest extends AbstractSpringIntegrationIndependentTest {

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
        var user = userRepository.findUserWithGroupsAndAuthoritiesByLogin(TEST_PREFIX + "student1").orElseThrow();
        user.setInternal(false);
        userRepository.save(user);
    }

    @Test
    @WithAnonymousUser
    void redirectProxy() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("id_token", VALID_ID_TOKEN);
        body.put("state", VALID_STATE);

        URI header = request.postForm("/api/public/lti13/auth-callback", body, HttpStatus.FOUND);

        validateRedirect(header, VALID_ID_TOKEN);
    }

    @Test
    @WithAnonymousUser
    void redirectProxyNoState() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("id_token", VALID_ID_TOKEN);

        request.postFormWithoutLocation("/api/public/lti13/auth-callback", body, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithAnonymousUser
    void redirectProxyNoToken() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("state", VALID_STATE);

        request.postFormWithoutLocation("/api/public/lti13/auth-callback", body, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithAnonymousUser
    void redirectProxyInvalidToken() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("state", VALID_STATE);
        body.put("id_token", "invalid-token");

        request.postFormWithoutLocation("/api/public/lti13/auth-callback", body, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithAnonymousUser
    void redirectProxyOutdatedToken() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("state", VALID_STATE);
        body.put("id_token", OUTDATED_TOKEN);

        request.postFormWithoutLocation("/api/public/lti13/auth-callback", body, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithAnonymousUser
    void redirectProxyTokenInvalidSignature() throws Exception {
        // We can't validate the signature, hence we ignore it.
        String invalidSignatureToken = VALID_ID_TOKEN.substring(0, VALID_ID_TOKEN.lastIndexOf(".")) + OUTDATED_TOKEN.substring(OUTDATED_TOKEN.lastIndexOf("."));
        Map<String, Object> body = new HashMap<>();
        body.put("state", VALID_STATE);
        body.put("id_token", invalidSignatureToken);

        URI header = request.postForm("/api/public/lti13/auth-callback", body, HttpStatus.FOUND);
        validateRedirect(header, invalidSignatureToken);
    }

    @Test
    @WithMockUser(value = "student1", roles = "USER")
    void oidcFlowFails_noRequestCached() throws Exception {
        String ltiLaunchUri = "/api/public/lti13/auth-login?id_token=some-token&state=some-state";
        request.get(ltiLaunchUri, HttpStatus.INTERNAL_SERVER_ERROR, Object.class);
    }

    private void validateRedirect(URI locationHeader, String token) {
        assertThat(locationHeader.getPath()).isEqualTo("/lti/launch");

        List<NameValuePair> params = URLEncodedUtils.parse(locationHeader, StandardCharsets.UTF_8);
        assertUriParamsContain(params, "id_token", token);
        assertUriParamsContain(params, "state", VALID_STATE);
    }

}
