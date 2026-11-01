package de.tum.cit.aet.artemis.account.repository.oidc;

import java.time.Duration;
import java.util.Arrays;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.stereotype.Repository;
import org.springframework.util.Assert;
import org.springframework.web.util.WebUtils;

import de.tum.cit.aet.artemis.account.config.OIDCConstants;
import de.tum.cit.aet.artemis.account.config.OIDCEnabled;
import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.core.service.distributed.api.map.DistributedMap;

/**
 * Keeps the pending OIDC authorization requests in the distributed data provider, so that the callback of the identity provider can be handled by any node.
 *
 * <p>
 * Spring Security's default keeps the request in the HTTP session of the node that started the login. Without sticky sessions the callback usually reaches another
 * node, which does not know that session, and the login fails with {@code authorization_request_not_found}.
 * </p>
 *
 * <p>
 * The request is stored under its {@code state}. The same value is set in the {@value OIDCConstants#OIDC_STATE_COOKIE_NAME} cookie, and the callback is only accepted if
 * its {@code state} parameter matches the cookie. This binds the callback to the browser that started the login, which the session did before: without it, a callback URL
 * of someone else's login would sign the victim in to that other account.
 * </p>
 *
 * <p>
 * When the callback takes the request out of the store, it is also kept as the request attribute {@value OIDCConstants#OIDC_AUTHORIZATION_REQUEST_ATTRIBUTE}, so that the
 * success and failure handlers can read the parameters the login was started with.
 * </p>
 */
@Lazy
@Repository
@Conditional(OIDCEnabled.class)
public class DistributedOIDCAuthorizationRequestRepository implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    private static final Logger log = LoggerFactory.getLogger(DistributedOIDCAuthorizationRequestRepository.class);

    private static final String DEVELOPMENT_PROFILE = "dev";

    private static final String MAP_NAME = "oidcAuthorizationRequests";

    /**
     * How long a user has to sign in at the identity provider, including a second factor.
     */
    private static final Duration AUTHORIZATION_REQUEST_TIME_TO_LIVE = Duration.ofMinutes(10);

    /**
     * The cookie is only needed by the callback, so it is not sent with any other request.
     */
    private static final String STATE_COOKIE_PATH = "/login/oauth2/code/";

    private final DistributedDataProvider distributedDataProvider;

    private final Environment environment;

    private DistributedMap<String, OAuth2AuthorizationRequest> authorizationRequests;

    public DistributedOIDCAuthorizationRequestRepository(DistributedDataProvider distributedDataProvider, Environment environment) {
        this.distributedDataProvider = distributedDataProvider;
        this.environment = environment;
    }

    /**
     * Initializes the distributed map after dependency injection.
     * EventListener cannot be used here, as the bean is lazy.
     */
    @PostConstruct
    public void init() {
        authorizationRequests = distributedDataProvider.getExpiringMap(MAP_NAME, AUTHORIZATION_REQUEST_TIME_TO_LIVE);
    }

    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        String state = boundState(request);
        return state == null ? null : authorizationRequests.get(state);
    }

    @Override
    public void saveAuthorizationRequest(OAuth2AuthorizationRequest authorizationRequest, HttpServletRequest request, HttpServletResponse response) {
        if (authorizationRequest == null) {
            removeAuthorizationRequest(request, response);
            return;
        }
        String state = authorizationRequest.getState();
        Assert.hasText(state, "authorizationRequest.state cannot be empty");
        authorizationRequests.put(state, authorizationRequest);
        response.addHeader(HttpHeaders.SET_COOKIE, buildStateCookie(state, AUTHORIZATION_REQUEST_TIME_TO_LIVE).toString());
    }

    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(HttpServletRequest request, HttpServletResponse response) {
        String state = boundState(request);
        OAuth2AuthorizationRequest authorizationRequest = state == null ? null : authorizationRequests.remove(state);
        if (WebUtils.getCookie(request, OIDCConstants.OIDC_STATE_COOKIE_NAME) != null) {
            response.addHeader(HttpHeaders.SET_COOKIE, buildStateCookie("", Duration.ZERO).toString());
        }
        if (authorizationRequest != null) {
            request.setAttribute(OIDCConstants.OIDC_AUTHORIZATION_REQUEST_ATTRIBUTE, authorizationRequest);
        }
        return authorizationRequest;
    }

    /**
     * @return the {@code state} parameter of the request if the browser holds the same value in its state cookie, otherwise {@code null}
     */
    private static String boundState(HttpServletRequest request) {
        String state = request.getParameter(OAuth2ParameterNames.STATE);
        if (state == null || state.isBlank()) {
            return null;
        }
        Cookie cookie = WebUtils.getCookie(request, OIDCConstants.OIDC_STATE_COOKIE_NAME);
        if (cookie == null || !state.equals(cookie.getValue())) {
            log.warn("Rejecting the OIDC callback because its state does not match the state cookie of this browser (cookie present: {})", cookie != null);
            return null;
        }
        return state;
    }

    private ResponseCookie buildStateCookie(String value, Duration maxAge) {
        boolean isSecure = !Arrays.asList(environment.getActiveProfiles()).contains(DEVELOPMENT_PROFILE);
        // Lax, not Strict: the callback is a top-level navigation coming from the identity provider, which is another site
        return ResponseCookie.from(OIDCConstants.OIDC_STATE_COOKIE_NAME, value).httpOnly(true).sameSite("Lax").secure(isSecure).path(STATE_COOKIE_PATH).maxAge(maxAge).build();
    }
}
