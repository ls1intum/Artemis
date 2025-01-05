package de.tum.cit.aet.artemis.core.security.jwt;

import java.io.IOException;

import jakarta.annotation.Nullable;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

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

    public JWTFilter(TokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        HttpServletRequest httpServletRequest = (HttpServletRequest) servletRequest;
        HttpServletResponse httpServletResponse = (HttpServletResponse) servletResponse;
        String jwtToken;
        try {
            jwtToken = extractValidJwt(httpServletRequest, this.tokenProvider);
        }
        catch (IllegalArgumentException e) {
            httpServletResponse.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        if (jwtToken != null) {
            Authentication authentication = this.tokenProvider.getAuthentication(jwtToken);
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
    public static String extractValidJwt(HttpServletRequest httpServletRequest, TokenProvider tokenProvider) {
        var cookie = WebUtils.getCookie(httpServletRequest, JWT_COOKIE_NAME);
        var authHeader = httpServletRequest.getHeader(AUTHORIZATION_HEADER);

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
            return null;
        }

        return jwtToken;
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
     * @param tokenProvider the Artemis token provider used to generate and validate jwt's
     * @param jwtToken      the jwt token which should be validated
     * @param source        the source of the jwt token
     * @return true if the jwt is valid, false if missing or invalid
     */
    private static boolean isJwtValid(TokenProvider tokenProvider, @Nullable String jwtToken, @Nullable String source) {
        return StringUtils.hasText(jwtToken) && tokenProvider.validateTokenForAuthority(jwtToken, source);
    }
}
