package de.tum.in.www1.artemis.security.jwt;

import java.io.IOException;

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
        Cookie jwtCookie = WebUtils.getCookie(httpServletRequest, JWT_COOKIE_NAME);

        String jwtToken;
        if (isJwtValid(this.tokenProvider, jwtToken = getJwtFromCookie(jwtCookie))
                || isJwtValid(this.tokenProvider, jwtToken = getJwtFromBearer(httpServletRequest.getHeader("Authorization")))) {
            Authentication authentication = this.tokenProvider.getAuthentication(jwtToken);
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        filterChain.doFilter(servletRequest, servletResponse);
    }

    public static String getJwtFromCookie(Cookie jwtCookie) {
        if (jwtCookie == null) {
            return null;
        }
        return jwtCookie.getValue();
    }

    private static String getJwtFromBearer(String jwtBearer) {
        if (!StringUtils.hasText(jwtBearer) || !jwtBearer.startsWith("Bearer ")) {
            return null;
        }

        return jwtBearer.split("Bearer ")[1];
    }

    /**
     * Checks if the jwt token is valid
     *
     * @param tokenProvider the artemis token provider used to generate and validate jwt's
     * @param jwtToken      the jwt token
     * @return true if the jwt is valid, false if missing or invalid
     */
    public static boolean isJwtValid(TokenProvider tokenProvider, String jwtToken) {
        return StringUtils.hasText(jwtToken) && tokenProvider.validateTokenForAuthority(jwtToken);
    }
}
