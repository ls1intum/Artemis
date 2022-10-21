package de.tum.in.www1.artemis.security.jgitServlet;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Base64;

/**
 * Filters incoming fetch requests reaching the jgitServlet at /git/*.
 */
public class JGitFetchFilter extends OncePerRequestFilter {

    private final Logger log = LoggerFactory.getLogger(JGitFetchFilter.class);

    public static final String AUTHORIZATION_HEADER = "Authorization";

    private final UserRepository userRepository;

    private final AuthorizationCheckService authorizationCheckService;

    public JGitFetchFilter(UserRepository userRepository, AuthorizationCheckService authorizationCheckService) {
        this.userRepository = userRepository;
        this.authorizationCheckService = authorizationCheckService;
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
        String login = basicAuthCredentials.split(":")[0];
        String password = basicAuthCredentials.split(":")[1];

        log.debug("Found user with login {} and password {} in fetch request.", login, password);

        // Zum testen wird hier erstmal einfach der Nutzer gefetcht, ohne das Passwort zu prüfen. Eventuell muss man das später über Spring-Security machen.
        User user = userRepository.findOneByLogin(login).orElse(null);

        if (user == null) {
            servletResponse.setStatus(401);
            return;
        }


        //Course course = exercise.getCourseViaExerciseGroupOrCourseMember();
        //boolean hasPermissions = authCheckService.isAtLeastTeachingAssistantInCourse(course, user);



        filterChain.doFilter(servletRequest, servletResponse);
    }
}
