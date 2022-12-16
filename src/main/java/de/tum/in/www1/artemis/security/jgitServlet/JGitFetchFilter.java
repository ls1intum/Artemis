package de.tum.in.www1.artemis.security.jgitServlet;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.exception.LocalGitException;
import de.tum.in.www1.artemis.service.connectors.localgit.LocalGitRepositoryUrl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Filters incoming fetch requests reaching the jgitServlet at /git/*.
 */
public class JGitFetchFilter extends OncePerRequestFilter {

    private final Logger log = LoggerFactory.getLogger(JGitFetchFilter.class);

    public static final String AUTHORIZATION_HEADER = "Authorization";

    private final JGitFilterUtilService jGitFilterUtilService;


    public JGitFetchFilter(JGitFilterUtilService jGitFilterUtilService) {
        this.jGitFilterUtilService = jGitFilterUtilService;
    }

    @Override
    public void doFilterInternal(HttpServletRequest servletRequest, HttpServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {

        log.debug("Trying to fetch repository {}", servletRequest.getRequestURI());

        servletResponse.setHeader("WWW-Authenticate", "Basic");

        String basicAuthCredentials;
        try {
            basicAuthCredentials = jGitFilterUtilService.checkAuthorizationHeader(servletRequest.getHeader(AUTHORIZATION_HEADER));
        } catch (LocalGitAuthException ex) {
            servletResponse.setStatus(401);
            return;
        }

        String username = basicAuthCredentials.split(":")[0];
        String password = basicAuthCredentials.split(":")[1];

        User user;
        try {
            user = jGitFilterUtilService.authenticateUser(username, password);
        } catch (LocalGitAuthException ex) {
            servletResponse.setStatus(401);
            return;
        }


        String uri = servletRequest.getRequestURI();
        LocalGitRepositoryUrl localGitUrl;
        try {
            localGitUrl = jGitFilterUtilService.validateRepositoryUrl(uri);
        } catch (LocalGitException e) {
            servletResponse.setStatus(400);
            return;
        }

        String projectKey = localGitUrl.getProjectKey();
        String courseShortName = localGitUrl.getCourseShortName();
        String repositoryTypeOrUserName = localGitUrl.getRepositoryTypeOrUserName();

        Course course;

        try {
            course = jGitFilterUtilService.findCourseForRepository(courseShortName);
        } catch (LocalGitException e) {
            servletResponse.setStatus(404);
            return;
        }

        ProgrammingExercise exercise;

        try {
            exercise = jGitFilterUtilService.findExerciseForRepository(projectKey);
        } catch (LocalGitException e) {
            servletResponse.setStatus(404);
            return;
        }

        try {
            jGitFilterUtilService.authorizeUser(repositoryTypeOrUserName, course, exercise, user);
        } catch (LocalGitAuthException e) {
            servletResponse.setStatus(401);
            return;
        }

        // Get exercise with participations (ProgrammingExerciseRepository or ParticipationRepository?)

        filterChain.doFilter(servletRequest, servletResponse);
    }
}
