package de.tum.in.www1.artemis.security.jgitServlet;

import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

/**
 * Filters incoming requests and installs a Spring Security principal if a header corresponding to a valid user is found.
 */
@Component
public class JGitPushFilter extends OncePerRequestFilter {

    private final Logger log = LoggerFactory.getLogger(JGitPushFilter.class);

    private final AuthorizationCheckService authorizationCheckService;

    public JGitPushFilter(AuthorizationCheckService authorizationCheckService) {
        this.authorizationCheckService = authorizationCheckService;
    }


    public void doFilterInternal(HttpServletRequest servletRequest, HttpServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {

        Map<String, String> map = new HashMap<String, String>();
        Enumeration headerNames = servletRequest.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String key = (String) headerNames.nextElement();
            String value = servletRequest.getHeader(key);
            map.put("header:" + key, value);
        }
        map.put("getRemoteUser", servletRequest.getRemoteUser());
        map.put("getMethod", servletRequest.getMethod());
        map.put("getQueryString", servletRequest.getQueryString());
        map.put("getAuthType", servletRequest.getAuthType());
        map.put("getContextPath", servletRequest.getContextPath());
        map.put("getPathInfo", servletRequest.getPathInfo());
        map.put("getPathTranslated", servletRequest.getPathTranslated());
        map.put("getRequestedSessionId", servletRequest.getRequestedSessionId());
        map.put("getRequestURI", servletRequest.getRequestURI());
        map.put("getRequestURL", servletRequest.getRequestURL().toString());
        map.put("getMethod", servletRequest.getMethod());
        map.put("getServletPath", servletRequest.getServletPath());
        map.put("getContentType", servletRequest.getContentType());
        map.put("getLocalName", servletRequest.getLocalName());
        map.put("getProtocol", servletRequest.getProtocol());
        map.put("getRemoteAddr", servletRequest.getRemoteAddr());
        map.put("getServerName", servletRequest.getServerName());

        log.debug("httpServletRequest when pushing. {}", map);

        if (servletRequest.getHeader("Authorization") == null) {
            servletResponse.setStatus(401);
            return;
        }



        filterChain.doFilter(servletRequest, servletResponse);
    }
}
