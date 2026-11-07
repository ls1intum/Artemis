package de.tum.cit.aet.artemis.account.security;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import de.tum.cit.aet.artemis.admin.service.RateLimitService;
import de.tum.cit.aet.artemis.core.exception.RateLimitExceededException;
import de.tum.cit.aet.artemis.core.security.RateLimitType;
import inet.ipaddr.IPAddress;

/**
 * Filter that enforces rate limiting on OIDC authorization initialization requests.
 * Intercepts requests matching `/oauth2/authorization` and checks the client's rate limit budget.
 */
public class OIDCRateLimitFilter extends OncePerRequestFilter {

    private final RateLimitService rateLimitService;

    /**
     * Instantiates a new OIDC rate limit filter.
     *
     * @param rateLimitService the rate limit service used to check budgets and resolve client addresses
     */
    public OIDCRateLimitFilter(RateLimitService rateLimitService) {
        this.rateLimitService = rateLimitService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        // This filter is triggered for /oauth2/authorization and /login/oauth2/code. Rate-limiting applies only for initialization endpoint, so we track it
        String requestUri = request.getRequestURI();
        if (requestUri != null && requestUri.contains("/oauth2/authorization")) {
            IPAddress clientId = rateLimitService.resolveClientId();
            try {
                rateLimitService.enforcePerMinute(clientId, RateLimitType.OIDC_AUTHORIZATION);
            }
            catch (RateLimitExceededException e) {
                long seconds = e.getRetryAfterSeconds();
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setHeader("Retry-After", String.valueOf(seconds));
                response.getWriter().write("Rate limit exceeded. Try again in " + seconds + " seconds.");
                // Break the filter chain, the request won't be stored and redirected
                return;
            }
        }

        filterChain.doFilter(request, response);
    }
}
