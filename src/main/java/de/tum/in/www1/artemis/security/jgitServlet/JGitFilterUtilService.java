package de.tum.in.www1.artemis.security.jgitServlet;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.RepositoryType;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.exception.LocalGitException;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.StudentParticipationRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.connectors.localgit.LocalGitRepositoryUrl;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.chrono.ChronoZonedDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

@Service
public class JGitFilterUtilService {

    private final Logger log = LoggerFactory.getLogger(JGitFilterUtilService.class);

    private final AuthenticationManagerBuilder authenticationManagerBuilder;

    private final UserRepository userRepository;

    private final CourseRepository courseRepository;

    private final AuthorizationCheckService authorizationCheckService;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final StudentParticipationRepository studentParticipationRepository;

    public JGitFilterUtilService(AuthenticationManagerBuilder authenticationManagerBuilder, UserRepository userRepository, CourseRepository courseRepository, AuthorizationCheckService authorizationCheckService, ProgrammingExerciseRepository programmingExerciseRepository, StudentParticipationRepository studentParticipationRepository) {
        this.authenticationManagerBuilder = authenticationManagerBuilder;
        this.userRepository = userRepository;
        this.courseRepository = courseRepository;
        this.authorizationCheckService = authorizationCheckService;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.studentParticipationRepository = studentParticipationRepository;
    }

    public String checkAuthorizationHeader(String authorizationHeader) throws LocalGitAuthException {
        if (authorizationHeader == null) {
            throw new LocalGitAuthException();
        }

        String[] basicAuthCredentialsEncoded = authorizationHeader.split(" ");

        if (!basicAuthCredentialsEncoded[0].equals("Basic")) {
            throw new LocalGitAuthException();
        }

        // Return decoded basic auth credentials which contain the username and the password.
        return new String(Base64.getDecoder().decode(basicAuthCredentialsEncoded[1]));
    }

    public User authenticateUser(String username, String password) throws LocalGitAuthException {
        // TODO: Remove!
        log.debug("Found user with login {} and password {} in fetch request.", username, password);

        try {
            SecurityUtils.checkUsernameAndPasswordValidity(username, password);

            // Try to authenticate the user.
            UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(username, password);
            authenticationManagerBuilder.getObject().authenticate(authenticationToken);
        } catch (AccessForbiddenException | AuthenticationException ex) {
            throw new LocalGitAuthException();
        }

        User user = userRepository.findOneByLogin(username).orElse(null);

        // Check that the user exists.
        if (user == null) {
            throw new LocalGitAuthException();
        }

        return user;
    }

    public LocalGitRepositoryUrl validateRepositoryUrl(String url) throws LocalGitException {

        URI uri;

        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            throw new LocalGitException("Badly formed URI.", e);
        }

        if (!uri.getScheme().equals("http")) {
            throw new LocalGitException("Bad scheme.");
        }

        String path = uri.getPath();

        String[] pathSplit = path.split("/");

        // Should have 5 elements, start with '/git', and end with '.git'.
        if (pathSplit.length != 5 || !pathSplit[1].equals("git") || !(pathSplit[4].endsWith(".git"))) {
            throw new LocalGitException("Invalid URL.");
        }

        String repositorySlug = pathSplit[4].replace(".git", "");

        LocalGitRepositoryUrl localGitRepo = new LocalGitRepositoryUrl(pathSplit[3], pathSplit[2], repositorySlug);

        // Project key should contain the course short name.
        if (!localGitRepo.getProjectKey().toLowerCase().contains(localGitRepo.getCourseShortName().toLowerCase())) {
            throw new LocalGitException("Badly formed Local Git URI: " + path + " Expected the repository name to start with the lower case course short name.");
        }

        return localGitRepo;
    }

    public Course findCourseForRepository(String courseShortName) throws LocalGitException {
        List<Course> courses = courseRepository.findAllByShortName(courseShortName);

        if (courses.size() != 1) {
            if (courses.size() > 1) {
                log.error("Multiple courses found for short name {}.", courseShortName);
            }
            throw new LocalGitException("No unique course found for the given course short name.");
        }

        return courses.get(0);
    }

    public ProgrammingExercise findExerciseForRepository(String projectKey) throws LocalGitException {
        List<ProgrammingExercise> exercises = programmingExerciseRepository.findByProjectKey(projectKey);
        if (exercises.size() != 1) {
            throw new LocalGitException();
        }
        return exercises.get(0);
    }

    public void authorizeUser(String repositoryTypeOrUserName, Course course, ProgrammingExercise exercise, User user) throws LocalGitAuthException {


        if (isRequestingBaseRepository(repositoryTypeOrUserName)) {
            // ---- Requesting one of the base repositories ("exercise", "tests", or "solution") ----
            // Check that the user is at least an instructor in the course the repository belongs to.
            boolean isAtLeastInstructorInCourse = authorizationCheckService.isAtLeastInstructorInCourse(course, user);
            if (!isAtLeastInstructorInCourse) {
                throw new LocalGitAuthException();
            }
        } else {
            // ---- Requesting one of the participant repositories ----

            // Check that the user name in the repository name corresponds to the user name used for Basic Auth.
            if (!user.getLogin().equals(repositoryTypeOrUserName)) {
                throw new LocalGitAuthException();
            }

            // Check that the user is at least a student in the course.
            boolean isAtLeastStudentInCourse = authorizationCheckService.isAtLeastStudentInCourse(course, user);
            if (!isAtLeastStudentInCourse) {
                throw new LocalGitAuthException();
            }

            // Check that the user participates in the exercise the repository belongs to.
            Optional<StudentParticipation> participation = studentParticipationRepository.findByExerciseIdAndStudentLogin(exercise.getId(), user.getLogin());

            if (participation.isEmpty()) throw new LocalGitAuthException();

            // Check that the exercise's Release Date is either not set or is in the past.
            if (exercise.getReleaseDate() != null && exercise.getReleaseDate().isAfter(ZonedDateTime.now())) {
                throw new LocalGitAuthException();
            }

            // For Push, Fetching  is alway allowed, independent of the due date.
            // Check that the exercise's Due Date is either not set or is in the future.
            // Students can still commit code and receive feedback after the exercise due date, if manual review and complaints are not activated. The results for these submissions will not be rated.
        }
    }

    private boolean isRequestingBaseRepository(String requestedRepositoryType) {
        for (RepositoryType repositoryType : RepositoryType.values()) {
            if (repositoryType.toString().equals(requestedRepositoryType)) return true;
        }
        return false;
    }

}
