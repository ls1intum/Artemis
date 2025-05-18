package de.tum.cit.aet.artemis.core.security.jwt;

import java.io.IOException;
import java.util.Date;
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

    public static final String JWT_COOKIE_NAME = "jwt";

    private static final String AUTHORIZATION_HEADER = "Authorization";

    private static final String BEARER_PREFIX = "Bearer ";

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
     *
     * <h5>Rolling Token Mechanism</h5>
     * <ul>
     * <li><b>Maximum Lifetime:</b> The token's maximum lifetime is configurable via the property
     * {@code artemis.user-management.passkey.token-validity-in-seconds-for-passkey}.</li>
     * <li><b>Token Renewal:</b> The token is renewed if its remaining lifetime is below 50% of its validity period,
     * but only up to the maximum lifetime (default: 180 days). The lifetime of a renewed token is capped at
     * {@code jhipster.security.authentication.jwt.token-validity-in-seconds-for-remember-me} (default: 30 days).</li>
     * <li><b>IssuedAt (iat) Timestamp:</b> The original "issued at" timestamp remains unchanged during renewal.</li>
     * <li><b>Automatic Logout:</b> Users who are inactive for the duration of
     * {@code jhipster.security.authentication.jwt.token-validity-in-seconds-for-remember-me}
     * (default: 30 days) are automatically logged out.</li>
     * <li><b>Extended Login:</b> Active users can remain logged in for up to the duration of
     * {@code artemis.user-management.passkey.token-validity-in-seconds-for-passkey}.</li>
     * <li><b>Security:</b> The mechanism relies on secure cookie storage and HTTPS. Note that no token revocation
     * mechanism is implemented.</li>
     * </ul>
     *
     * @param jwtToken       The current JWT token to be evaluated for renewal.
     * @param authentication The {@link org.springframework.security.core.Authentication} object associated with the token.
     * @param response       The {@link jakarta.servlet.http.HttpServletResponse} where the renewed token will be added as a cookie.
     * @throws NotAuthorizedException If the token cannot be renewed due to validation or other issues.
     */
    private void rotateTokenSilently(String jwtToken, Authentication authentication, HttpServletResponse response) throws NotAuthorizedException {
        Date issuedAt = this.tokenProvider.getIssuedAtDate(jwtToken);
        Date expirationDate = this.tokenProvider.getExpirationDate(jwtToken);

        long currentTime = System.currentTimeMillis();
        long tokenValidityInMilliseconds = this.tokenProvider.getTokenValidity(true);
        long remainingLifetime = expirationDate.getTime() - currentTime;

        boolean isRemainingLifetimeBelowHalf = remainingLifetime < tokenValidityInMilliseconds / 2;
        if (isRemainingLifetimeBelowHalf) {
            long nowInMilliseconds = System.currentTimeMillis();
            long newTokenExpirationTimeInMilliseconds = Math.min(nowInMilliseconds + tokenValidityInMilliseconds,
                    issuedAt.getTime() + Math.multiplyExact(this.tokenValidityInSecondsForPasskey, 1000));
            long rotatedTokenDurationInMilliseconds = newTokenExpirationTimeInMilliseconds - nowInMilliseconds;

            String rotatedToken = this.tokenProvider.createToken(authentication, issuedAt, new Date(newTokenExpirationTimeInMilliseconds), this.tokenProvider.getTools(jwtToken),
                    true);

            ResponseCookie responseCookie = jwtCookieService.buildRotatedCookie(rotatedToken, rotatedTokenDurationInMilliseconds);

            response.addHeader(HttpHeaders.SET_COOKIE, responseCookie.toString());
        }
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        HttpServletRequest httpServletRequest = (HttpServletRequest) servletRequest;
        HttpServletResponse httpServletResponse = (HttpServletResponse) servletResponse;
        String jwtToken;
        String source;
        try {
            JwtWithSource jwtWithSource = Objects.requireNonNull(extractValidJwt(httpServletRequest, this.tokenProvider));
            jwtToken = jwtWithSource.jwt();
            source = jwtWithSource.source();
        }
        catch (IllegalArgumentException e) {
            httpServletResponse.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        if (jwtToken != null) {
            Authentication authentication = this.tokenProvider.getAuthentication(jwtToken);

            if (source.equals("cookie") && this.tokenProvider.getAuthenticatedWithPasskey(jwtToken)) {
                rotateTokenSilently(jwtToken, authentication, httpServletResponse);
            }

            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

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
        var cookie = WebUtils.getCookie(httpServletRequest, JWT_COOKIE_NAME);
        var authHeader = httpServletRequest.getHeader(AUTHORIZATION_HEADER);

        if (cookie == null && authHeader == null) {
            return null;
        }

        // TODO move the token rotation to this place as we can differentiate between bearer and cookie
        if (cookie != null && authHeader != null) {
            // Single Method Enforcement: Only one method of authentication is allowed
            throw new IllegalArgumentException("Multiple authentication methods detected: Both JWT cookie and Bearer token are present");
        }

        String jwtToken = cookie != null ? getJwtFromCookie(cookie) : getJwtFromBearer(authHeader);
        String source = cookie != null ? "cookie" : "bearer";

        if (!isJwtValid(tokenProvider, jwtToken, source)) {
            return null;
        }

        return new JwtWithSource(jwtToken, source);
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
}
