package de.tum.in.www1.artemis.security.localVC;

import java.net.URL;
import java.time.ZonedDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import javax.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.RepositoryType;
import de.tum.in.www1.artemis.domain.participation.*;
import de.tum.in.www1.artemis.exception.LocalVCException;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.connectors.localvc.LocalVCRepositoryUrl;
import de.tum.in.www1.artemis.service.exam.ExamSubmissionService;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;

@Service
@Profile("localvc")
public class LocalVCFilterUtilService {

    private final Logger log = LoggerFactory.getLogger(LocalVCFilterUtilService.class);

    @Value("${artemis.version-control.url}")
    private URL localVCServerUrl;

    private final AuthenticationManagerBuilder authenticationManagerBuilder;

    private final UserRepository userRepository;

    private final CourseRepository courseRepository;

    private final AuthorizationCheckService authorizationCheckService;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final TemplateProgrammingExerciseParticipationRepository templateProgrammingExerciseParticipationRepository;

    private final SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository;

    private final ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository;

    private final ExamSubmissionService examSubmissionService;

    private final TeamRepository teamRepository;

    public static final String AUTHORIZATION_HEADER = "Authorization";

    public LocalVCFilterUtilService(AuthenticationManagerBuilder authenticationManagerBuilder, UserRepository userRepository, CourseRepository courseRepository,
            AuthorizationCheckService authorizationCheckService, ProgrammingExerciseRepository programmingExerciseRepository,
            TemplateProgrammingExerciseParticipationRepository templateProgrammingExerciseParticipationRepository,
            SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository,
            ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository, TeamRepository teamRepository,
            ExamSubmissionService examSubmissionService) {
        this.authenticationManagerBuilder = authenticationManagerBuilder;
        this.userRepository = userRepository;
        this.courseRepository = courseRepository;
        this.authorizationCheckService = authorizationCheckService;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.templateProgrammingExerciseParticipationRepository = templateProgrammingExerciseParticipationRepository;
        this.solutionProgrammingExerciseParticipationRepository = solutionProgrammingExerciseParticipationRepository;
        this.programmingExerciseStudentParticipationRepository = programmingExerciseStudentParticipationRepository;
        this.examSubmissionService = examSubmissionService;
        this.teamRepository = teamRepository;
    }

    /**
     * @param servletRequest The object containing all information about the incoming request.
     * @param forPush        Whether the method should authenticate a fetch or a push request. For a push request, additional checks are conducted.
     * @throws LocalVCAuthException For when the user cannot be authenticated or is not authorized to access the repository.
     */
    public void authenticateAndAuthorizeGitRequest(HttpServletRequest servletRequest, boolean forPush) throws LocalVCAuthException {

        String basicAuthCredentials = checkAuthorizationHeader(servletRequest.getHeader(LocalVCFilterUtilService.AUTHORIZATION_HEADER));

        String username = basicAuthCredentials.split(":")[0];
        String password = basicAuthCredentials.split(":")[1];

        User user = authenticateUser(username, password);

        String url = servletRequest.getRequestURL().toString();
        LocalVCRepositoryUrl localVCUrl = validateRepositoryUrl(url);

        String projectKey = localVCUrl.getProjectKey();
        String courseShortName = localVCUrl.getCourseShortName();
        String repositoryTypeOrUserName = localVCUrl.getRepositoryTypeOrUserName();

        Course course = findCourseForRepository(courseShortName);

        ProgrammingExercise exercise = findExerciseForRepository(projectKey);

        authorizeUser(repositoryTypeOrUserName, course, exercise, user, forPush);

        if (forPush) {
            log.debug("Notifying Artemis about a new push.");
            // TODO: Add Webhooks -> notifies Artemis on Push
        }
    }

    private String checkAuthorizationHeader(String authorizationHeader) throws LocalVCAuthException {
        if (authorizationHeader == null) {
            throw new LocalVCAuthException();
        }

        String[] basicAuthCredentialsEncoded = authorizationHeader.split(" ");

        if (!basicAuthCredentialsEncoded[0].equals("Basic")) {
            throw new LocalVCAuthException();
        }

        // Return decoded basic auth credentials which contain the username and the password.
        return new String(Base64.getDecoder().decode(basicAuthCredentialsEncoded[1]));
    }

    private User authenticateUser(String username, String password) throws LocalVCAuthException {
        try {
            SecurityUtils.checkUsernameAndPasswordValidity(username, password);

            // Try to authenticate the user.
            UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(username, password);
            authenticationManagerBuilder.getObject().authenticate(authenticationToken);
        }
        catch (AccessForbiddenException | AuthenticationException ex) {
            throw new LocalVCAuthException();
        }

        User user = userRepository.findOneByLogin(username).orElse(null);

        // Check that the user exists.
        if (user == null) {
            throw new LocalVCAuthException();
        }

        return user;
    }

    private LocalVCRepositoryUrl validateRepositoryUrl(String url) throws LocalVCBadRequestException {

        LocalVCRepositoryUrl localVCRepositoryUrl;

        try {
            localVCRepositoryUrl = new LocalVCRepositoryUrl(localVCServerUrl, url);
        }
        catch (LocalVCException e) {
            throw new LocalVCBadRequestException("Badly formed Local Git URI: " + url, e);
        }

        return localVCRepositoryUrl;
    }

    private Course findCourseForRepository(String courseShortName) throws LocalVCNotFoundException, LocalVCInternalException {
        List<Course> courses = courseRepository.findAllByShortName(courseShortName);
        if (courses.size() != 1) {
            if (courses.size() == 0) {
                throw new LocalVCNotFoundException("No course found for the given short name: " + courseShortName);
            }
            else {
                throw new LocalVCInternalException("Multiple courses found for the given short name: " + courseShortName);
            }
        }
        return courses.get(0);
    }

