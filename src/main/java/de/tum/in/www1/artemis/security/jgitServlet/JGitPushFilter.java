package de.tum.in.www1.artemis.security.jgitServlet;

import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

/**
 * Filters incoming requests and installs a Spring Security principal if a header corresponding to a valid user is found.
 */
@Component
public class JGitPushFilter implements Filter {

    private final Logger log = LoggerFactory.getLogger(JGitPushFilter.class);

    private final AuthorizationCheckService authorizationCheckService;

    public JGitPushFilter(AuthorizationCheckService authorizationCheckService) {
        this.authorizationCheckService = authorizationCheckService;
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        HttpServletRequest httpServletRequest = (HttpServletRequest) servletRequest;
        log.debug("httpServletRequest when pushing. Authorization header: {}", httpServletRequest.getHeader("Authorization"));

        filterChain.doFilter(servletRequest, servletResponse);
    }
}
