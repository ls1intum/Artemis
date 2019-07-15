package de.tum.in.www1.artemis.security;

import java.io.IOException;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;

import org.springframework.web.filter.GenericFilterBean;

import de.tum.in.www1.artemis.security.jwt.TokenProvider;

/**
 * Filters incoming requests for a custom user name in the url request parameter. This custom user name can be used as a replacement for the actually logged in user to get API
 * results for the given user
 */
public class CustomStudentFilter extends GenericFilterBean {

    public static final String AUTHORIZATION_HEADER = "Authorization";

    public static final String AUTHORIZATION_TOKEN = "access_token";

    private TokenProvider tokenProvider;

    public CustomStudentFilter() {
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        HttpServletRequest httpServletRequest = (HttpServletRequest) servletRequest;
        String customStudentName = resolveStudentName(httpServletRequest);
        httpServletRequest.getSession().setAttribute("customUser", customStudentName);
        filterChain.doFilter(servletRequest, servletResponse);
    }

    private String resolveStudentName(HttpServletRequest request) {
        return request.getParameter("userId");

    }
}
