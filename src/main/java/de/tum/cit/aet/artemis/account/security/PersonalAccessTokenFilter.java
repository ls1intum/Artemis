package de.tum.cit.aet.artemis.account.security;

import static de.tum.cit.aet.artemis.core.config.Constants.BEARER_PREFIX;
import static de.tum.cit.aet.artemis.core.config.Constants.JWT_COOKIE_NAME;
import static de.tum.cit.aet.artemis.core.config.Constants.PERSONAL_ACCESS_TOKEN_PREFIX;

import java.io.IOException;
import java.util.Optional;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.GenericFilterBean;
import org.springframework.web.util.WebUtils;

import de.tum.cit.aet.artemis.account.service.PersonalAccessTokenService;
import de.tum.cit.aet.artemis.core.security.allowedScopes.AgentAccessErrors;
import de.tum.cit.aet.artemis.core.security.allowedScopes.PersonalAccessTokenAuthentication;

/**
 * Authenticates requests that carry a personal access token as a bearer token.
 * <p>
 * Only {@code /api/} requests are considered. A request with an invalid, expired or revoked token is rejected with 401
 * instead of falling through as anonymous, so that a client learns that its token is the problem. Which endpoints such a
 * request may reach is decided afterwards by {@code AllowedScopesInterceptor}.
 */
public class PersonalAccessTokenFilter extends GenericFilterBean {

    /** A separate logger so that operators can route the audit trail of token calls independently. */
    private static final Logger log = LoggerFactory.getLogger("de.tum.cit.aet.artemis.agentaccess.audit");

    private static final String API_PREFIX = "/api/";

    /** Resolved per request rather than injected: the service exists only when agent access is enabled. */
    private final ObjectProvider<PersonalAccessTokenService> personalAccessTokenService;

    public PersonalAccessTokenFilter(ObjectProvider<PersonalAccessTokenService> personalAccessTokenService) {
        this.personalAccessTokenService = personalAccessTokenService;
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        String secret = extractSecret(request.getHeader(HttpHeaders.AUTHORIZATION));
        if (secret == null || !request.getRequestURI().startsWith(API_PREFIX) || isOpenOrInternalPath(request.getRequestURI())) {
            filterChain.doFilter(servletRequest, servletResponse);
            return;
        }

        if (WebUtils.getCookie(request, JWT_COOKIE_NAME) != null) {
            // The same single-method rule the JWT filter applies to a cookie together with a bearer token.
            AgentAccessErrors.reject(response, HttpStatus.BAD_REQUEST, "Send either a personal access token or a session cookie, not both.");
            return;
        }

        PersonalAccessTokenService service = personalAccessTokenService.getIfAvailable();
        Optional<PersonalAccessTokenAuthentication> authentication = service != null ? service.authenticate(secret) : Optional.empty();
        if (authentication.isEmpty()) {
            AgentAccessErrors.reject(response, HttpStatus.UNAUTHORIZED, "The token is unknown, expired or revoked, or its owner may not use tokens.");
            return;
        }

        SecurityContextHolder.getContext().setAuthentication(authentication.get());
        try {
            filterChain.doFilter(servletRequest, servletResponse);
        }
        finally {
            // The path only, never the query string, which can carry user data.
            log.info("token={} user={} {} {} -> {}", authentication.get().getTokenId(), authentication.get().getName(), request.getMethod(), request.getRequestURI(),
                    response.getStatus());
        }
    }

    /**
     * A token never needs a public or an internal endpoint, and some of them, such as the LTI launch, turn the authentication of the
     * request into a session. Such a request is left to the other filters, which do not know the token.
     */
    private static boolean isOpenOrInternalPath(String requestUri) {
        return requestUri.contains("/public/") || requestUri.contains("/internal/");
    }

    private static String extractSecret(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return null;
        }
        String credentials = authorizationHeader.substring(BEARER_PREFIX.length()).trim();
        return credentials.startsWith(PERSONAL_ACCESS_TOKEN_PREFIX) ? credentials : null;
    }
}
