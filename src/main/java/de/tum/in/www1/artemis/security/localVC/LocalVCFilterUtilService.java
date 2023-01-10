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
import de.tum.in.www1.artemis.domain.enumeration.RepositoryType;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.participation.*;
import de.tum.in.www1.artemis.domain.submissionpolicy.LockRepositoryPolicy;
import de.tum.in.www1.artemis.exception.LocalVCException;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.ExerciseDateService;
import de.tum.in.www1.artemis.service.SubmissionPolicyService;
import de.tum.in.www1.artemis.service.connectors.localvc.LocalVCRepositoryUrl;
import de.tum.in.www1.artemis.service.exam.ExamSubmissionService;
import de.tum.in.www1.artemis.service.plagiarism.PlagiarismService;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

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

    private final SubmissionPolicyService submissionPolicyService;

    private final PlagiarismService plagiarismService;

    private final TeamRepository teamRepository;

    public static final String AUTHORIZATION_HEADER = "Authorization";

    public LocalVCFilterUtilService(AuthenticationManagerBuilder authenticationManagerBuilder, UserRepository userRepository, CourseRepository courseRepository,
            AuthorizationCheckService authorizationCheckService, ProgrammingExerciseRepository programmingExerciseRepository,
            TemplateProgrammingExerciseParticipationRepository templateProgrammingExerciseParticipationRepository,
            SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository,
            ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository, ExamSubmissionService examSubmissionService,
            SubmissionPolicyService submissionPolicyService, PlagiarismService plagiarismService, TeamRepository teamRepository) {
        this.authenticationManagerBuilder = authenticationManagerBuilder;
        this.userRepository = userRepository;
        this.courseRepository = courseRepository;
        this.authorizationCheckService = authorizationCheckService;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.templateProgrammingExerciseParticipationRepository = templateProgrammingExerciseParticipationRepository;
        this.solutionProgrammingExerciseParticipationRepository = solutionProgrammingExerciseParticipationRepository;
        this.programmingExerciseStudentParticipationRepository = programmingExerciseStudentParticipationRepository;
        this.examSubmissionService = examSubmissionService;
        this.submissionPolicyService = submissionPolicyService;
        this.plagiarismService = plagiarismService;
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

    private Course findCourseForRepository(String courseShortName) throws LocalVCInternalException {
        List<Course> courses = courseRepository.findAllByShortName(courseShortName);
        if (courses.size() != 1) {
            throw new LocalVCInternalException("No course or multiple courses found for the given short name: " + courseShortName);
        }
        return courses.get(0);
    }

    private ProgrammingExercise findExerciseForRepository(String projectKey) throws LocalVCInternalException {
        List<ProgrammingExercise> exercises = programmingExerciseRepository.findByProjectKey(projectKey);
        if (exercises.size() != 1) {
            throw new LocalVCInternalException("No exercise or multiple exercises found for the given project key: " + projectKey);
        }
        return exercises.get(0);
    }

    private void authorizeUser(String repositoryTypeOrUserName, Course course, ProgrammingExercise exercise, User user, boolean forPush)
            throws LocalVCAuthException, LocalVCForbiddenException, LocalVCInternalException {

        if (isRequestingBaseRepository(repositoryTypeOrUserName)) {
            // ---- Requesting one of the base repositories ("exercise", "tests", or "solution") ----
            authorizeUserForBaseRepository(course, user, repositoryTypeOrUserName, exercise);
            return;
        }

        // ---- Requesting one of the participant repositories. ----

        // Check that the user is a student.
        if (authorizationCheckService.isAtLeastTeachingAssistantForExercise(exercise)) {
            throw new LocalVCForbiddenException();
        }

        // Check that a participation exists.
        Optional<ProgrammingExerciseStudentParticipation> participation = programmingExerciseStudentParticipationRepository.findByExerciseIdAndStudentLogin(exercise.getId(),
                user.getLogin());

        if (participation.isEmpty()) {
            throw new LocalVCInternalException();
        }

        // Check that offline IDE usage is allowed.
        if (Boolean.FALSE.equals(exercise.isAllowOfflineIde())) {
            throw new LocalVCForbiddenException();
        }

        if (exercise.isExamExercise()) {
            authorizeUserForExamExercise(participation.get(), repositoryTypeOrUserName, exercise, forPush, user);
        }
        else {
            authorizeUserForCourseExercise(course, repositoryTypeOrUserName, user, participation.get(), exercise, forPush);
        }
    }

    private boolean isRequestingBaseRepository(String requestedRepositoryType) {
        for (RepositoryType repositoryType : RepositoryType.values()) {
            if (repositoryType.toString().equals(requestedRepositoryType))
                return true;
        }
        return false;
    }

    private void authorizeUserForBaseRepository(Course course, User user, String repositoryType, ProgrammingExercise exercise)
            throws LocalVCAuthException, LocalVCInternalException {
        // Check that the user is at least a teaching assistant in the course the repository belongs to.
        boolean isAtLeastTeachingAssistantInCourse = authorizationCheckService.isAtLeastTeachingAssistantInCourse(course, user);
        if (!isAtLeastTeachingAssistantInCourse) {
            throw new LocalVCAuthException();
        }

        // Check that there is a template participation or a solution participation for the exercise.
        if (repositoryType.equals(RepositoryType.TEMPLATE.getName())) {
            try {
                templateProgrammingExerciseParticipationRepository.findByProgrammingExerciseIdElseThrow(exercise.getId());
            }
            catch (EntityNotFoundException e) {
                throw new LocalVCInternalException();
            }
        }

        if (repositoryType.equals(RepositoryType.SOLUTION.getName())) {
            try {
                solutionProgrammingExerciseParticipationRepository.findByProgrammingExerciseIdElseThrow(exercise.getId());
            }
            catch (EntityNotFoundException e) {
                throw new LocalVCInternalException();
            }
        }
    }

    private void authorizeUserForExamExercise(ProgrammingExerciseStudentParticipation participation, String userName, ProgrammingExercise exercise, boolean forPush, User user)
            throws LocalVCAuthException, LocalVCForbiddenException {
        // Check that the student owns the participation.
        if (participation.getStudent().isEmpty() || !participation.getStudent().get().getLogin().equals(userName)) {
            throw new LocalVCAuthException();
        }

        Exam exam = exercise.getExerciseGroup().getExam();

        // Start date of the exam must be in the past.
        if (exam.getStartDate() != null && ZonedDateTime.now().isAfter(exam.getStartDate())) {
            throw new LocalVCForbiddenException();
        }

        if (!forPush) {
            return;
        }

        // ---- Additional checks for push request. ----

        // Check that submission is in time.
        if (!examSubmissionService.isAllowedToSubmitDuringExam(exercise, user, false)) {
            throw new LocalVCForbiddenException();
        }

        if (participation.isTestRun()) {
            return;
        }

        // Check the submission policy.
        checkSubmissionPolicy(exercise, participation);

        // Check whether there was plagiarism detected and the user was notified by the instructor.
        if (plagiarismService.wasUserNotifiedByInstructor(participation.getId(), user.getLogin())) {
            throw new LocalVCForbiddenException();
        }
    }

    private void authorizeUserForCourseExercise(Course course, String userName, User user, ProgrammingExerciseStudentParticipation participation, ProgrammingExercise exercise,
            boolean forPush) {
        // Check if the repository belongs to a team.
        Team team = findValidTeam(course.getId(), userName, user, participation);

        if (team == null) {
            // Repository belongs to a single student.

            // Check that the user name in the repository name corresponds to the user name used for Basic Auth.
            if (!user.getLogin().equals(userName)) {
                throw new LocalVCAuthException();
            }

            // Check that the student owns the participation.
            if (!participation.isOwnedBy(user)) {
                throw new LocalVCAuthException();
            }
        }

        // Start date of the exercise must be in the past.
        if (exercise.getParticipationStartDate() != null && ZonedDateTime.now().isAfter(exercise.getParticipationStartDate())) {
            throw new LocalVCForbiddenException();
        }

        // Students can commit code for test runs after the due date.
        // The results for these submissions will not be rated.
        if (!forPush || participation.isTestRun()) {
            return;
        }

        Optional<ZonedDateTime> dueDate = ExerciseDateService.getDueDate(participation);

        // Due date of the exercise must be in the future.
        if (dueDate.isPresent() && ZonedDateTime.now().isAfter(dueDate.get())) {
            throw new LocalVCForbiddenException();
        }

        // Check the submission policy.
        checkSubmissionPolicy(exercise, participation);

        // Check whether there was plagiarism detected and the user was notified by the instructor.
        if (plagiarismService.wasUserNotifiedByInstructor(participation.getId(), user.getLogin())) {
            throw new LocalVCForbiddenException();
        }
    }

    private Team findValidTeam(Long courseId, String userNameFromUrl, User user, ProgrammingExerciseStudentParticipation participation)
            throws LocalVCInternalException, LocalVCAuthException {
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

            // Check that the team owns the participation.
            if (participation.getTeam().isEmpty() || !participation.getTeam().get().getShortName().equals(team.getShortName())) {
                throw new LocalVCAuthException();
            }

            return team;
        }
        return null;
    }

    private void checkSubmissionPolicy(ProgrammingExercise exercise, ProgrammingExerciseStudentParticipation participation)
            throws LocalVCInternalException, LocalVCForbiddenException {
        boolean submissionPolicyEnforced = false;

        Optional<ProgrammingExercise> programmingExerciseWithPolicy = programmingExerciseRepository.findWithSubmissionPolicyById(exercise.getId());

        if (programmingExerciseWithPolicy.isEmpty()) {
            throw new LocalVCInternalException();
        }

        if (programmingExerciseWithPolicy.get().getSubmissionPolicy() instanceof LockRepositoryPolicy policy) {
            submissionPolicyEnforced = submissionPolicyService.isParticipationLocked(policy, participation);
        }

        if (submissionPolicyEnforced) {
            throw new LocalVCForbiddenException();
        }
    }
}
