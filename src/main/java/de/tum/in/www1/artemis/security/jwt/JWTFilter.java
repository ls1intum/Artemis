package de.tum.in.www1.artemis.security.jwt;

import java.io.IOException;
import java.time.Duration;

import javax.servlet.*;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;

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

    private final TokenProvider tokenProvider;

    public JWTFilter(TokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        HttpServletRequest httpServletRequest = (HttpServletRequest) servletRequest;
        Cookie jwtCookie = WebUtils.getCookie(httpServletRequest, JWT_COOKIE_NAME);
        if (jwtCookie != null) {
            String jwt = jwtCookie.getValue();
            if (StringUtils.hasText(jwt) && this.tokenProvider.validateTokenForAuthority(jwt)) {
                Authentication authentication = this.tokenProvider.getAuthentication(jwt);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }
        filterChain.doFilter(servletRequest, servletResponse);
    }

    /**
     * Builds the cookie containing the jwt
     * @param jwt      the token that will be used as the cookie's value
     * @param duration the validity of the cookie
     * @param isSecure true if the cookie should be secure
     * @return         the response cookie that should be set containing the jwt
     */
    public static ResponseCookie buildJWTCookie(String jwt, Duration duration, boolean isSecure) {

        return ResponseCookie.from(JWT_COOKIE_NAME, jwt).httpOnly(true) // Must be httpOnly
                .sameSite("Lax") // Must be Lax to allow navigation links to Artemis to work
                .secure(isSecure) // Should only be secure when using https, otherwise in environments using http (cypress) browsers don't set the cookie
                .path("/") // Must be "/" to be sent in ALL request
                .maxAge(duration) // Duration should match the duration of the jwt
                .build(); // Build cookie
    }
}
