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
import de.tum.in.www1.artemis.exception.localvc.LocalVCBadRequestException;
import de.tum.in.www1.artemis.exception.localvc.LocalVCException;
import de.tum.in.www1.artemis.exception.localvc.LocalVCForbiddenException;
import de.tum.in.www1.artemis.exception.localvc.LocalVCInternalException;
import de.tum.in.www1.artemis.repository.SolutionProgrammingExerciseParticipationRepository;
import de.tum.in.www1.artemis.repository.TemplateProgrammingExerciseParticipationRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.service.RepositoryAccessService;
import de.tum.in.www1.artemis.service.connectors.localvc.LocalVCRepositoryUrl;
import de.tum.in.www1.artemis.service.programming.ProgrammingExerciseParticipationService;
import de.tum.in.www1.artemis.service.programming.ProgrammingExerciseService;
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

    private final ProgrammingExerciseService programmingExerciseService;

    private final TemplateProgrammingExerciseParticipationRepository templateProgrammingExerciseParticipationRepository;

    private final SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository;

    private final ProgrammingExerciseParticipationService programmingExerciseParticipationService;

    private final RepositoryAccessService repositoryAccessService;

    @Value("${artemis.version-control.url}")
    private URL localVCBaseUrl;

    /**
     * Name of the header containing the authorization information.
     */
    public static final String AUTHORIZATION_HEADER = "Authorization";

    public LocalVCFilterService(AuthenticationManagerBuilder authenticationManagerBuilder, UserRepository userRepository, ProgrammingExerciseService programmingExerciseService,
            TemplateProgrammingExerciseParticipationRepository templateProgrammingExerciseParticipationRepository,
            SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository,
            ProgrammingExerciseParticipationService programmingExerciseParticipationService, RepositoryAccessService repositoryAccessService) {
        this.authenticationManagerBuilder = authenticationManagerBuilder;
        this.userRepository = userRepository;
        this.programmingExerciseService = programmingExerciseService;
        this.templateProgrammingExerciseParticipationRepository = templateProgrammingExerciseParticipationRepository;
        this.solutionProgrammingExerciseParticipationRepository = solutionProgrammingExerciseParticipationRepository;
        this.programmingExerciseParticipationService = programmingExerciseParticipationService;
        this.repositoryAccessService = repositoryAccessService;
    }

    /**
     * Determines whether a given request to access a local VC repository (either via fetch of push) is authenticated and authorized. Throws an exception if not.
     *
     * @param servletRequest       The object containing all information about the incoming request.
     * @param repositoryActionType Indicates whether the method should authenticate a fetch or a push request. For a push request, additional checks are conducted.
     */
    public void authenticateAndAuthorizeGitRequest(HttpServletRequest servletRequest, RepositoryActionType repositoryActionType) {

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

        LocalVCRepositoryUrl url;
        try {
            url = new LocalVCRepositoryUrl(servletRequest.getRequestURL().toString().replace("/info/refs", ""), localVCBaseUrl);
        }
        catch (LocalVCException e) {
            throw new LocalVCBadRequestException("Badly formed Local Git URI: " + servletRequest.getRequestURL().toString().replace("/info/refs", ""), e);
        }

        String projectKey = url.getProjectKey();
        String repositoryTypeOrUserName = url.getRepositoryTypeOrUserName();
        // Is true if the repository slug contains "-practice-".
        boolean isPracticeRepository = url.isPracticeRepository();

        ProgrammingExercise exercise;

        try {
            exercise = programmingExerciseService.findOneByProjectKey(projectKey, true);
        }
        catch (EntityNotFoundException e) {
            throw new LocalVCInternalException("Could not find single programming exercise with project key " + projectKey, e);
        }

        // Check that offline IDE usage is allowed.
        if (Boolean.FALSE.equals(exercise.isAllowOfflineIde())) {
            throw new LocalVCForbiddenException();
        }

        authorizeUser(repositoryTypeOrUserName, user, exercise, isPracticeRepository, repositoryActionType);

        log.info("Authorizing user {} for repository {} took {}", user.getLogin(), url, TimeLogUtil.formatDurationFrom(timeNanoStart));
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

        if (!basicAuthCredentialsEncoded[0].equals("Basic")) {
            throw new LocalVCAuthException();
        }

        // Return decoded basic auth credentials which contain the username and the password.
        return new String(Base64.getDecoder().decode(basicAuthCredentialsEncoded[1]));
    }

    private void authorizeUser(String repositoryTypeOrUserName, User user, ProgrammingExercise exercise, boolean isPracticeRepository, RepositoryActionType repositoryActionType)
            throws LocalVCAuthException, LocalVCForbiddenException, LocalVCInternalException {

        if (repositoryTypeOrUserName.equals(RepositoryType.TESTS.toString())) {
            try {
                repositoryAccessService.checkAccessTestRepositoryElseThrow(false, exercise, user);
            }
            catch (AccessForbiddenException e) {
                throw new LocalVCAuthException(e);
            }
            return;
        }

        ProgrammingExerciseParticipation participation = getParticipation(repositoryTypeOrUserName, exercise, isPracticeRepository, user);

        try {
            repositoryAccessService.checkAccessRepositoryElseThrow(participation, exercise, user, repositoryActionType);
        }
        catch (AccessUnauthorizedException e) {
            throw new LocalVCAuthException(e);
        }
        catch (AccessForbiddenException e) {
            throw new LocalVCForbiddenException(e);
        }
        catch (IllegalArgumentException e) {
            throw new LocalVCInternalException(e);
        }
    }

    /**
     * Returns the participation for the given repository type or username.
     *
     * @param repositoryTypeOrUserName The repository type or username (e.g. "exercise" for the template repository, or "artemis_test_user_1" for the repository of
     *                                     artemis_test_user_1).
     * @param exercise                 The programming exercise.
     * @param isPracticeRepository     True if the repository is a practice repository, i.e. the repository name contains "-practice-". This is not true for exam test runs
     *                                     conducted by an instructor, because there is no way to tell just from the repository URL!
     * @param user                     The authenticated user.
     * @return The participation for the given repository type or username.
     */
    private ProgrammingExerciseParticipation getParticipation(String repositoryTypeOrUserName, ProgrammingExercise exercise, boolean isPracticeRepository, User user) {
        try {
            if (repositoryTypeOrUserName.equals(RepositoryType.SOLUTION.toString())) {
                return solutionProgrammingExerciseParticipationRepository.findWithEagerResultsAndSubmissionsByProgrammingExerciseIdElseThrow(exercise.getId());
            }
            else if (repositoryTypeOrUserName.equals(RepositoryType.TEMPLATE.toString())) {
                return templateProgrammingExerciseParticipationRepository.findWithEagerResultsAndSubmissionsByProgrammingExerciseIdElseThrow(exercise.getId());
            }

            return programmingExerciseParticipationService.findStudentParticipationByExerciseAndUserNameAndTestRunOrThrow(exercise, repositoryTypeOrUserName, isPracticeRepository,
                    user);
        }
        catch (EntityNotFoundException e) {
            throw new LocalVCInternalException(
                    "Could not find single participation for exercise " + exercise.getId() + " and repository type or user name " + repositoryTypeOrUserName, e);
        }
    }

    /**
     * Returns the HTTP status code for the given exception thrown by the above method "authenticateAndAuthorizeGitRequest".
     *
     * @param e             The exception thrown.
     * @param repositoryUrl The URL of the repository that was accessed.
     * @return The HTTP status code.
     */
    public int getHttpStatusForException(Exception e, String repositoryUrl) {
        if (e instanceof LocalVCAuthException) {
            return HttpStatus.UNAUTHORIZED.value();
        }
        else if (e instanceof LocalVCForbiddenException) {
            return HttpStatus.FORBIDDEN.value();
        }
        else if (e instanceof LocalVCBadRequestException) {
            return HttpStatus.BAD_REQUEST.value();
        }
        else if (e instanceof LocalVCInternalException) {
            log.error("Internal server error while trying to access repository {}: {}", repositoryUrl, e.getMessage());
            return HttpStatus.INTERNAL_SERVER_ERROR.value();
        }
        else {
            log.error("Unexpected error while trying to access repository {}: {}", repositoryUrl, e.getMessage());
            return HttpStatus.INTERNAL_SERVER_ERROR.value();
        }
    }
}
