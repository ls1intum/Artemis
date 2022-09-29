package de.tum.in.www1.artemis.security.lti;

import java.io.IOException;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Redirects a LTI 1.3 Authorization Request Response to another endpoint.
 *
 */
public class Lti13RedirectProxyFilter extends OncePerRequestFilter {

    private AntPathRequestMatcher requestMatcher;

    private String redirectTargetUri;

    private Logger log = LoggerFactory.getLogger(Lti13RedirectProxyFilter.class);

    public Lti13RedirectProxyFilter(String redirectProxyUri, String redirectTargetUri) {
        requestMatcher = new AntPathRequestMatcher(redirectProxyUri);
        this.redirectTargetUri = redirectTargetUri;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        if (!this.requestMatcher.matches(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String state = request.getParameter("state");
        if (state == null) {
            errorOnMissingParameter(response, "state");
            return;
        }

        String idToken = request.getParameter("id_token");
        if (idToken == null) {
            errorOnMissingParameter(response, "id_token");
            return;
        }

        String redirectUri = this.redirectTargetUri + "?state=" + state + "&id_token=" + idToken;
        response.sendRedirect(redirectUri);
    }

    private void errorOnMissingParameter(HttpServletResponse response, String missingParamName) throws IOException {
        String message = "Missing parameter on oauth2 authorization response: " + missingParamName;
        log.error(message);
        response.sendError(HttpStatus.INTERNAL_SERVER_ERROR.value(), message);
    }
}
