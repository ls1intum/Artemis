package de.tum.in.www1.artemis.security.jgitServlet;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.exception.LocalGitException;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.connectors.localgit.LocalGitRepositoryUrl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Base64;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

/**
 * Filters incoming push requests reaching the jgitServlet at /git/*.
 */
public class JGitPushFilter extends OncePerRequestFilter {

    private final Logger log = LoggerFactory.getLogger(JGitPushFilter.class);

    private final JGitFilterUtilService jGitFilterUtilService;

    public JGitPushFilter(JGitFilterUtilService jGitFilterUtilService) {
        this.jGitFilterUtilService = jGitFilterUtilService;
    }

    @Override
    public void doFilterInternal(HttpServletRequest servletRequest, HttpServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {

        log.debug("Trying to push to repository {}", servletRequest.getRequestURI());

        servletResponse.setHeader("WWW-Authenticate", "Basic");

        try {
            jGitFilterUtilService.authenticateAndAuthorizeGitRequest(servletRequest, true);
        } catch (LocalGitAuthException e) {
            servletResponse.setStatus(401);
            return;
        } catch (LocalGitBadRequestException e) {
            servletResponse.setStatus(400);
            return;
        } catch (LocalGitNotFoundException e) {
            servletResponse.setStatus(404);
            return;
        } catch (LocalGitInternalException e) {
            servletResponse.setStatus(500);
            return;
        }

        filterChain.doFilter(servletRequest, servletResponse);

    }
}
