package de.tum.cit.aet.artemis.account.security;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import de.tum.cit.aet.artemis.admin.service.RateLimitService;
import de.tum.cit.aet.artemis.core.exception.RateLimitExceededException;
import de.tum.cit.aet.artemis.core.security.RateLimitType;
import de.tum.cit.aet.artemis.core.util.HttpRequestUtils;
import inet.ipaddr.IPAddress;
import inet.ipaddr.IPAddressString;

/**
 * Filter that enforces rate limiting on OIDC authorization initialization requests.
 * Intercepts requests matching `/oauth2/authorization/**` and checks the client's rate limit budget.
 */
public class OIDCRateLimitFilter extends OncePerRequestFilter {

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

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
        // Decode URI to protect the rate-limit bypass via percent-encoding (e.g., %61uthorization)
        String requestUri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && requestUri != null && requestUri.startsWith(contextPath)) {
            requestUri = requestUri.substring(contextPath.length());
        }

        String path = requestUri;
        if (requestUri != null) {
            try {
                path = URLDecoder.decode(requestUri, StandardCharsets.UTF_8);
            }
            catch (Exception ignored) {
                path = requestUri;
            }
        }

        // Filter should only be triggered for /oauth2/authorization/** requests
        if (path != null && PATH_MATCHER.match("/oauth2/authorization/**", path)) {
            IPAddress clientId = null;
            clientId = rateLimitService.resolveClientId();

            // If clientId is zero because of port -> parse the port too
            if (clientId == null && request != null) {
                String ipString = HttpRequestUtils.getIpStringFromRequest(request);
                if (ipString == null || ipString.isBlank()) {
                    ipString = request.getRemoteAddr();
                }
                if (ipString != null && !ipString.isBlank()) {
                    clientId = parseIpWithPort(ipString.trim());
                }
            }

            // if clientId can not be resolved -> reject the request
            if (clientId == null) {
                response.setStatus(HttpStatus.BAD_REQUEST.value());
                response.getWriter().write("Unable to resolve valid client IP address.");
                return;
            }

            try {
                rateLimitService.enforcePerMinute(clientId, RateLimitType.OIDC_AUTHORIZATION);
            }
            catch (RateLimitExceededException e) {
                long seconds = Math.max(1, e.getRetryAfterSeconds() + 1);
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setHeader("Retry-After", String.valueOf(seconds));
                response.getWriter().write("Rate limit exceeded. Try again in " + seconds + " seconds.");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Parses an IP address string that may include a port (IPv4 host:port or [IPv6]:port),
     * returning a valid IPAddress object.
     */
    private IPAddress parseIpWithPort(String ipString) {
        if (ipString == null) {
            return null;
        }
        String host = ipString;
        if (host.startsWith("[")) {
            int bracketEnd = host.indexOf(']');
            if (bracketEnd != -1) {
                host = host.substring(1, bracketEnd);
            }
        }
        else {
            int lastColon = host.lastIndexOf(':');
            if (lastColon != -1 && host.indexOf(':') == lastColon) {
                host = host.substring(0, lastColon);
            }
        }

        IPAddress address = new IPAddressString(host).getAddress();
        if (address == null) {
            address = new IPAddressString(ipString).getAddress();
        }
        return address;
    }
}
