package de.tum.in.www1.artemis.security.localvc;

import java.net.URL;
import java.time.ZonedDateTime;
import java.util.Base64;
import java.util.Optional;

import javax.servlet.http.HttpServletRequest;

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
import de.tum.in.www1.artemis.exception.LocalVCException;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.service.*;
import de.tum.in.www1.artemis.service.connectors.localvc.LocalVCRepositoryUrl;
import de.tum.in.www1.artemis.service.exam.ExamSubmissionService;
import de.tum.in.www1.artemis.service.programming.ProgrammingExerciseParticipationService;
import de.tum.in.www1.artemis.service.programming.ProgrammingExerciseService;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;
import de.tum.in.www1.artemis.web.rest.repository.RepositoryActionType;

@Service
@Profile("localvc")
public class LocalVCFilterService {

    @Value("${artemis.version-control.url}")
    private URL localVCServerUrl;

    private final AuthenticationManagerBuilder authenticationManagerBuilder;

    private final UserRepository userRepository;

    private final CourseService courseService;

    private final AuthorizationCheckService authorizationCheckService;

    private final ProgrammingExerciseService programmingExerciseService;

    private final TemplateProgrammingExerciseParticipationRepository templateProgrammingExerciseParticipationRepository;

    private final SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository;

    private final ProgrammingExerciseParticipationService programmingExerciseParticipationService;

    private final ExamSubmissionService examSubmissionService;

    private final RepositoryAccessService repositoryAccessService;

    public static final String AUTHORIZATION_HEADER = "Authorization";

    public LocalVCFilterService(AuthenticationManagerBuilder authenticationManagerBuilder, UserRepository userRepository, CourseService courseService,
            AuthorizationCheckService authorizationCheckService, ProgrammingExerciseService programmingExerciseService,
            TemplateProgrammingExerciseParticipationRepository templateProgrammingExerciseParticipationRepository,
            SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository,
            ProgrammingExerciseParticipationService programmingExerciseParticipationService, ExamSubmissionService examSubmissionService,
            RepositoryAccessService repositoryAccessService) {
        this.authenticationManagerBuilder = authenticationManagerBuilder;
        this.userRepository = userRepository;
        this.courseService = courseService;
        this.authorizationCheckService = authorizationCheckService;
        this.programmingExerciseService = programmingExerciseService;
        this.templateProgrammingExerciseParticipationRepository = templateProgrammingExerciseParticipationRepository;
        this.solutionProgrammingExerciseParticipationRepository = solutionProgrammingExerciseParticipationRepository;
        this.programmingExerciseParticipationService = programmingExerciseParticipationService;
        this.examSubmissionService = examSubmissionService;
        this.repositoryAccessService = repositoryAccessService;
    }

