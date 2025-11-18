package de.tum.cit.aet.artemis.core.security.jwt;

import static de.tum.cit.aet.artemis.core.config.Constants.BEARER_PREFIX;
import static de.tum.cit.aet.artemis.core.config.Constants.JWT_COOKIE_NAME;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;

import jakarta.annotation.Nullable;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.NotAuthorizedException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.GenericFilterBean;
import org.springframework.web.util.WebUtils;

/**
 * Filters incoming requests and installs a Spring Security principal if a header corresponding to a valid user is found.
 */
public class JWTFilter extends GenericFilterBean {

    private static final Logger log = LoggerFactory.getLogger(JWTFilter.class);

    private final TokenProvider tokenProvider;

    private final JWTCookieService jwtCookieService;

    private final long tokenValidityInSecondsForPasskey;

    public JWTFilter(TokenProvider tokenProvider, JWTCookieService jwtCookieService, long tokenValidityInSecondsForPasskey) {
        this.tokenProvider = tokenProvider;
        this.jwtCookieService = jwtCookieService;
        this.tokenValidityInSecondsForPasskey = tokenValidityInSecondsForPasskey;
    }

    /**
     * Renews the JWT token if its remaining lifetime is less than 50% of its total validity period.
     * The renewed token retains the original issuedAt timestamp.
     *
     * <p>
     * <b>Configurable values:</b>
     * </p>
     * <ul>
     * <li>{@code artemis.user-management.passkey.token-validity-in-seconds-for-passkey}: Caps the maximum lifetime of a renewed token, limiting how long an infrequently used
     * token can remain valid.</li>
     * <li>{@code jhipster.security.authentication.jwt.token-validity-in-seconds-for-remember-me}: Determines the expiration time for authentication tokens.</li>
     * </ul>
     *
     * <p>
     * <b>Security:</b>
     * </p>
     * <p>
     * This mechanism relies on secure cookie storage and HTTPS. Note that no token revocation
     * mechanism is implemented. Role changes are not checked, as resources for non-admin roles
     * will validate the current role during access.
     * </p>
     *
     * @param jwtToken       The current JWT token to evaluate for renewal.
     * @param authentication The {@link org.springframework.security.core.Authentication} object associated with the token.
     * @param response       The {@link jakarta.servlet.http.HttpServletResponse} where the renewed token will be added as a cookie.
     * @throws NotAuthorizedException If the token cannot be renewed due to validation or other issues.
     */
    private void rotateTokenSilently(String jwtToken, Authentication authentication, HttpServletResponse response) throws NotAuthorizedException {
        // Only rotate tokens that were issued using the PASSKEY authentication method
        boolean canRefreshToken = Objects.equals(this.tokenProvider.getAuthenticationMethod(jwtToken), AuthenticationMethod.PASSKEY);
        if (!canRefreshToken) {
            return;
        }

        // Extract issued and expiration timestamps from the existing token
        Date issuedAt = this.tokenProvider.getIssuedAtDate(jwtToken);
        Date expirationDate = this.tokenProvider.getExpirationDate(jwtToken);

        // Calculate remaining lifetime of the token
        long nowInMs = System.currentTimeMillis();
        long tokenValidityInMs = this.tokenProvider.getTokenValidity(true);
        long remainingLifetime = expirationDate.getTime() - nowInMs;

        // Trigger rotation if token has less than half of its validity period remaining
        boolean isRemainingLifetimeBelowHalf = remainingLifetime < tokenValidityInMs / 2;
        if (isRemainingLifetimeBelowHalf) {
            // Compute the new expiration time, respecting the original token's max lifetime
            long newTokenExpirationTimeInMs = Math.min(nowInMs + tokenValidityInMs, issuedAt.getTime() + Math.multiplyExact(this.tokenValidityInSecondsForPasskey, 1000));
            // Determine the lifetime of the rotated token
            long rotatedTokenDurationInMs = newTokenExpirationTimeInMs - nowInMs;
            // Create the rotated token with updated expiration and same issued time/tools
            var rotatedToken = this.tokenProvider.createToken(authentication, issuedAt, new Date(newTokenExpirationTimeInMs), this.tokenProvider.getTools(jwtToken), true);

            // Build and set the new token as a response cookie
            ResponseCookie responseCookie = jwtCookieService.buildRotatedCookie(rotatedToken, rotatedTokenDurationInMs);
            response.addHeader(HttpHeaders.SET_COOKIE, responseCookie.toString());
        }
    }

