package de.tum.in.www1.artemis.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
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

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import de.tum.in.www1.artemis.config.lti.CustomLti13Configurer;
import de.tum.in.www1.artemis.exception.LtiEmailAlreadyInUseException;
import de.tum.in.www1.artemis.security.lti.Lti13LaunchFilter;
import de.tum.in.www1.artemis.service.connectors.lti.Lti13Service;
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
        JsonObject resourceLinkClaim = new JsonObject();
        resourceLinkClaim.addProperty("id", "some-resource-id");
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
        idTokenClaims.put(Claims.TARGET_LINK_URI, "https://any-artemis-domain.org/lti/deep-linking/121");
        idTokenClaims.put(Claims.MESSAGE_TYPE, "LtiDeepLinkingRequest");
    }

    @Test
    void authenticatedLogin() throws Exception {
        doReturn(true).when(authentication).isAuthenticated();
        JsonObject responseJsonBody = getMockJsonObject(false);
        verify(lti13Service).performLaunch(any(), any());
        verify(httpResponse, never()).setStatus(HttpStatus.UNAUTHORIZED.value());
        assertThat((responseJsonBody.get("targetLinkUri").getAsString())).as("Response body contains the expected targetLinkUri").contains(this.targetLinkUri);
        verify(lti13Service).buildLtiResponse(any(), any());
    }

    @Test
    void authenticatedLoginForDeepLinking() throws Exception {
        doReturn(true).when(authentication).isAuthenticated();
        JsonObject responseJsonBody = getMockJsonObject(true);
        verify(lti13Service).startDeepLinking(any());
        verify(httpResponse, never()).setStatus(HttpStatus.UNAUTHORIZED.value());
        assertThat((responseJsonBody.get("targetLinkUri").toString())).as("Response body contains the expected targetLinkUri")
                .contains("https://any-artemis-domain.org/lti/deep-linking/121");
        verify(lti13Service).buildLtiResponse(any(), any());

    }

    @Test
    void authenticatedLogin_oauth2AuthenticationException() throws Exception {
        doReturn(true).when(authentication).isAuthenticated();
        doReturn(CustomLti13Configurer.LTI13_LOGIN_PATH).when(httpRequest).getServletPath();
        doThrow(new OAuth2AuthenticationException(new OAuth2Error(OAuth2ErrorCodes.INVALID_REQUEST))).when(defaultFilter).attemptAuthentication(any(), any());

        launchFilter.doFilter(httpRequest, httpResponse, filterChain);

        verify(httpResponse).sendError(eq(HttpStatus.INTERNAL_SERVER_ERROR.value()), any());
        verify(lti13Service, never()).performLaunch(any(), any());
        verify(lti13Service, never()).startDeepLinking(any());
    }

    @Test
    void authenticatedLogin_noAuthenticationTokenReturned() throws Exception {
        doReturn(true).when(authentication).isAuthenticated();
        doReturn(CustomLti13Configurer.LTI13_LOGIN_PATH).when(httpRequest).getServletPath();
        doReturn(null).when(defaultFilter).attemptAuthentication(any(), any());

        launchFilter.doFilter(httpRequest, httpResponse, filterChain);

        verify(httpResponse).sendError(eq(HttpStatus.INTERNAL_SERVER_ERROR.value()), any());
        verify(lti13Service, never()).performLaunch(any(), any());
        verify(lti13Service, never()).startDeepLinking(any());
    }

    @Test
    void authenticatedLogin_serviceLaunchFailed() throws Exception {
        doReturn(true).when(authentication).isAuthenticated();
        doReturn(CustomLti13Configurer.LTI13_LOGIN_PATH).when(httpRequest).getServletPath();
        doThrow(new RuntimeException("something")).when(lti13Service).performLaunch(any(), any());

        launchFilter.doFilter(httpRequest, httpResponse, filterChain);

        verify(httpResponse).sendError(eq(HttpStatus.INTERNAL_SERVER_ERROR.value()), any());
    }

    @Test
    void emailAddressAlreadyInUseServiceLaunchFailed() throws ServletException, IOException {
        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);
        doReturn(printWriter).when(httpResponse).getWriter();

        doReturn(false).when(authentication).isAuthenticated();
        doThrow(new LtiEmailAlreadyInUseException()).when(lti13Service).performLaunch(any(), any());

        doReturn(CustomLti13Configurer.LTI13_LOGIN_PATH).when(httpRequest).getServletPath();
        doReturn(oidcToken).when(defaultFilter).attemptAuthentication(any(), any());

        JsonObject responseJsonBody = getMockJsonObject(false);

        verify(httpResponse).setStatus(HttpStatus.UNAUTHORIZED.value());
        assertThat((responseJsonBody.get("targetLinkUri").toString())).as("Response body contains the expected targetLinkUri")
                .contains("https://any-artemis-domain.org/course/123/exercise/1234");
        assertThat(responseJsonBody.get("ltiIdToken")).isNull();
        assertThat((responseJsonBody.get("clientRegistrationId").toString())).as("Response body contains the expected clientRegistrationId").contains("some-registration");
    }

    @Test
    void emailAddressAlreadyInUseServiceDeepLinkingFailed() throws ServletException, IOException {
        doReturn(false).when(authentication).isAuthenticated();
        doThrow(new LtiEmailAlreadyInUseException()).when(lti13Service).startDeepLinking(any());

        doReturn(CustomLti13Configurer.LTI13_LOGIN_PATH).when(httpRequest).getServletPath();
        doReturn(oidcToken).when(defaultFilter).attemptAuthentication(any(), any());
        initValidTokenForDeepLinking();

        JsonObject responseJsonBody = getMockJsonObject(true);

        verify(httpResponse).setStatus(HttpStatus.UNAUTHORIZED.value());
        assertThat((responseJsonBody.get("targetLinkUri").toString())).as("Response body contains the expected targetLinkUri")
                .contains("https://any-artemis-domain.org/lti/deep-linking/121");
        assertThat(responseJsonBody.get("ltiIdToken")).isNull();
        assertThat((responseJsonBody.get("clientRegistrationId").toString())).as("Response body contains the expected clientRegistrationId").contains("some-registration");

    }

    private JsonObject getMockJsonObject(boolean isDeepLinkingRequest) throws IOException, ServletException {
        doReturn(CustomLti13Configurer.LTI13_LOGIN_PATH).when(httpRequest).getServletPath();
        doReturn(oidcToken).when(defaultFilter).attemptAuthentication(any(), any());
        doReturn(responseWriter).when(httpResponse).getWriter();
        if (isDeepLinkingRequest) {
            initValidTokenForDeepLinking();
        }
        else {
            initValidIdToken();
        }

        launchFilter.doFilter(httpRequest, httpResponse, filterChain);

        verify(httpResponse).setContentType("application/json");

        ArgumentCaptor<String> argument = ArgumentCaptor.forClass(String.class);
        verify(responseWriter).print(argument.capture());

        String jsonResponseString = argument.getValue();
        JsonParser parser = new JsonParser();
        JsonObject responseJsonBody = parser.parse(jsonResponseString).getAsJsonObject();

        return responseJsonBody;
    }
}
