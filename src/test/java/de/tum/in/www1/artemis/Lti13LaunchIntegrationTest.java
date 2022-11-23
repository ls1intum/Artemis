package de.tum.in.www1.artemis;

import static de.tum.in.www1.artemis.util.TestUriParamsUtil.assertUriParamsContain;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.crypto.spec.SecretKeySpec;

import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.config.lti.CustomLti13Configurer;
import de.tum.in.www1.artemis.web.rest.LtiResource;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

/**
 * LTI 1.3 Exercise Launch
 * Note that Step 1. of the OpenID Connect Third Party intiated login flow is handled entirely by spring-security-lti13
 * which does not require additional testing here.
 * Testing all possible cases of Step 3. of the OpenID Connect Third Party intiated login flow is
 * nearly impossible if spring-security-lti13 is not mocked. Because of that, there is not a full integration test
 * provided here.
 * However, Lti13LaunchFilter is responsible to handle this step and is tested extensively.
 * see <a href="https://www.imsglobal.org/spec/lti/v1p3/#lti-message-general-details">LTI message general details</a>
 * see <a href="https://www.imsglobal.org/spec/security/v1p0/#openid_connect_launch_flow">OpenId Connect launch flow</a>
 */
class Lti13LaunchIntegrationTest extends AbstractSpringIntegrationJenkinsGitlabTest {

    private final Key signingKey = new SecretKeySpec("a".repeat(100).getBytes(), SignatureAlgorithm.HS256.getJcaName());

    private final String validIdToken = Jwts.builder().setExpiration(Date.from(Instant.now().plusSeconds(60))).setIssuer("https://example.com").setAudience("client-id")
            .setId("1234").signWith(signingKey).compact();

    private final String outdatedToken = Jwts.builder().setExpiration(Date.from(Instant.now().minusSeconds(60))).setIssuer("https://example.com").setAudience("client-id")
            .setId("1234").signWith(signingKey).compact();

    private final String validState = "validState";

    @Test
    @WithAnonymousUser
    void redirectProxy() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("id_token", validIdToken);
        body.put("state", validState);

        URI header = request.postForm(CustomLti13Configurer.LTI13_LOGIN_REDIRECT_PROXY_PATH, body, HttpStatus.FOUND);

        validateRedirect(header, validIdToken);
    }

    @Test
    @WithAnonymousUser
    void redirectProxyNoState() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("id_token", validIdToken);

        request.postFormWithoutLocation(CustomLti13Configurer.LTI13_LOGIN_REDIRECT_PROXY_PATH, body, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithAnonymousUser
    void redirectProxyNoToken() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("state", validState);

        request.postFormWithoutLocation(CustomLti13Configurer.LTI13_LOGIN_REDIRECT_PROXY_PATH, body, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithAnonymousUser
    void redirectProxyInvalidToken() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("state", validState);
        body.put("id_token", "invalid-token");

        request.postFormWithoutLocation(CustomLti13Configurer.LTI13_LOGIN_REDIRECT_PROXY_PATH, body, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithAnonymousUser
    void redirectProxyOutdatedToken() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("state", validState);
        body.put("id_token", outdatedToken);

        request.postFormWithoutLocation(CustomLti13Configurer.LTI13_LOGIN_REDIRECT_PROXY_PATH, body, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithAnonymousUser
    void redirectProxyTokenInvalidSignature() throws Exception {
        // We can't validate the signature, hence we ignore it.
        String invalidSignatureToken = validIdToken.substring(0, validIdToken.lastIndexOf(".")) + outdatedToken.substring(outdatedToken.lastIndexOf("."));
        Map<String, Object> body = new HashMap<>();
        body.put("state", validState);
        body.put("id_token", invalidSignatureToken);

        URI header = request.postForm(CustomLti13Configurer.LTI13_LOGIN_REDIRECT_PROXY_PATH, body, HttpStatus.FOUND);
        validateRedirect(header, invalidSignatureToken);
    }

    @Test
    @WithMockUser(value = "student1", roles = "USER")
    void oidcFlowFails_noRequestCached() throws Exception {
        String ltiLaunchUri = CustomLti13Configurer.LTI13_LOGIN_PATH + "?id_token=some-token&state=some-state";
        request.get(ltiLaunchUri, HttpStatus.INTERNAL_SERVER_ERROR, Object.class);
    }

    private void validateRedirect(URI locationHeader, String token) {
        assertEquals(LtiResource.LOGIN_REDIRECT_CLIENT_PATH, locationHeader.getPath());

        List<NameValuePair> params = URLEncodedUtils.parse(locationHeader, StandardCharsets.UTF_8);
        assertUriParamsContain(params, "id_token", token);
        assertUriParamsContain(params, "state", validState);
    }
}
