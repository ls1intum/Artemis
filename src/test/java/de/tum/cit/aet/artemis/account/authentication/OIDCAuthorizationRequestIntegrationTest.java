package de.tum.cit.aet.artemis.account.authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.util.UriComponentsBuilder;

import de.tum.cit.aet.artemis.account.config.OIDCConstants;
import de.tum.cit.aet.artemis.account.repository.oidc.DistributedOIDCAuthorizationRequestRepository;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationLocalVCSamlTest;

/**
 * The callback of the identity provider must work on any node, because the load balancer has no sticky sessions.
 * A request without an HTTP session stands in for a callback that reaches another node than the one that started the login.
 */
class OIDCAuthorizationRequestIntegrationTest extends AbstractSpringIntegrationLocalVCSamlTest {

    private static final String CODE_CHALLENGE = "E9Melhoa2OwvFrGMTJguCH5A_lUhKw6YGzaWzkTCdcM";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DistributedOIDCAuthorizationRequestRepository authorizationRequestRepository;

    @Test
    void testCallbackWithoutTheSessionOfTheStartingNodeFindsTheAuthorizationRequest() throws Exception {
        MvcResult start = startLogin();
        String state = stateOf(start);

        // Nothing may be kept in the node-local session
        assertThat(start.getRequest().getSession(false)).isNull();
        Cookie stateCookie = start.getResponse().getCookie(OIDCConstants.OIDC_STATE_COOKIE_NAME);
        assertThat(stateCookie).isNotNull();
        assertThat(stateCookie.getValue()).isEqualTo(state);
        assertThat(stateCookie.isHttpOnly()).isTrue();

        MockHttpServletRequest callback = callback(state, stateCookie);
        OAuth2AuthorizationRequest authorizationRequest = authorizationRequestRepository.removeAuthorizationRequest(callback, new MockHttpServletResponse());

        assertThat(authorizationRequest).isNotNull();
        assertThat(authorizationRequest.getState()).isEqualTo(state);
        assertThat(authorizationRequest.<Boolean>getAttribute(OIDCConstants.OIDC_REMEMBER_ME_ATTRIBUTE)).isTrue();
        assertThat(authorizationRequest.<String>getAttribute(OIDCConstants.OIDC_REDIRECT_TARGET_ATTRIBUTE)).isEqualTo(OIDCConstants.VS_CODE_REDIRECT_TARGET);
        assertThat(authorizationRequest.<String>getAttribute(OIDCConstants.OIDC_CODE_CHALLENGE_ATTRIBUTE)).isEqualTo(CODE_CHALLENGE);
        assertThat(callback.getAttribute(OIDCConstants.OIDC_AUTHORIZATION_REQUEST_ATTRIBUTE)).isSameAs(authorizationRequest);
    }

    @Test
    void testAuthorizationRequestCanOnlyBeUsedOnce() throws Exception {
        MvcResult start = startLogin();
        String state = stateOf(start);
        Cookie stateCookie = start.getResponse().getCookie(OIDCConstants.OIDC_STATE_COOKIE_NAME);

        MockHttpServletResponse firstResponse = new MockHttpServletResponse();
        assertThat(authorizationRequestRepository.removeAuthorizationRequest(callback(state, stateCookie), firstResponse)).isNotNull();
        assertThat(firstResponse.getCookie(OIDCConstants.OIDC_STATE_COOKIE_NAME).getMaxAge()).isZero();

        assertThat(authorizationRequestRepository.removeAuthorizationRequest(callback(state, stateCookie), new MockHttpServletResponse())).isNull();
    }

    @Test
    void testCallbackFromAnotherBrowserIsRejected() throws Exception {
        String state = stateOf(startLogin());

        // The callback URL of someone else's login, opened in a browser without the matching state cookie
        assertThat(authorizationRequestRepository.loadAuthorizationRequest(callback(state, null))).isNull();
        assertThat(authorizationRequestRepository.loadAuthorizationRequest(callback(state, new Cookie(OIDCConstants.OIDC_STATE_COOKIE_NAME, "state-of-another-login")))).isNull();

        // The rejected attempts must not have used up the request of the browser that started the login
        assertThat(authorizationRequestRepository.loadAuthorizationRequest(callback(state, new Cookie(OIDCConstants.OIDC_STATE_COOKIE_NAME, state)))).isNotNull();
    }

    @Test
    void testCallbackWithoutStateCookieRedirectsToSignInWithError() throws Exception {
        String state = stateOf(startLogin());

        mockMvc.perform(get("/login/oauth2/code/oidc").param("code", "some-code").param("state", state)).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/sign-in?loginError=oidcFailure"));
    }

    private MvcResult startLogin() throws Exception {
        return mockMvc.perform(
                get("/oauth2/authorization/oidc").param("rememberMe", "true").param("redirect", OIDCConstants.VS_CODE_REDIRECT_TARGET).param("code_challenge", CODE_CHALLENGE))
                .andExpect(status().is3xxRedirection()).andReturn();
    }

    private static String stateOf(MvcResult start) {
        String location = start.getResponse().getRedirectedUrl();
        assertThat(location).startsWith("http://mock-auth");
        String state = URLDecoder.decode(UriComponentsBuilder.fromUriString(location).build().getQueryParams().getFirst("state"), StandardCharsets.UTF_8);
        assertThat(state).isNotBlank();
        return state;
    }

    private static MockHttpServletRequest callback(String state, Cookie stateCookie) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/login/oauth2/code/oidc");
        request.setParameter("code", "some-code");
        request.setParameter("state", state);
        if (stateCookie != null) {
            request.setCookies(stateCookie);
        }
        return request;
    }
}
