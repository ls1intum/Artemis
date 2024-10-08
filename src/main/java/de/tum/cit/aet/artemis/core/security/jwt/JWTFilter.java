package de.tum.cit.aet.artemis.core.security.jwt;

import java.io.IOException;

import jakarta.annotation.Nullable;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

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

    private final TokenProvider tokenProvider;

    public JWTFilter(TokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        HttpServletRequest httpServletRequest = (HttpServletRequest) servletRequest;

        String jwtToken = extractValidJwt(httpServletRequest, this.tokenProvider);
        if (jwtToken != null) {
            Authentication authentication = this.tokenProvider.getAuthentication(jwtToken);
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        filterChain.doFilter(servletRequest, servletResponse);
    }

    /**
     * Extracts the first valid jwt found in the cookie or the Authorization header
     *
     * @param httpServletRequest the http request
     * @param tokenProvider      the Artemis token provider used to generate and validate jwt's
     * @return the valid jwt or null if not found or invalid
     */
    public static @Nullable String extractValidJwt(HttpServletRequest httpServletRequest, TokenProvider tokenProvider) {
        String jwtToken = getJwtFromCookie(WebUtils.getCookie(httpServletRequest, JWT_COOKIE_NAME));
        if (isJwtValid(tokenProvider, jwtToken)) {
            return jwtToken;
        }
        jwtToken = getJwtFromBearer(httpServletRequest.getHeader("Authorization"));
        if (isJwtValid(tokenProvider, jwtToken)) {
            return jwtToken;
        }
        return null;
    }

    /**
     * Extracts the jwt from the cookie
     *
     * @param jwtCookie the cookie with Key "jwt"
     * @return the jwt or null if not found
     */
    private static @Nullable String getJwtFromCookie(Cookie jwtCookie) {
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
    private static @Nullable String getJwtFromBearer(String jwtBearer) {
        if (!StringUtils.hasText(jwtBearer) || !jwtBearer.startsWith("Bearer ")) {
            return null;
        }

        return jwtBearer.substring(7).trim();
    }

    /**
     * Checks if the jwt is valid
     *
     * @param tokenProvider the Artemis token provider used to generate and validate jwt's
     * @param jwtToken      the jwt
     * @return true if the jwt is valid, false if missing or invalid
     */
    private static boolean isJwtValid(TokenProvider tokenProvider, String jwtToken) {
        return StringUtils.hasText(jwtToken) && tokenProvider.validateTokenForAuthority(jwtToken);
    }
}