    private ProgrammingExercise findExerciseForRepository(String projectKey) throws LocalVCNotFoundException, LocalVCInternalException {
        List<ProgrammingExercise> exercises = programmingExerciseRepository.findByProjectKey(projectKey);
        if (exercises.size() != 1) {
            if (exercises.size() == 0) {
                throw new LocalVCNotFoundException("No exercise found for the given project key: " + projectKey);
            }
            else {
                throw new LocalVCInternalException("Multiple exercises found for the given project key: " + projectKey);
            }
        }
        return exercises.get(0);
    }

    private void authorizeUser(String repositoryTypeOrUserName, Course course, ProgrammingExercise exercise, User user, boolean forPush) throws LocalVCAuthException {
        if (isRequestingBaseRepository(repositoryTypeOrUserName)) {
            // ---- Requesting one of the base repositories ("exercise", "tests", or "solution") ----
            if (!isAuthorizedToAccessBaseRepository(course, user, repositoryTypeOrUserName, exercise)) {
                throw new LocalVCAuthException();
            }
            return;
        }

        // ---- Requesting one of the participant repositories. ----

        // Check that offline IDE usage is allowed.
        if (Boolean.FALSE.equals(exercise.isAllowOfflineIde())) {
            throw new LocalVCAuthException();
        }

        // Check if the repository URL corresponds to a team.
        Team team = findValidTeam(course.getId(), repositoryTypeOrUserName, user);

        // Check that the user name in the repository name corresponds to the user name used for Basic Auth.
        if (team == null && !user.getLogin().equals(repositoryTypeOrUserName)) {
            throw new LocalVCAuthException();
        }

        String userIdentifier = team != null ? team.getShortName() : user.getLogin();

        if (exercise.isExamExercise()) {
            if (!isAllowedForExamExercise(forPush, exercise, user)) {
                throw new LocalVCAuthException();
            }
        }
        else {
            if (!isAllowedForCourseExercise(exercise, userIdentifier, forPush)) {
                throw new LocalVCAuthException();
            }
        }
    }

    private boolean isRequestingBaseRepository(String requestedRepositoryType) {
        for (RepositoryType repositoryType : RepositoryType.values()) {
            if (repositoryType.toString().equals(requestedRepositoryType))
                return true;
        }
        return false;
    }

    private boolean isAuthorizedToAccessBaseRepository(Course course, User user, String repositoryType, Exercise exercise) {
        // Check that the user is at least an instructor in the course the repository belongs to.
        boolean isAtLeastInstructorInCourse = authorizationCheckService.isAtLeastInstructorInCourse(course, user);
        if (!isAtLeastInstructorInCourse) {
            return false;
        }

        // Check that there is a template participation or a solution participation for the exercise.
        if (repositoryType.equals("exercise")) {
            Optional<TemplateProgrammingExerciseParticipation> participation = templateProgrammingExerciseParticipationRepository.findByProgrammingExerciseId(exercise.getId());
            if (participation.isEmpty()) {
                return false;
            }
        }

        if (repositoryType.equals("solution")) {
            Optional<SolutionProgrammingExerciseParticipation> participation = solutionProgrammingExerciseParticipationRepository.findByProgrammingExerciseId(exercise.getId());
            if (participation.isEmpty()) {
                return false;
            }
        }

        return true;
    }

    private Team findValidTeam(Long courseId, String userNameFromUrl, User user) throws LocalVCInternalException, LocalVCAuthException {
        List<Team> teams = teamRepository.findAllByExerciseCourseIdAndShortName(courseId, userNameFromUrl);
        if (teams.size() > 0) {
            if (teams.size() > 1) {
                throw new LocalVCInternalException("Multiple teams with the same team short name for one exercise.");
            }

            // Check that the authenticated user is part of the team.
            Team team = teams.get(0);
            if (!team.hasStudent(user)) {
                throw new LocalVCAuthException();
            }
            return team;
        }
        return null;
    }

    private boolean isAllowedForExamExercise(boolean forPush, Exercise exercise, User user) {
        if (!forPush)
            return true;

        // ---- Additional checks for push request. ----
        // Check that the user is allowed to submit in case this is an exam exercise.
        return examSubmissionService.isAllowedToSubmitDuringExam(exercise, user, false);
    }

    private boolean isAllowedForCourseExercise(Exercise exercise, String userIdentifier, boolean forPush) {
        // Check that the user or team participates in the exercise the repository belongs to.
        Optional<ProgrammingExerciseStudentParticipation> participation = programmingExerciseStudentParticipationRepository.findByExerciseIdAndStudentLogin(exercise.getId(),
                userIdentifier);

        if (participation.isEmpty())
            return false;

        // Check that the exercise's Participation Start Date is either not set or is in the past.
        if (exercise.getParticipationStartDate() != null && exercise.getParticipationStartDate().isAfter(ZonedDateTime.now())) {
            return false;
        }
        if (!forPush)
            return true;

        // ---- Additional checks for push request. ----

        // Check that pushes are allowed for the authenticated user (i.e. they have write permission).
        // TODO

        // Check that the exercise's Due Date is either not set or is in the future.
        if (exercise.getDueDate() != null && exercise.getDueDate().isBefore(ZonedDateTime.now())) {
            // Students can still commit code and receive feedback after the exercise due date, if manual review and complaints are not activated.
            // Students can also commit code for test runs after the due date.
            // The results for these submissions will not be rated.
            if ((exercise.getAssessmentType() == AssessmentType.MANUAL || exercise.getAllowComplaintsForAutomaticAssessments()) && !participation.get().isTestRun()) {
                return false;
            }
        }

        return true;
    }
}
