package de.tum.in.www1.artemis.security.jgitServlet;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.RepositoryType;
import de.tum.in.www1.artemis.exception.LocalGitException;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.util.Pair;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Filters incoming fetch requests reaching the jgitServlet at /git/*.
 */
public class JGitFetchFilter extends OncePerRequestFilter {

    private final Logger log = LoggerFactory.getLogger(JGitFetchFilter.class);

    public static final String AUTHORIZATION_HEADER = "Authorization";

    private final UserRepository userRepository;

    private final UserDetailsService userDetailsService;

    private final CourseRepository courseRepository;

    private final AuthorizationCheckService authorizationCheckService;

    private final ExerciseRepository exerciseRepository;

    private final AuthenticationManagerBuilder authenticationManagerBuilder;

    public JGitFetchFilter(UserRepository userRepository, UserDetailsService userDetailsService, CourseRepository courseRepository, AuthorizationCheckService authorizationCheckService, ExerciseRepository exerciseRepository, AuthenticationManagerBuilder authenticationManagerBuilder) {
        this.userRepository = userRepository;
        this.userDetailsService = userDetailsService;
        this.courseRepository = courseRepository;
        this.authorizationCheckService = authorizationCheckService;
        this.exerciseRepository = exerciseRepository;
        this.authenticationManagerBuilder = authenticationManagerBuilder;
    }

    @Override
    public void doFilterInternal(HttpServletRequest servletRequest, HttpServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {

        log.debug("Trying to fetch repository {}", servletRequest.getRequestURI());

        servletResponse.setHeader("WWW-Authenticate", "Basic");

        if (servletRequest.getHeader(AUTHORIZATION_HEADER) == null) {
            servletResponse.setStatus(401);
            return;
        }

        String[] basicAuthCredentialsEncoded = servletRequest.getHeader(AUTHORIZATION_HEADER).split(" ");
        if (!basicAuthCredentialsEncoded[0].equals("Basic")) {
            servletResponse.setStatus(401);
            return;
        }

        String basicAuthCredentials = new String(Base64.getDecoder().decode(basicAuthCredentialsEncoded[1]));
        String username = basicAuthCredentials.split(":")[0];
        String password = basicAuthCredentials.split(":")[1];

        // TODO: Remove!
        log.debug("Found user with login {} and password {} in fetch request.", username, password);

        try {
            SecurityUtils.checkUsernameAndPasswordValidity(username, password);
        } catch (AccessForbiddenException e) {
            servletResponse.setStatus(401);
        }

        // Try to authenticate the user.
        UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(username, password);
        try {
            // authenticationToken.setDetails(Pair.of("userAgent", userAgent));
            Authentication authentication = authenticationManagerBuilder.getObject().authenticate(authenticationToken);
        } catch (Exception e) {
            log.error(e.getMessage());
        }

        User user = userRepository.findOneByLogin(username).orElse(null);

        // Check that the user exists.
        // if (user == null) {
        //   servletResponse.setStatus(401);
        // return;
        //}

        // Check that the user's password is correct.
        //log.debug("user.getPassword(): {}", user.getPassword());
        //try {
        //   log.debug("userDetails: {}", userDetailsService.loadUserByUsername(username).getPassword());
        //} catch (UsernameNotFoundException e) {
        //  log.debug(e.getMessage());
        // }

        String[] uri = servletRequest.getRequestURI().split("/");

        try {
            validateLocalGitUri(uri);
        } catch (LocalGitException e) {
            log.error(e.getMessage());
            servletResponse.setStatus(400);
            return;
        }

        String projectKey = uri[1].toLowerCase();
        String repositoryName = uri[2];
        String repositoryTypeOrUserName = getRepositoryTypeOrUserName(projectKey, repositoryName);

        // TODO: Find a way to save the course short name with the repository (e.g. as part of the URL or in the config)
        // List<Course> courses = courseRepository.findByShortName(...)
        List<Course> courses = courseRepository.findAll();

        List<Course> coursesFiltered = new ArrayList<>();

        for (Course course : courses) {
            if (projectKey.startsWith(course.getShortName())) {
                coursesFiltered.add(course);
            }
        }

        if (coursesFiltered.size() != 1) {
            if (coursesFiltered.size() == 0) {
                // RepositoryNotFoundException wird auch in RepositoryResolver geworfen
                // -> checken in welcher Reihenfolge geprüft wird und ob das hier überhaupt notwendig ist.
                log.error("No course found for the specified projectKey {}.", projectKey);
                servletResponse.setStatus(404);
                return;
            }
            log.error("Multiple courses found for projectKey {}.", projectKey);
            servletResponse.setStatus(500);
            return;
        }

        Course course = coursesFiltered.get(0);

        // ---- Requesting one of the base repositories ("exercise", "tests", or "solution") ----
        if (isRequestingBaseRepository(repositoryTypeOrUserName)) {
            // Check that the user is at least an instructor in the course the repository belongs to.
            boolean isAtLeastInstructorInCourse = authorizationCheckService.isAtLeastInstructorInCourse(course, user);
            if (!isAtLeastInstructorInCourse) {
                servletResponse.setStatus(401);
                return;
            }
        }

        // ---- Requesting one of the participant repositories ----

        // Check that the user name in the repository name corresponds to the user name used for Basic Auth.
        if (!username.equals(repositoryTypeOrUserName)) {
            servletResponse.setStatus(401);
            return;
        }

        // Check that the user is at least a student in the course.
        boolean isAtLeastStudentInCourse = authorizationCheckService.isAtLeastStudentInCourse(course, user);
        if (!isAtLeastStudentInCourse) {
            servletResponse.setStatus(401);
            return;
        }

        // Check that the user participates in the exercise the repository belongs to.
        String exerciseShortName = projectKey.replace(course.getShortName(), "");

        // Get exercise with participations (ProgrammingExerciseRepository or ParticipationRepository?)

        // Check that the exercise's Release Date is either not set or is in the past.
        // Check that the exercise's Due Date is either not set or is in the future.

        filterChain.doFilter(servletRequest, servletResponse);
    }

    private void validateLocalGitUri(String[] uri) throws LocalGitException {
        if (!uri[2].startsWith(uri[1].toLowerCase())) {
            throw new LocalGitException("Badly formed Local Git URI: " + String.join("/", uri) + " Expected the repository name to start with the lower case course short name.");
        }
    }

    // Recht abhängig von der Art und Weise wie die Repositories beim Erstellen benannt werden.
    // Eventuell lässt sich das noch schön auslagern.
    private String getRepositoryTypeOrUserName(String projectKey, String repositoryName) {
        return repositoryName.replace(projectKey + "-", "");
    }

    private boolean isRequestingBaseRepository(String requestedRepositoryType) {
        for (RepositoryType repositoryType : RepositoryType.values()) {
            if (repositoryType.toString().equals(requestedRepositoryType)) return true;
        }
        return false;
    }
}
