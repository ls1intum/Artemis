package de.tum.in.www1.artemis.security.localvc;

import java.net.URL;
import java.util.Base64;

import javax.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.RepositoryType;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.exception.localvc.LocalVCAuthException;
import de.tum.in.www1.artemis.exception.localvc.LocalVCForbiddenException;
import de.tum.in.www1.artemis.exception.localvc.LocalVCInternalException;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.RepositoryAccessService;
import de.tum.in.www1.artemis.service.connectors.localvc.LocalVCRepositoryUrl;
import de.tum.in.www1.artemis.service.programming.ProgrammingExerciseParticipationService;
import de.tum.in.www1.artemis.service.util.TimeLogUtil;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.in.www1.artemis.web.rest.errors.AccessUnauthorizedException;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;
import de.tum.in.www1.artemis.web.rest.repository.RepositoryActionType;

/**
 * This service is responsible for authenticating and authorizing git requests.
 * It is used by the LocalVCFetchFilter and LocalVCPushFilter.
 */
@Service
@Profile("localvc")
public class LocalVCFilterService {

    private final Logger log = LoggerFactory.getLogger(LocalVCFilterService.class);

    private final AuthenticationManagerBuilder authenticationManagerBuilder;

    private final UserRepository userRepository;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final ProgrammingExerciseParticipationService programmingExerciseParticipationService;

    private final RepositoryAccessService repositoryAccessService;

    private final AuthorizationCheckService authorizationCheckService;

    @Value("${artemis.version-control.url}")
    private URL localVCBaseUrl;

    /**
     * Name of the header containing the authorization information.
     */
    public static final String AUTHORIZATION_HEADER = "Authorization";

    public LocalVCFilterService(AuthenticationManagerBuilder authenticationManagerBuilder, UserRepository userRepository,
            ProgrammingExerciseRepository programmingExerciseRepository, ProgrammingExerciseParticipationService programmingExerciseParticipationService,
            RepositoryAccessService repositoryAccessService, AuthorizationCheckService authorizationCheckService) {
        this.authenticationManagerBuilder = authenticationManagerBuilder;
        this.userRepository = userRepository;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.programmingExerciseParticipationService = programmingExerciseParticipationService;
        this.repositoryAccessService = repositoryAccessService;
        this.authorizationCheckService = authorizationCheckService;
    }

    /**
     * Determines whether a given request to access a local VC repository (either via fetch of push) is authenticated and authorized.
     *
     * @param servletRequest       The object containing all information about the incoming request.
     * @param repositoryActionType Indicates whether the method should authenticate a fetch or a push request. For a push request, additional checks are conducted.
     * @throws LocalVCAuthException      If the user authentication fails or the user is not authorized to access a certain repository.
     * @throws LocalVCForbiddenException If the user is not allowed to access the repository, e.g. because offline IDE usage is not allowed or the due date has passed.
     * @throws LocalVCInternalException  If an internal error occurs, e.g. because the LocalVCRepositoryUrl could not be created.
     */
    public void authenticateAndAuthorizeGitRequest(HttpServletRequest servletRequest, RepositoryActionType repositoryActionType)
            throws LocalVCAuthException, LocalVCForbiddenException {

        long timeNanoStart = System.nanoTime();

        User user = authenticateUser(servletRequest.getHeader(LocalVCFilterService.AUTHORIZATION_HEADER));

        // Optimization.
        // For each git command (i.e. 'git fetch' or 'git push'), the git client sends three requests.
        // The URLs of the first two requests end on '[repository URL]/info/refs'. The third one ends on '[repository URL]/git-receive-pack' (for push) and '[repository
        // URL]/git-upload-pack' (for fetch).
        // The following checks will only be conducted for the second request, so we do not have to access the database too often.
        // The first request does not contain credentials and will thus already be blocked by the 'authenticateUser' method above.
        if (!servletRequest.getRequestURI().endsWith("/info/refs")) {
            return;
        }

        LocalVCRepositoryUrl localVCRepositoryUrl = new LocalVCRepositoryUrl(servletRequest.getRequestURL().toString().replace("/info/refs", ""), localVCBaseUrl);

        String projectKey = localVCRepositoryUrl.getProjectKey();
        String repositoryTypeOrUserName = localVCRepositoryUrl.getRepositoryTypeOrUserName();

        ProgrammingExercise exercise;

        try {
            exercise = programmingExerciseRepository.findOneByProjectKeyOrThrow(projectKey, true);
        }
        catch (EntityNotFoundException e) {
            throw new LocalVCInternalException("Could not find single programming exercise with project key " + projectKey, e);
        }

        // Check that offline IDE usage is allowed.
        if (Boolean.FALSE.equals(exercise.isAllowOfflineIde()) && authorizationCheckService.isOnlyStudentInCourse(exercise.getCourseViaExerciseGroupOrCourseMember(), user)) {
            throw new LocalVCForbiddenException();
        }

        authorizeUser(repositoryTypeOrUserName, user, exercise, repositoryActionType, localVCRepositoryUrl.isPracticeRepository());

        log.info("Authorizing user {} for repository {} took {}", user.getLogin(), localVCRepositoryUrl, TimeLogUtil.formatDurationFrom(timeNanoStart));
    }

