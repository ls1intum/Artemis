package de.tum.in.www1.artemis;

import static de.tum.in.www1.artemis.util.TestUriParamsUtil.assertUriParamsContain;
import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.config.lti.CustomLti13Configurer;
import de.tum.in.www1.artemis.web.rest.LtiResource;

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

    @Test
    @WithAnonymousUser
    void redirectProxy() throws Exception {
        String idToken = "some-token";
        String state = "some-state";
        Map<String, Object> body = new HashMap<>();
        body.put("id_token", idToken);
        body.put("state", state);

        URI header = request.postForm(CustomLti13Configurer.LTI13_LOGIN_REDIRECT_PROXY_PATH, body, HttpStatus.FOUND);

        assertEquals(LtiResource.LOGIN_REDIRECT_CLIENT_PATH, header.getPath());

        List<NameValuePair> params = URLEncodedUtils.parse(header, StandardCharsets.UTF_8);
        assertUriParamsContain(params, "id_token", idToken);
        assertUriParamsContain(params, "state", state);
    }

    @Test
    @WithAnonymousUser
    void redirectProxyNoState() throws Exception {
        String idToken = "some-token";
        Map<String, Object> body = new HashMap<>();
        body.put("id_token", idToken);

        request.postFormWithoutLocation(CustomLti13Configurer.LTI13_LOGIN_REDIRECT_PROXY_PATH, body, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    @WithAnonymousUser
    void redirectProxyNoToken() throws Exception {
        String state = "some-state";
        Map<String, Object> body = new HashMap<>();
        body.put("state", state);

        request.postFormWithoutLocation(CustomLti13Configurer.LTI13_LOGIN_REDIRECT_PROXY_PATH, body, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    @WithMockUser(value = "student1", roles = "USER")
    void oidcFlowFails_noRequestCached() throws Exception {
        String ltiLaunchUri = CustomLti13Configurer.LTI13_LOGIN_PATH + "?id_token=some-token&state=some-state";
        request.get(ltiLaunchUri, HttpStatus.INTERNAL_SERVER_ERROR, Object.class);
    }
}
