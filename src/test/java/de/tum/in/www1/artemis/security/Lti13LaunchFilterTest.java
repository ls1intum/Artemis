package de.tum.in.www1.artemis.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.FilterChain;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.stubbing.Answer;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import de.tum.in.www1.artemis.config.lti.CustomLti13Configurer;
import de.tum.in.www1.artemis.security.lti.Lti13LaunchFilter;
import de.tum.in.www1.artemis.service.connectors.lti.Lti13Service;
import net.minidev.json.JSONObject;
import uk.ac.ox.ctl.lti13.lti.Claims;
import uk.ac.ox.ctl.lti13.security.oauth2.client.lti.authentication.OidcAuthenticationToken;
import uk.ac.ox.ctl.lti13.security.oauth2.client.lti.web.OAuth2LoginAuthenticationFilter;

class Lti13LaunchFilterTest {

    @Mock
    private OAuth2LoginAuthenticationFilter defaultFilter;

    @Mock
    private Lti13Service lti13Service;

    @Mock
    private PrintWriter responseWriter;

    @Mock
    private FilterChain filterChain;

    @Mock
    private HttpServletResponse httpResponse;

    @Mock
    private HttpServletRequest httpRequest;

    @Mock
    private SecurityContext securityContext;

    @Mock
    private Authentication authentication;

    @Mock
    private OidcIdToken idToken;

    private Lti13LaunchFilter launchFilter;

    private OidcAuthenticationToken oidcToken;

    private String targetLinkUri;

    private final Map<String, Object> idTokenClaims = new HashMap<>();

    private AutoCloseable closeable;

    @BeforeEach
    void init() {
        closeable = MockitoAnnotations.openMocks(this);
        launchFilter = new Lti13LaunchFilter(defaultFilter, CustomLti13Configurer.LTI13_LOGIN_PATH, lti13Service);
        SecurityContextHolder.setContext(securityContext);
        doReturn(authentication).when(securityContext).getAuthentication();
        doReturn(CustomLti13Configurer.LTI13_LOGIN_PATH).when(httpRequest).getServletPath();

        doReturn(idTokenClaims).when(idToken).getClaims();
        OidcUser oidcUser = mock(OidcUser.class);
        doReturn(idToken).when(oidcUser).getIdToken();
        doReturn(idTokenClaims).when(oidcUser).getClaims();

        Answer<Object> getClaimAnswer = invocation -> idTokenClaims.get((String) invocation.getArguments()[0]);
        doAnswer(getClaimAnswer).when(idToken).getClaim(any());
        doAnswer(getClaimAnswer).when(oidcUser).getClaim(any());
        oidcToken = new OidcAuthenticationToken(oidcUser, null, "some-registration", "some-state");

        targetLinkUri = "https://any-artemis-domain.org/course/123/exercise/1234";
    }

    @AfterEach
    void tearDown() throws Exception {
        if (closeable != null) {
            closeable.close();
        }
        reset(defaultFilter, lti13Service, responseWriter, filterChain, httpResponse, httpRequest, securityContext, authentication, idToken);
    }

    private void initValidIdToken() {
        idTokenClaims.put("iss", "https://some.lms.org");
        idTokenClaims.put("sub", "23423435");
        idTokenClaims.put(Claims.LTI_DEPLOYMENT_ID, "some-deployment-id");
        JSONObject resourceLinkClaim = new JSONObject();
        resourceLinkClaim.put("id", "some-resource-id");
        idTokenClaims.put(Claims.RESOURCE_LINK, resourceLinkClaim);
        idTokenClaims.put(Claims.TARGET_LINK_URI, targetLinkUri);
    }

    private void initValidTokenForDeepLinking() {
        idTokenClaims.put("iss", "https://some.lms.org");
        idTokenClaims.put("aud", "[962fa4d8-bcbf-49a0-94b2-2de05ad274af]");
        idTokenClaims.put("exp", "1510185728");
        idTokenClaims.put("iat", "1510185228");
        idTokenClaims.put("nonce", "fc5fdc6d-5dd6-47f4-b2c9-5d1216e9b771");
        idTokenClaims.put(Claims.LTI_DEPLOYMENT_ID, "some-deployment-id");

        idTokenClaims.put(Claims.DEEP_LINKING_SETTINGS, "{ \"deep_link_return_url\": \"https://platform.example/deep_links\" }");
        idTokenClaims.put(Claims.TARGET_LINK_URI, "https://any-artemis-domain.org/api/deep-linking/121");
        idTokenClaims.put(Claims.MESSAGE_TYPE, "LtiDeepLinkingRequest");
    }