    private User authenticateUser(String authorizationHeader) throws LocalVCAuthException {

        String basicAuthCredentials = checkAuthorizationHeader(authorizationHeader);

        if (basicAuthCredentials.split(":").length != 2) {
            throw new LocalVCAuthException();
        }

        String username = basicAuthCredentials.split(":")[0];
        String password = basicAuthCredentials.split(":")[1];

        try {
            SecurityUtils.checkUsernameAndPasswordValidity(username, password);

            // Try to authenticate the user.
            UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(username, password);
            authenticationManagerBuilder.getObject().authenticate(authenticationToken);
        }
        catch (AccessForbiddenException | AuthenticationException e) {
            throw new LocalVCAuthException(e);
        }

        // Check that the user exists.
        return userRepository.findOneByLogin(username).orElseThrow(LocalVCAuthException::new);
    }

    private String checkAuthorizationHeader(String authorizationHeader) throws LocalVCAuthException {
        if (authorizationHeader == null) {
            throw new LocalVCAuthException();
        }

        String[] basicAuthCredentialsEncoded = authorizationHeader.split(" ");

        if (!("Basic".equals(basicAuthCredentialsEncoded[0]))) {
            throw new LocalVCAuthException();
        }

        // Return decoded basic auth credentials which contain the username and the password.
        return new String(Base64.getDecoder().decode(basicAuthCredentialsEncoded[1]));
    }

    private void authorizeUser(String repositoryTypeOrUserName, User user, ProgrammingExercise exercise, RepositoryActionType repositoryActionType, boolean isPracticeRepository)
            throws LocalVCAuthException, LocalVCForbiddenException {

        if (repositoryTypeOrUserName.equals(RepositoryType.TESTS.toString())) {
            try {
                // Only editors and higher are able push. Teaching assistants can only fetch.
                repositoryAccessService.checkAccessTestRepositoryElseThrow(repositoryActionType == RepositoryActionType.WRITE, exercise, user);
            }
            catch (AccessForbiddenException e) {
                throw new LocalVCAuthException(e);
            }
            return;
        }

        // The repository is a test run repository either if the repository URL contains "-practice-" or if the exercise is an exam exercise and the repository's owner is at least
        // an editor (exam test run repository).
        boolean isTestRunRepository = isPracticeRepository || (exercise.isExamExercise() && !repositoryTypeOrUserName.equals(RepositoryType.TEMPLATE.toString())
                && !repositoryTypeOrUserName.equals(RepositoryType.SOLUTION.toString())
                && authorizationCheckService.isAtLeastEditorForExercise(exercise, userRepository.getUserByLoginElseThrow(repositoryTypeOrUserName)));
        ProgrammingExerciseParticipation participation = programmingExerciseParticipationService
                .findParticipationByRepositoryTypeOrUserNameAndExerciseAndTestRunOrThrow(repositoryTypeOrUserName, exercise, isTestRunRepository, false);

        try {
            repositoryAccessService.checkAccessRepositoryElseThrow(participation, user, exercise, repositoryActionType);
        }
        catch (AccessUnauthorizedException e) {
            throw new LocalVCAuthException(e);
        }
        catch (AccessForbiddenException e) {
            throw new LocalVCForbiddenException(e);
        }
    }

    /**
     * Returns the HTTP status code for the given exception thrown by the above method "authenticateAndAuthorizeGitRequest".
     *
     * @param exception     The exception thrown.
     * @param repositoryUrl The URL of the repository that was accessed.
     * @return The HTTP status code.
     */
    public int getHttpStatusForException(Exception exception, String repositoryUrl) {
        if (exception instanceof LocalVCAuthException) {
            return HttpStatus.UNAUTHORIZED.value();
        }
        else if (exception instanceof LocalVCForbiddenException) {
            return HttpStatus.FORBIDDEN.value();
        }
        else {
            log.error("Internal server error while trying to access repository {}: {}", repositoryUrl, exception.getMessage());
            return HttpStatus.INTERNAL_SERVER_ERROR.value();
        }
    }
}
