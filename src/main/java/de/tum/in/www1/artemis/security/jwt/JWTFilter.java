package de.tum.in.www1.artemis.security.jwt;

import java.io.IOException;

import javax.servlet.*;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;

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
        if (isJwtCookieValid(this.tokenProvider, jwtCookie)) {
            Authentication authentication = this.tokenProvider.getAuthentication(jwtCookie.getValue());
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
        filterChain.doFilter(servletRequest, servletResponse);
    }

    public static boolean isJwtCookieValid(TokenProvider tokenProvider, Cookie jwtCookie) {
        if (jwtCookie == null) {
            return false;
        }
        String jwt = jwtCookie.getValue();
        return StringUtils.hasText(jwt) && tokenProvider.validateTokenForAuthority(jwt);
    }
}