    /**
     * Filter that extracts a JWT from the incoming HTTP request, performs authentication,
     * optionally rotates the token if it meets security criteria, and sets the authentication
     * context for downstream processing.
     *
     * <p>
     * If a valid token is extracted from a cookie and does not contain tool metadata,
     * it is silently rotated and re-issued via a response cookie to extend the session securely.
     * </p>
     *
     * @param servletRequest  the incoming request
     * @param servletResponse the outgoing response
     * @param filterChain     the remaining filter chain
     * @throws IOException      in case of I/O errors
     * @throws ServletException in case of filter chain issues
     */
    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        HttpServletRequest httpServletRequest = (HttpServletRequest) servletRequest;
        HttpServletResponse httpServletResponse = (HttpServletResponse) servletResponse;
        String jwtToken = null;
        String source = null;
        try {
            // Extract a valid JWT (if any) and track its source (e.g., header, cookie)
            JwtWithSource jwtWithSource = extractValidJwt(httpServletRequest, this.tokenProvider);
            if (jwtWithSource != null) {
                jwtToken = jwtWithSource.jwt();
                source = jwtWithSource.source();
            }
        }
        catch (IllegalArgumentException e) {
            // Send a 400 response if the JWT is malformed
            httpServletResponse.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        if (jwtToken != null) {
            // Resolve the Spring Security authentication from the JWT
            Authentication authentication = this.tokenProvider.getAuthentication(jwtToken);

            // Only consider rotating secure, cookie-based tokens without tool-specific data
            boolean tokenIsConsideredSecure = "cookie".equals(source) && this.tokenProvider.getTools(jwtToken) == null;
            if (tokenIsConsideredSecure && authentication != null) {
                rotateTokenSilently(jwtToken, authentication, httpServletResponse);
            }

            // Set the security context if authentication succeeded
            if (authentication != null) {
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }

        // Continue with the remaining filters in the chain
        filterChain.doFilter(servletRequest, servletResponse);
    }

    /**
     * Extracts the valid jwt found in the cookie or the Authorization header
     *
     * @param httpServletRequest the http request
     * @param tokenProvider      the Artemis token provider used to generate and validate jwt's
     * @return the valid jwt or null if not found or invalid
     */
    @Nullable
    public static JwtWithSource extractValidJwt(HttpServletRequest httpServletRequest, TokenProvider tokenProvider) {
        if (isIgnoredUri(httpServletRequest.getRequestURI())) {
            return null;
        }

        var cookie = WebUtils.getCookie(httpServletRequest, JWT_COOKIE_NAME);
        var authHeader = httpServletRequest.getHeader(HttpHeaders.AUTHORIZATION);

        if (cookie == null && authHeader == null) {
            return null;
        }

        if (cookie != null && authHeader != null) {
            // Single Method Enforcement: Only one method of authentication is allowed
            throw new IllegalArgumentException("Multiple authentication methods detected: Both JWT cookie and Bearer token are present");
        }

        String jwtToken = cookie != null ? getJwtFromCookie(cookie) : getJwtFromBearer(authHeader);
        String source = cookie != null ? "cookie" : "bearer";

        if (!isJwtValid(tokenProvider, jwtToken, source)) {
            // Log the invalid JWT token details to find out how it was created in case of accidental issues
            log.info("""
                    Invalid JWT token detected. Details:
                    {
                     "source": "{}",
                     "remote_ip": "{}",
                     "user_agent": "{}",
                     "request_uri": "{}",
                     "headers": {}
                    }
                    """, source, httpServletRequest.getRemoteAddr(), httpServletRequest.getHeader(HttpHeaders.USER_AGENT), httpServletRequest.getRequestURI(),
                    collectHeaders(httpServletRequest));
            return null;
        }

        return new JwtWithSource(jwtToken, source);
    }

    /**
     * Collects the headers of the request and formats them as a JSON-like string
     *
     * @param request the http request
     * @return the formatted headers
     */
    private static String collectHeaders(HttpServletRequest request) {
        List<String> headerEntries = new ArrayList<>();
        request.getHeaderNames().asIterator().forEachRemaining(headerName -> headerEntries.add(String.format("\"%s\": \"%s\"", headerName, request.getHeader(headerName))));
        return "[\n" + String.join(",\n", headerEntries) + "\n]";
    }

    /**
     * Extracts the jwt from the cookie
     *
     * @param jwtCookie the cookie with Key "jwt"
     * @return the jwt or null if not found
     */
    @Nullable
    private static String getJwtFromCookie(@Nullable Cookie jwtCookie) {
        if (jwtCookie == null) {
            return null;
        }
        return jwtCookie.getValue();
    }

    /**
     * Extracts the jwt from the Authorization header
     *
     * @param jwtBearer the content of the Authorization header
     * @return the jwt or null if not found
     */
    @Nullable
    private static String getJwtFromBearer(@Nullable String jwtBearer) {
        if (!StringUtils.hasText(jwtBearer) || !jwtBearer.startsWith(BEARER_PREFIX)) {
            return null;
        }

        String token = jwtBearer.substring(BEARER_PREFIX.length()).trim();
        return StringUtils.hasText(token) ? token : null;
    }

    /**
     * Checks if the jwt is valid
     *
     * @param tokenProvider used to generate and validate jwt's
     * @param jwtToken      which should be validated
     * @param source        of the jwt token
     * @return true if the jwt is valid, false if missing or invalid
     */
    private static boolean isJwtValid(TokenProvider tokenProvider, @Nullable String jwtToken, @Nullable String source) {
        return StringUtils.hasText(jwtToken) && tokenProvider.validateTokenForAuthority(jwtToken, source);
    }

    /**
     * Not all URIs need JWT authentication.
     *
     * <p>
     * For example, Git clones/pushes use an HTTP token mechanism.
     *
     * @param uri A URI relative to the Artemis base URL, i.e. without the {@code https://artemis.domain.com} part at the start.
     * @return True, if the URI does not use JWT authentication.
     */
    private static boolean isIgnoredUri(final String uri) {
        // /git/**: used by Git clients with a token mechanism
        // /api/iris/internal/** used by Pyris status callbacks with a token mechanism restricted to internal access
        // /api/programming/public/programming-exercises/new-result: used by Jenkins to send test results back to Artemis,
        // uses a separate secret token.
        return uri.startsWith("/git/") || uri.startsWith("/api/iris/internal/") || "/api/programming/public/programming-exercises/new-result".equals(uri);
    }
}