    @Test
    void authenticatedLogin() throws Exception {
        doReturn(true).when(authentication).isAuthenticated();
        doReturn(CustomLti13Configurer.LTI13_LOGIN_PATH).when(httpRequest).getServletPath();
        doReturn(oidcToken).when(defaultFilter).attemptAuthentication(any(), any());
        doReturn(responseWriter).when(httpResponse).getWriter();
        initValidIdToken();

        launchFilter.doFilter(httpRequest, httpResponse, filterChain);

        verify(httpResponse, never()).setStatus(HttpStatus.UNAUTHORIZED.value());
        verify(httpResponse).setContentType("application/json");
        verify(httpResponse).setCharacterEncoding("UTF-8");
        verify(lti13Service).performLaunch(any(), any());

        ArgumentCaptor<JSONObject> argument = ArgumentCaptor.forClass(JSONObject.class);
        verify(responseWriter).print(argument.capture());
        JSONObject responseJsonBody = argument.getValue();
        verify(lti13Service).buildLtiResponse(any(), any());
        assertThat(((String) responseJsonBody.get("targetLinkUri"))).as("Response body contains the expected targetLinkUri").contains(this.targetLinkUri);
    }

    @Test
    void authenticatedLoginForDeepLinking() throws Exception {
        doReturn(true).when(authentication).isAuthenticated();
        doReturn(CustomLti13Configurer.LTI13_LOGIN_PATH).when(httpRequest).getServletPath();
        doReturn(oidcToken).when(defaultFilter).attemptAuthentication(any(), any());
        doReturn(responseWriter).when(httpResponse).getWriter();
        initValidTokenForDeepLinking();

        launchFilter.doFilter(httpRequest, httpResponse, filterChain);

        verify(httpResponse, never()).setStatus(HttpStatus.UNAUTHORIZED.value());
        verify(httpResponse).setContentType("application/json");
        verify(httpResponse).setCharacterEncoding("UTF-8");
        verify(lti13Service).startDeepLinking(any(), any());

        ArgumentCaptor<JSONObject> argument = ArgumentCaptor.forClass(JSONObject.class);
        verify(responseWriter).print(argument.capture());
        JSONObject responseJsonBody = argument.getValue();
        verify(lti13Service).buildLtiResponse(any(), any());
        assertThat(((String) responseJsonBody.get("targetLinkUri"))).as("Response body contains the expected targetLinkUri")
                .contains("https://any-artemis-domain.org/api/deep-linking/121");

    }

    @Test
    void authenticatedLogin_oauth2AuthenticationException() throws Exception {
        doReturn(true).when(authentication).isAuthenticated();
        doReturn(CustomLti13Configurer.LTI13_LOGIN_PATH).when(httpRequest).getServletPath();
        doThrow(new OAuth2AuthenticationException(new OAuth2Error(OAuth2ErrorCodes.INVALID_REQUEST))).when(defaultFilter).attemptAuthentication(any(), any());

        launchFilter.doFilter(httpRequest, httpResponse, filterChain);

        verify(httpResponse).sendError(eq(HttpStatus.INTERNAL_SERVER_ERROR.value()), any());
        verify(lti13Service, never()).performLaunch(any(), any());
        verify(lti13Service, never()).startDeepLinking(any(), any());
    }

    @Test
    void authenticatedLogin_noAuthenticationTokenReturned() throws Exception {
        doReturn(true).when(authentication).isAuthenticated();
        doReturn(CustomLti13Configurer.LTI13_LOGIN_PATH).when(httpRequest).getServletPath();
        doReturn(null).when(defaultFilter).attemptAuthentication(any(), any());

        launchFilter.doFilter(httpRequest, httpResponse, filterChain);

        verify(httpResponse).sendError(eq(HttpStatus.INTERNAL_SERVER_ERROR.value()), any());
        verify(lti13Service, never()).performLaunch(any(), any());
        verify(lti13Service, never()).startDeepLinking(any(), any());
    }

    @Test
    void authenticatedLogin_serviceLaunchFailed() throws Exception {
        doReturn(true).when(authentication).isAuthenticated();
        doReturn(CustomLti13Configurer.LTI13_LOGIN_PATH).when(httpRequest).getServletPath();
        doThrow(new RuntimeException("something")).when(lti13Service).performLaunch(any(), any());

        launchFilter.doFilter(httpRequest, httpResponse, filterChain);

        verify(httpResponse).sendError(eq(HttpStatus.INTERNAL_SERVER_ERROR.value()), any());
    }
}