    /**
     * @param servletRequest       The object containing all information about the incoming request.
     * @param repositoryActionType Indicates whether the method should authenticate a fetch or a push request. For a push request, additional checks are conducted.
     * @throws LocalVCAuthException For when the user cannot be authenticated or is not authorized to access the repository.
     */
    public void authenticateAndAuthorizeGitRequest(HttpServletRequest servletRequest, RepositoryActionType repositoryActionType) throws LocalVCAuthException {

        String basicAuthCredentials = checkAuthorizationHeader(servletRequest.getHeader(LocalVCFilterService.AUTHORIZATION_HEADER));

        String username = basicAuthCredentials.split(":")[0];
        String password = basicAuthCredentials.split(":")[1];

        User user = authenticateUser(username, password);

        // Optimization.
        // For each git command (i.e. 'git fetch' or 'git push'), the git client sends three requests.
        // The URLs of the first two requests end on '[repository URL]/info/refs'. The third one ends on '[repository URL]/git-receive-pack' (for push) and '[repository
        // URL]/git-upload-pack' (for fetch).
        // The following checks will only be conducted for the second request, so we do not have to access the database too often.
        // The first request does not contain credentials and will thus already be blocked by the 'authenticateUser' method above.
        if (!servletRequest.getRequestURI().endsWith("/info/refs")) {
            return;
        }

        String url = servletRequest.getRequestURL().toString();
        LocalVCRepositoryUrl localVCUrl = validateRepositoryUrl(url);

        String projectKey = localVCUrl.getProjectKey();
        String courseShortName = localVCUrl.getCourseShortName();
        String repositoryTypeOrUserName = localVCUrl.getRepositoryTypeOrUserName();
        boolean isTestRunRepository = localVCUrl.isTestRunRepository();

        try {
            courseService.findOneByShortName(courseShortName);
        }
        catch (EntityNotFoundException e) {
            throw new LocalVCInternalException("Could not find single course with short name " + courseShortName);
        }

        ProgrammingExercise exercise;

        try {
            exercise = programmingExerciseService.findOneByProjectKey(projectKey, true);
        }
        catch (EntityNotFoundException e) {
            throw new LocalVCInternalException("Could not find single programming exercise with project key " + projectKey);
        }

        // Check that offline IDE usage is allowed.
        if (Boolean.FALSE.equals(exercise.isAllowOfflineIde())) {
            throw new LocalVCForbiddenException();
        }

        authorizeUser(repositoryTypeOrUserName, user, exercise, isTestRunRepository, repositoryActionType);
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

        // Check that the user exists.
        return userRepository.findOneByLogin(username).orElseThrow(LocalVCAuthException::new);
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

    private void authorizeUser(String repositoryTypeOrUserName, User user, ProgrammingExercise exercise, boolean isTestRunRepository, RepositoryActionType repositoryActionType)
            throws LocalVCAuthException, LocalVCForbiddenException, LocalVCInternalException {

        if (isRequestingTestRepository(repositoryTypeOrUserName)) {
            try {
                repositoryAccessService.checkAccessTestRepositoryElseThrow(false, exercise, user);
            }
            catch (AccessForbiddenException e) {
                throw new LocalVCForbiddenException();
            }
            return;
        }

        Participation participation;

        try {
            if (repositoryTypeOrUserName.equals(RepositoryType.TEMPLATE.toString())) {
                participation = templateProgrammingExerciseParticipationRepository.findByProgrammingExerciseIdElseThrow(exercise.getId());
            }
            else if (repositoryTypeOrUserName.equals(RepositoryType.SOLUTION.toString())) {
                participation = solutionProgrammingExerciseParticipationRepository.findByProgrammingExerciseIdElseThrow(exercise.getId());
            }
            else {
                participation = programmingExerciseParticipationService.findStudentParticipationByExerciseAndStudentLoginAndTestRun(exercise, repositoryTypeOrUserName,
                        isTestRunRepository, false);

                // In this case, the username in the repositoryUrl must correspond to the user, if it is not a team exercise.
                if (!repositoryTypeOrUserName.equals(user.getLogin()) && !exercise.isTeamMode()) {
                    throw new LocalVCAuthException();
                }
            }
        }
        catch (EntityNotFoundException e) {
            throw new LocalVCInternalException(
                    "Could not find single participation for exercise " + exercise.getId() + " and repository type or user name " + repositoryTypeOrUserName);
        }

        try {
            repositoryAccessService.checkAccessRepositoryElseThrow(participation, exercise, user, repositoryActionType);
        }
        catch (AccessForbiddenException e) {
            throw new LocalVCForbiddenException();
        }
        catch (IllegalArgumentException e) {
            throw new LocalVCInternalException();
        }

        // if (isRequestingBaseRepository(repositoryTypeOrUserName)) {
        // // ---- Requesting one of the base repositories ("exercise", "tests", or "solution") ----
        // authorizeUserForBaseRepository(user, repositoryTypeOrUserName, exercise);
        // return;
        // }
        //
        // // ---- Requesting one of the participant repositories. ----
        //
        //
        //
        // if (!repositoryTypeOrUserName.equals(user.getLogin())) {
        // // Instructors can fetch and push to any repository.
        // if (authorizationCheckService.isAtLeastInstructorForExercise(exercise, user)) {
        // return;
        // }
        //
        // // Teaching assistants can only fetch any repository.
        // if (!forPush && authorizationCheckService.isAtLeastTeachingAssistantForExercise(exercise, user)) {
        // return;
        // }
        //
        // // Could still be a team mode exercise. If not, the user is trying to access another student's repository.
        // if (!exercise.isTeamMode()) {
        // throw new LocalVCAuthException();
        // }
        // }
        //
        // ProgrammingExerciseStudentParticipation participation;
        // try {
        // participation = programmingExerciseParticipationService.findStudentParticipationByExerciseAndStudentLoginAndTestRun(exercise, repositoryTypeOrUserName,
        // isTestRunRepository, false);
        // }
        // catch (EntityNotFoundException e) {
        // // This should not happen. If the participation was deleted, the repository should have been deleted as well which results in an immediate 404.
        // throw new LocalVCInternalException(
        // "Could not find participation for exercise " + exercise.getId() + " and user " + user.getLogin() + " and test run " + isTestRunRepository);
        // }
        //
        // // Check that the student or the student's team owns the participation.
        // if (!participation.isOwnedBy(user)) {
        // throw new LocalVCAuthException();
        // }
        //
        // if (exercise.isExamExercise()) {
        // authorizeUserForExamExercise(participation, exercise, user, forPush);
        // }
        // else {
        // authorizeUserForCourseExercise(participation, exercise, isTestRunRepository, forPush);
        // }
        //
        // // ---- Below checks are only relevant when the user pushes to the repository. If forPush == false, this code will not be reached. ----
        //
        // // Check the submission policy.
        // if (exercise.getSubmissionPolicy() instanceof LockRepositoryPolicy policy && submissionPolicyService.isParticipationLocked(policy, participation)) {
        // throw new LocalVCForbiddenException();
        // }
        //
        // // Check whether there was plagiarism detected and the user was notified by the instructor.
        // if (plagiarismService.wasUserNotifiedByInstructor(participation.getId(), user.getLogin())) {
        // throw new LocalVCForbiddenException();
        // }
    }

    private boolean isRequestingBaseRepository(String requestedRepositoryType) {
        return requestedRepositoryType.equals(RepositoryType.TEMPLATE.toString()) || requestedRepositoryType.equals(RepositoryType.SOLUTION.toString());
    }

    private boolean isRequestingTestRepository(String repositoryTypeOrUserName) {
        return repositoryTypeOrUserName.equals(RepositoryType.TESTS.toString());
    }

    private void authorizeUserForBaseRepository(User user, String repositoryType, ProgrammingExercise exercise) throws LocalVCAuthException, LocalVCInternalException {
        // Check that the user is at least a teaching assistant in the course the repository belongs to.
        boolean isAtLeastTeachingAssistantInCourse = authorizationCheckService.isAtLeastTeachingAssistantForExercise(exercise, user);
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
        else if (repositoryType.equals(RepositoryType.SOLUTION.getName())) {
            try {
                solutionProgrammingExerciseParticipationRepository.findByProgrammingExerciseIdElseThrow(exercise.getId());
            }
            catch (EntityNotFoundException e) {
                throw new LocalVCInternalException();
            }
        }
        else {
            // Repository type must be "tests". Auxiliary repositories are not supported yet.
            if (!repositoryType.equals(RepositoryType.TESTS.getName())) {
                throw new LocalVCInternalException();
            }
        }
    }

    private void authorizeUserForExamExercise(ProgrammingExerciseStudentParticipation participation, ProgrammingExercise exercise, User user, boolean forPush)
            throws LocalVCAuthException, LocalVCForbiddenException {

        // Access is allowed for test runs independent of the start date and due date.
        if (participation.isTestRun()) {
            return;
        }

        Exam exam = exercise.getExerciseGroup().getExam();

        // Start date of the exam must be in the past.
        if (exam.getStartDate() != null && ZonedDateTime.now().isBefore(exam.getStartDate())) {
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
    }

    private void authorizeUserForCourseExercise(ProgrammingExerciseStudentParticipation participation, ProgrammingExercise exercise, boolean isTestRunRepository, boolean forPush) {

        // Students can commit code for test runs after the due date.
        // The results for these submissions will not be rated.
        if (!forPush || isTestRunRepository) {
            return;
        }

        // ---- Additional checks for push request. ----

        // Start date of the exercise must be in the past.
        if (exercise.getParticipationStartDate() != null && ZonedDateTime.now().isBefore(exercise.getParticipationStartDate())) {
            throw new LocalVCForbiddenException();
        }

        // Due date of the exercise must be in the future.
        Optional<ZonedDateTime> dueDate = ExerciseDateService.getDueDate(participation);
        if (dueDate.isPresent() && ZonedDateTime.now().isAfter(dueDate.get())) {
            throw new LocalVCForbiddenException();
        }
    }
}
