package de.tum.cit.aet.artemis.programming.service.localvc;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALVC;
import static de.tum.cit.aet.artemis.core.util.HttpRequestUtils.getIpStringFromRequest;
import static de.tum.cit.aet.artemis.programming.service.localvc.LocalVCPersonalAccessTokenManagementService.TOKEN_PREFIX;
import static de.tum.cit.aet.artemis.programming.service.localvc.LocalVCPersonalAccessTokenManagementService.VCS_ACCESS_TOKEN_LENGTH;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import jakarta.servlet.http.HttpServletRequest;

import org.apache.commons.lang3.StringUtils;
import org.apache.sshd.server.session.ServerSession;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.exception.ContinuousIntegrationException;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.exception.RateLimitExceededException;
import de.tum.cit.aet.artemis.core.exception.VersionControlException;
import de.tum.cit.aet.artemis.core.exception.localvc.LocalVCAuthException;
import de.tum.cit.aet.artemis.core.exception.localvc.LocalVCForbiddenException;
import de.tum.cit.aet.artemis.core.exception.localvc.LocalVCInternalException;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.RateLimitType;
import de.tum.cit.aet.artemis.core.security.SecurityUtils;
import de.tum.cit.aet.artemis.core.service.RateLimitService;
import de.tum.cit.aet.artemis.core.util.TimeLogUtil;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participation;
import de.tum.cit.aet.artemis.exercise.service.ExerciseVersionService;
import de.tum.cit.aet.artemis.programming.domain.AuthenticationMechanism;
import de.tum.cit.aet.artemis.programming.domain.Commit;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.domain.SolutionProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.VcsAccessLog;
import de.tum.cit.aet.artemis.programming.repository.ParticipationVCSAccessTokenRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.service.AuxiliaryRepositoryService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseParticipationService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseTestCaseChangedService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingSubmissionMessagingService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingSubmissionService;
import de.tum.cit.aet.artemis.programming.service.RepositoryAccessService;
import de.tum.cit.aet.artemis.programming.service.ci.ContinuousIntegrationTriggerService;
import de.tum.cit.aet.artemis.programming.service.localvc.ssh.SshConstants;
import de.tum.cit.aet.artemis.programming.web.repository.RepositoryActionType;
import inet.ipaddr.IPAddress;
import inet.ipaddr.IPAddressString;

/**
 * This service is responsible for authenticating and authorizing git requests as well as for retrieving the requested Git repositories from disk.
 * It is used by the ArtemisGitServletService, the LocalVCFetchFilter, and the LocalVCPushFilter.
 */
@Lazy
@Service
@Profile(PROFILE_LOCALVC)
// TODO: we should rename this because its used in the context of https and ssh git operations
public class LocalVCServletService {

    private static final Logger log = LoggerFactory.getLogger(LocalVCServletService.class);

    private final AuthenticationManager authenticationManager;

    private final UserRepository userRepository;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final RepositoryAccessService repositoryAccessService;

    private final ProgrammingExerciseParticipationService programmingExerciseParticipationService;

    private final AuxiliaryRepositoryService auxiliaryRepositoryService;

    private final ContinuousIntegrationTriggerService ciTriggerService;

    private final ProgrammingSubmissionService programmingSubmissionService;

    private final ProgrammingSubmissionMessagingService programmingSubmissionMessagingService;

    private final ProgrammingExerciseTestCaseChangedService programmingExerciseTestCaseChangedService;

    // TODO As soon as only LocalVC is supported, this Optional can be removed
    private final Optional<VcsAccessLogService> vcsAccessLogService;

    private final ParticipationVCSAccessTokenRepository participationVCSAccessTokenRepository;

    private final RateLimitService rateLimitService;

    private final ExerciseVersionService exerciseVersionService;

    @Value("${artemis.version-control.url}")
    private URI localVCBaseUri;

    @Value("${artemis.version-control.local-vcs-repo-path}")
    private Path localVCBasePath;

    @Value("${artemis.version-control.build-agent-git-username}")
    private String buildAgentGitUsername;

    @Value("${artemis.version-control.build-agent-git-password}")
    private String buildAgentGitPassword;

    public static final String BUILD_USER_NAME = "buildjob_user";

    public LocalVCServletService(AuthenticationManager authenticationManager, UserRepository userRepository, ProgrammingExerciseRepository programmingExerciseRepository,
            RepositoryAccessService repositoryAccessService, ProgrammingExerciseParticipationService programmingExerciseParticipationService,
            AuxiliaryRepositoryService auxiliaryRepositoryService, ContinuousIntegrationTriggerService ciTriggerService, ProgrammingSubmissionService programmingSubmissionService,
            ProgrammingSubmissionMessagingService programmingSubmissionMessagingService, ProgrammingExerciseTestCaseChangedService programmingExerciseTestCaseChangedService,
            ParticipationVCSAccessTokenRepository participationVCSAccessTokenRepository, Optional<VcsAccessLogService> vcsAccessLogService, RateLimitService rateLimitService,
            ExerciseVersionService exerciseVersionService) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.repositoryAccessService = repositoryAccessService;
        this.programmingExerciseParticipationService = programmingExerciseParticipationService;
        this.auxiliaryRepositoryService = auxiliaryRepositoryService;
        this.ciTriggerService = ciTriggerService;
        this.programmingSubmissionService = programmingSubmissionService;
        this.programmingSubmissionMessagingService = programmingSubmissionMessagingService;
        this.programmingExerciseTestCaseChangedService = programmingExerciseTestCaseChangedService;
        this.participationVCSAccessTokenRepository = participationVCSAccessTokenRepository;
        this.vcsAccessLogService = vcsAccessLogService;
        this.rateLimitService = rateLimitService;
        this.exerciseVersionService = exerciseVersionService;
    }

    /**
     * Resolves the repository for the given path by creating a JGit repository and opening the local repository.
     * <p>
     * The returned {@link Repository} remains open after this method returns.
     * It is the caller's responsibility to close it when no longer needed.
     * <strong>Do not</strong> use try-with-resources inside this method, as that would close the repository
     * before the caller can use it.
     *
     * @param repositoryPath the path of the repository, as parsed out of the URL (everything after /git).
     * @return the opened repository instance.
     * @throws RepositoryNotFoundException if the repository could not be found.
     */
    public Repository resolveRepository(String repositoryPath) throws RepositoryNotFoundException {

        long timeNanoStart = System.nanoTime();
        // Find the local repository depending on the name.
        Path repositoryDir = localVCBasePath.resolve(repositoryPath);

        log.debug("Path to resolve repository from: {}", repositoryDir);
        if (!Files.exists(repositoryDir)) {
            log.error("Could not find local repository with name {}", repositoryPath);
            throw new RepositoryNotFoundException(repositoryPath);
        }

        log.debug("Opening local repository {}", repositoryPath);
        try {
            Repository repository = FileRepositoryBuilder.create(repositoryDir.toFile());
            // Enable pushing without credentials, authentication is handled by the LocalVCPushFilter.
            repository.getConfig().setBoolean("http", null, "receivepack", true);

            log.debug("Resolving repository for repository {} took {}", repositoryPath, TimeLogUtil.formatDurationFrom(timeNanoStart));
            return repository;
        }
        catch (IOException e) {
            log.error("Unable to open local repository {}", repositoryPath);
            throw new RepositoryNotFoundException(repositoryPath, e);
        }
    }

    /**
     * Determines whether a given request to access a local VC repository (either via fetch of push) is authenticated and authorized.
     *
     * @param request          The object containing all information about the incoming request.
     * @param repositoryAction Indicates whether the method should authenticate a fetch or a push request. For a push request, additional checks are conducted.
     * @throws LocalVCAuthException      If the user authentication fails or the user is not authorized to access a certain repository.
     * @throws LocalVCForbiddenException If the user is not allowed to access the repository, e.g. because offline IDE usage is not allowed or the due date has passed.
     * @throws LocalVCInternalException  If an internal error occurs, e.g. because the LocalVCRepositoryUri could not be created.
     */
    public void authenticateAndAuthorizeGitRequest(HttpServletRequest request, RepositoryActionType repositoryAction)
            throws LocalVCAuthException, LocalVCForbiddenException, AuthenticationException {

        long timeNanoStart = System.nanoTime();

        String authorizationHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

        // The first request does not contain an authorizationHeader, the client expects this response
        if (authorizationHeader == null) {
            throw new LocalVCAuthException("No authorization header provided");
        }

        // If it is a fetch request, we check if it is the build agent that is fetching the repository.
        if (repositoryAction == RepositoryActionType.READ) {
            UsernameAndPassword usernameAndPassword = extractUsernameAndPassword(authorizationHeader);
            if (Objects.equals(usernameAndPassword.username(), buildAgentGitUsername) && Objects.equals(usernameAndPassword.password(), buildAgentGitPassword)) {
                // Authentication successful
                return;
            }
        }

        // Optimization.
        // For each git command (i.e. 'git fetch' or 'git push'), the git client sends three requests.
        // The URLs of the first two requests end on '[repository URI]/info/refs'. The third one ends on '[repository URI]/git-receive-pack' (for push) and '[repository
        // URL]/git-upload-pack' (for fetch).
        // The following checks will only be conducted for the second request, so we do not have to access the database too often.
        if (!request.getRequestURI().endsWith("/info/refs")) {
            return;
        }

        String ipString = getIpStringFromRequest(request);
        final IPAddress ipAddress = new IPAddressString(ipString).getAddress();
        rateLimitService.enforcePerMinute(ipAddress, RateLimitType.AUTHENTICATION);

        LocalVCRepositoryUri localVCRepositoryUri = parseRepositoryUri(request);
        log.debug("Parsed repository URI from request: {}", localVCRepositoryUri);
        String projectKey = localVCRepositoryUri.getProjectKey();
        String repositoryTypeOrUserName = localVCRepositoryUri.getRepositoryTypeOrUserName();

        ProgrammingExercise exercise = getProgrammingExerciseOrThrow(projectKey);

        User user = authenticateUser(authorizationHeader, exercise, localVCRepositoryUri);

        // Check that offline IDE usage is allowed.
        try {
            repositoryAccessService.checkHasAccessToOfflineIDEElseThrow(exercise, user);
        }
        catch (AccessForbiddenException e) {
            throw new LocalVCForbiddenException(e);
        }

        try {
            var optionalParticipation = authorizeUser(repositoryTypeOrUserName, user, exercise, repositoryAction, localVCRepositoryUri, false);
            savePreliminaryVcsAccessLogForHTTPs(request, localVCRepositoryUri, user, repositoryAction, optionalParticipation);
        }
        catch (LocalVCForbiddenException e) {
            log.error("User {} does not have access to the repository {}", user.getLogin(), localVCRepositoryUri);
            saveFailedAccessVcsAccessLog(new AuthenticationContext.Request(request), repositoryTypeOrUserName, exercise, localVCRepositoryUri, user, repositoryAction);
            throw e;
        }

        log.debug("Authorizing user {} for repository {} took {}", user.getLogin(), localVCRepositoryUri, TimeLogUtil.formatDurationFrom(timeNanoStart));
    }

    /**
     * Determines whether a given request to access a local VC repository (either via fetch of push) is authenticated and authorized.
     *
     * @param request               The object containing all information about the incoming request.
     * @param localVCRepositoryUri  The uri of the requested repository
     * @param user                  The user
     * @param repositoryAction      Indicates whether the method should authenticate a fetch or a push request. For a push request, additional checks are conducted.
     * @param optionalParticipation The participation for which the access log should be stored. If an empty Optional is provided, the method does nothing
     * @throws LocalVCAuthException If the user authentication fails or the user is not authorized to access a certain repository.
     */
    private void savePreliminaryVcsAccessLogForHTTPs(HttpServletRequest request, LocalVCRepositoryUri localVCRepositoryUri, User user, RepositoryActionType repositoryAction,
            Optional<ProgrammingExerciseParticipation> optionalParticipation) throws LocalVCAuthException {
        if (optionalParticipation.isPresent()) {
            ProgrammingExerciseParticipation participation = optionalParticipation.get();
            var ipAddress = request.getRemoteAddr();
            var authenticationMechanism = resolveHTTPSAuthenticationMechanism(request.getHeader(HttpHeaders.AUTHORIZATION), user);

            String finalCommitHash = getCommitHash(localVCRepositoryUri);
            RepositoryActionType finalRepositoryAction = repositoryAction == RepositoryActionType.WRITE ? RepositoryActionType.PUSH : RepositoryActionType.PULL;
            vcsAccessLogService.ifPresent(service -> service.saveAccessLog(user, participation, finalRepositoryAction, authenticationMechanism, finalCommitHash, ipAddress));
        }
    }

    /**
     * Logs a failed attempt to access a repository.
     *
     * @param context                  the Authentication context
     * @param repositoryTypeOrUserName A string representing either the repository type or the username associated with the repository.
     * @param exercise                 The {@link Exercise} associated with the repository.
     * @param localVCRepositoryUri     The {@link LocalVCRepositoryUri} representing the repository location.
     * @param user                     The {@link User} attempting the access.
     * @param repositoryAction         The {@link RepositoryActionType} action that was attempted.
     */
    public void saveFailedAccessVcsAccessLog(AuthenticationContext context, String repositoryTypeOrUserName, Exercise exercise, LocalVCRepositoryUri localVCRepositoryUri,
            User user, RepositoryActionType repositoryAction) {
        var participation = tryToLoadParticipation(false, repositoryTypeOrUserName, localVCRepositoryUri, (ProgrammingExercise) exercise);
        var commitHash = getCommitHash(localVCRepositoryUri);
        var authenticationMechanism = resolveAuthenticationMechanismFromSessionOrRequest(context, user);
        var action = repositoryAction == RepositoryActionType.WRITE ? RepositoryActionType.PUSH_FAIL : RepositoryActionType.CLONE_FAIL;
        var ipAddress = context.getIpAddress();
        vcsAccessLogService.ifPresent(service -> service.saveAccessLog(user, participation, action, authenticationMechanism, commitHash, ipAddress));
    }

    /**
     * Determines the authentication mechanism based on the provided session or request.
     *
     * <p>
     * If a {@link ServerSession} is present, the authentication mechanism is assumed to be SSH.
     * </p>
     * <p>
     * If an {@link HttpServletRequest} is present, the method attempts to resolve the authentication
     * mechanism using the authorization header. If an exception occurs, HTTPS authentication is assumed by default.
     * </p>
     * <p>
     * If neither a session nor a request is available, the authentication mechanism defaults to OTHER.
     * </p>
     *
     * @param context the Authentication context
     * @param user    the user for whom authentication is being determined
     * @return the resolved {@link AuthenticationMechanism}
     */
    private AuthenticationMechanism resolveAuthenticationMechanismFromSessionOrRequest(AuthenticationContext context, User user) {
        switch (context) {
            case AuthenticationContext.Session ignored -> {
                return AuthenticationMechanism.SSH;
            }
            case AuthenticationContext.Request request -> {
                try {
                    return resolveHTTPSAuthenticationMechanism(request.request().getHeader(HttpHeaders.AUTHORIZATION), user);
                }
                catch (LocalVCAuthException ignored) {
                    return AuthenticationMechanism.AUTH_HEADER_MISSING;
                }
            }
        }
    }

    /**
     * Retrieves the latest commit hash from the given repository.
     *
     * @param localVCRepositoryUri The {@link LocalVCRepositoryUri} representing the repository location.
     * @return The latest commit hash as a string, or an empty string if retrieval fails.
     */
    private String getCommitHash(LocalVCRepositoryUri localVCRepositoryUri) {
        try {
            String repositoryPath = localVCRepositoryUri.getRelativeRepositoryPath().toString();
            try (Repository repository = resolveRepository(repositoryPath)) {
                return getLatestCommitHash(repository);
            }
        }
        catch (GitAPIException | RepositoryNotFoundException e) {
            log.warn("Failed to obtain commit hash for repository {}. Error: {}", localVCRepositoryUri.getRelativeRepositoryPath().toString(), e.getMessage());
        }
        return "";
    }

    /**
     * Resolves the user's authentication mechanism for the repository
     *
     * @param authorizationHeader the request's authorizationHeader, containing the token or password
     * @param user                the user
     * @return the authentication type
     * @throws LocalVCAuthException if extracting the token or password from the authorizationHeader fails
     */
    private AuthenticationMechanism resolveHTTPSAuthenticationMechanism(String authorizationHeader, User user) throws LocalVCAuthException {
        UsernameAndPassword usernameAndPassword = extractUsernameAndPassword(authorizationHeader);

        String password = usernameAndPassword.password();
        if (!password.startsWith(TOKEN_PREFIX)) {
            return AuthenticationMechanism.PASSWORD;
        }
        if (password.equals(user.getVcsAccessToken())) {
            return AuthenticationMechanism.USER_VCS_ACCESS_TOKEN;
        }
        return AuthenticationMechanism.PARTICIPATION_VCS_ACCESS_TOKEN;
    }

    /**
     * Authenticates a user based on the provided authorization header for a specific programming exercise/repository.
     * Authentication is tried with: 1) user VCS access token, 2) user participation VCS access token 3) password
     *
     * @param authorizationHeader  the authorization header containing authentication credentials
     * @param exercise             the programming exercise the user is attempting to access
     * @param localVCRepositoryUri the URI of the local version control repository the user is attempting to access
     * @return the authenticated {@link User} if authentication is successful
     * @throws LocalVCAuthException    if an error occurs during authentication with the local version control system
     * @throws AuthenticationException if the authentication credentials are invalid or authentication fails
     */
    private User authenticateUser(String authorizationHeader, ProgrammingExercise exercise, LocalVCRepositoryUri localVCRepositoryUri)
            throws LocalVCAuthException, AuthenticationException {

        UsernameAndPassword usernameAndPassword = extractUsernameAndPassword(authorizationHeader);
        String username = usernameAndPassword.username();
        String passwordOrToken = usernameAndPassword.password();

        User user = userRepository.findOneByLogin(username).orElseThrow(LocalVCAuthException::new);

        try {
            SecurityUtils.checkUsernameAndPasswordValidity(username, passwordOrToken);
        }
        catch (AccessForbiddenException | AuthenticationException e) {
            if (StringUtils.isNotEmpty(passwordOrToken)) {
                log.warn("Failed login attempt for user {} with password {} due to issue: {}", username, passwordOrToken, e.getMessage());
            }
            throw new LocalVCAuthException(e.getMessage());
        }

        // check user VCS access token
        if (Objects.equals(user.getVcsAccessToken(), passwordOrToken) && user.getVcsAccessTokenExpiryDate() != null
                && user.getVcsAccessTokenExpiryDate().isAfter(ZonedDateTime.now())) {
            return user;
        }

        // check user participation VCS access token
        if (tryAuthenticationWithParticipationVCSAccessToken(user, passwordOrToken, exercise, localVCRepositoryUri)) {
            return user;
        }

        // if the user does not have an access token or used a password, we try to authenticate the user with it
        // Try to authenticate the user based on the configured options, this can include sending the data to an external system (e.g. LDAP) or using internal authentication.
        UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(username, passwordOrToken);
        authenticationManager.authenticate(authenticationToken);

        return user;
    }

    /**
     * Attempts to authenticate a user with the provided participation VCS access token
     *
     * @param user                 the user attempting authentication
     * @param providedToken        the participation VCS access token provided by the user
     * @param exercise             the programming exercise containing the repository the user tries to access
     * @param localVCRepositoryUri the URI of the local version control repository the user tries to access
     * @return {@code true} if the authentication is successful, {@code false} otherwise
     */
    private boolean tryAuthenticationWithParticipationVCSAccessToken(User user, String providedToken, ProgrammingExercise exercise, LocalVCRepositoryUri localVCRepositoryUri)
            throws LocalVCAuthException {

        // Note: we first check if the user has used a vcs access token instead of a password
        if (providedToken.startsWith(TOKEN_PREFIX) && providedToken.length() == VCS_ACCESS_TOKEN_LENGTH) {
            try {

                // check participation vcs access token
                List<ProgrammingExerciseStudentParticipation> participations;
                Optional<ProgrammingExerciseStudentParticipation> studentParticipation;
                if (exercise.isTeamMode()) {
                    studentParticipation = programmingExerciseParticipationService.findTeamParticipationByExerciseAndUser(exercise, user);
                }
                else {
                    participations = programmingExerciseParticipationService.findStudentParticipationsByExerciseAndStudentId(exercise, user.getLogin());
                    studentParticipation = participations.stream().filter(participation -> participation.getRepositoryUri().equals(localVCRepositoryUri.toString())).findAny();
                }
                if (studentParticipation.isPresent()) {
                    var storedToken = participationVCSAccessTokenRepository.findByUserIdAndParticipationId(user.getId(), studentParticipation.get().getId());
                    if (storedToken.isPresent() && Objects.equals(storedToken.get().getVcsAccessToken(), providedToken)) {
                        user.setVcsAccessToken(storedToken.get().getVcsAccessToken());
                        return true;
                    }
                }
            }
            catch (EntityNotFoundException e) {
                throw new LocalVCAuthException();
            }
        }
        return false;
    }

    /**
     * Determines whether a user is allowed to force-push to a certain repository.
     *
     * @param user       The user that wants to force-push to the repository.
     * @param repository The repository the user wants to force-push to.
     * @return true if the user is allowed to force-push to the repository, false otherwise.
     */
    public boolean isUserAllowedToForcePush(User user, Repository repository) {
        LocalVCRepositoryUri localVCRepositoryUri = parseRepositoryUri(repository.getDirectory().toPath());
        String projectKey = localVCRepositoryUri.getProjectKey();
        String repositoryTypeOrUserName = localVCRepositoryUri.getRepositoryTypeOrUserName();

        ProgrammingExercise exercise = getProgrammingExerciseOrThrow(projectKey);

        return repositoryAccessService.checkHasAccessToForcePush(exercise, user, repositoryTypeOrUserName);
    }

    /**
     * Checks if branching is allowed for the exercise to which the given repository belongs.
     *
     * @param repository The repository for which we check if branching is allowed.
     * @return True if branching is allowed, false otherwise.
     */
    public boolean isBranchingAllowedForRepository(Repository repository) {
        LocalVCRepositoryUri localVCRepositoryUri = parseRepositoryUri(repository.getDirectory().toPath());
        String projectKey = localVCRepositoryUri.getProjectKey();

        ProgrammingExercise exercise = getProgrammingExerciseOrThrow(projectKey, true);
        return exercise.getBuildConfig().isAllowBranching();
    }

    public static enum BranchingStatus {
        BRANCHING_DISABLED, NAME_DOES_NOT_MATCH_REGEX, BRANCH_ALLOWED
    }

    /**
     * Checks if branching is allowed for the exercise to which the given repository belongs.
     *
     * @param repository The repository for which we check if branching is allowed.
     * @param branchName The branch name for which to check if it matches the regex.
     * @return Whether branching is allowed or why it is not.
     */
    public BranchingStatus isBranchNameAllowedForRepository(Repository repository, String branchName) {
        LocalVCRepositoryUri localVCRepositoryUri = parseRepositoryUri(repository.getDirectory().toPath());
        String projectKey = localVCRepositoryUri.getProjectKey();

        ProgrammingExercise exercise = getProgrammingExerciseOrThrow(projectKey, true);

        if (!exercise.getBuildConfig().isAllowBranching() || exercise.getBuildConfig().getBranchRegex() == null) {
            return BranchingStatus.BRANCHING_DISABLED;
        }

        Pattern pattern;
        try {
            pattern = Pattern.compile(exercise.getBuildConfig().getBranchRegex());
        }
        catch (PatternSyntaxException e) {
            return BranchingStatus.NAME_DOES_NOT_MATCH_REGEX;
        }

        return pattern.matcher(branchName).matches() ? BranchingStatus.BRANCH_ALLOWED : BranchingStatus.NAME_DOES_NOT_MATCH_REGEX;
    }

    public LocalVCRepositoryUri parseRepositoryUri(HttpServletRequest request) {
        String path = request.getRequestURI();
        String normalizedPath = path.replaceFirst("/(info/refs|git-(upload|receive)-pack)$", "");
        return new LocalVCRepositoryUri(localVCBaseUri, Path.of(normalizedPath));
    }

    private LocalVCRepositoryUri parseRepositoryUri(Path repositoryPath) {
        return new LocalVCRepositoryUri(localVCBaseUri, repositoryPath);
    }

    private ProgrammingExercise getProgrammingExerciseOrThrow(String projectKey, boolean withBuildConfig) {
        try {
            return programmingExerciseRepository.findOneByProjectKeyOrThrow(projectKey, true, withBuildConfig);
        }
        catch (EntityNotFoundException e) {
            throw new LocalVCInternalException("Could not find single programming exercise with project key " + projectKey, e);
        }
    }

    private ProgrammingExercise getProgrammingExerciseOrThrow(String projectKey) {
        return getProgrammingExerciseOrThrow(projectKey, false);
    }

    /**
     * Extracts the username and password from a Basic Authorization header.
     *
     * @param authorizationHeader the authorization header containing Basic credentials
     * @return a {@link UsernameAndPassword} object with the extracted username and password
     * @throws LocalVCAuthException if the header is missing, invalid, or improperly formatted
     */
    private UsernameAndPassword extractUsernameAndPassword(String authorizationHeader) throws LocalVCAuthException {
        if (authorizationHeader == null) {
            throw new LocalVCAuthException("No authorization header provided");
        }
        String[] basicAuthCredentialsEncoded = authorizationHeader.split(" ");

        if (!("Basic".equals(basicAuthCredentialsEncoded[0]))) {
            throw new LocalVCAuthException("Non basic authorization header provided");
        }

        // Return decoded basic auth credentials which contain the username and the password.
        String basicAuthCredentials = new String(Base64.getDecoder().decode(basicAuthCredentialsEncoded[1]));

        int separatorIndex = basicAuthCredentials.indexOf(":");

        if (separatorIndex == -1) {
            throw new LocalVCAuthException();
        }
        String username = basicAuthCredentials.substring(0, separatorIndex);
        String password = basicAuthCredentials.substring(separatorIndex + 1);

        return new UsernameAndPassword(username, password);
    }

    /**
     * Authorize a user to access a certain repository.
     *
     * @param repositoryTypeOrUserName The type of the repository or the username of the user.
     * @param user                     The user that wants to access the repository.
     * @param exercise                 The exercise the repository belongs to.
     * @param repositoryActionType     The type of the action the user wants to perform.
     * @param localVCRepositoryUri     The URI of the local repository.
     * @param usingSSH                 The flag specifying whether the method is called from the SSH or HTTPs context
     * @return the ProgrammingParticipation Optional, containing the fetched participation
     * @throws LocalVCForbiddenException If the user is not allowed to access the repository.
     */
    public Optional<ProgrammingExerciseParticipation> authorizeUser(String repositoryTypeOrUserName, User user, ProgrammingExercise exercise,
            RepositoryActionType repositoryActionType, LocalVCRepositoryUri localVCRepositoryUri, boolean usingSSH) throws LocalVCForbiddenException {

        if (checkIfRepositoryIsAuxiliaryOrTestRepository(exercise, repositoryTypeOrUserName, repositoryActionType, user)) {
            return Optional.empty();
        }

        ProgrammingExerciseParticipation participation = tryToLoadParticipation(usingSSH, repositoryTypeOrUserName, localVCRepositoryUri, exercise);

        checkAccessForRepository(participation, user, exercise, repositoryActionType);

        return Optional.of(participation);
    }

    /**
     * Retrieves a user based on the provided authorization header.
     *
     * @param authorizationHeader the authorization header containing Basic credentials
     * @return the {@link User}
     * @throws LocalVCAuthException if the user could not be found or if the authorization header is invalid
     */
    public User getUserByAuthHeader(String authorizationHeader) throws LocalVCAuthException {
        UsernameAndPassword usernameAndPassword = extractUsernameAndPassword(authorizationHeader);
        String username = usernameAndPassword.username();
        return userRepository.findOneByLogin(username).orElseThrow(LocalVCAuthException::new);
    }

    /**
     * Attempts to load a programming exercise participation based on the provided parameters.
     *
     * @param usingSSH                 {@code true} if the user's session is over SSH, {@code false} if over HTTP
     * @param repositoryTypeOrUserName A string representing either the repository type or the username associated with the repository.
     * @param localVCRepositoryUri     The local version control repository URI.
     * @param exercise                 The programming exercise for which participation is being fetched.
     * @return The fetched {@link ProgrammingExerciseParticipation} instance.
     * @throws LocalVCInternalException If no participation is found and it is not an auxiliary repository.
     */
    private ProgrammingExerciseParticipation tryToLoadParticipation(boolean usingSSH, String repositoryTypeOrUserName, LocalVCRepositoryUri localVCRepositoryUri,
            ProgrammingExercise exercise) throws LocalVCInternalException {
        ProgrammingExerciseParticipation participation;
        try {
            if (usingSSH) {
                participation = programmingExerciseParticipationService.fetchParticipationWithSubmissionsByRepository(repositoryTypeOrUserName, localVCRepositoryUri.toString(),
                        exercise);
            }
            else {
                participation = programmingExerciseParticipationService.fetchParticipationByRepository(repositoryTypeOrUserName, localVCRepositoryUri.toString(), exercise);
            }
        }
        catch (EntityNotFoundException e) {
            // If the repository was not found, this could mean it is an auxiliary repository (which do not have participations)
            if (auxiliaryRepositoryService.isAuxiliaryRepositoryOfExercise(repositoryTypeOrUserName, exercise)) {
                return programmingExerciseParticipationService.findSolutionParticipationByProgrammingExerciseId(exercise.getId());
            }
            throw new LocalVCInternalException(
                    "No participation found for repository with repository type or username " + repositoryTypeOrUserName + " in exercise " + exercise.getId(), e);
        }
        return participation;
    }

    private void checkAccessForRepository(ProgrammingExerciseParticipation participation, User user, ProgrammingExercise exercise, RepositoryActionType repositoryActionType)
            throws LocalVCForbiddenException {
        try {
            repositoryAccessService.checkAccessRepositoryElseThrow(participation, user, exercise, repositoryActionType);
        }
        catch (AccessForbiddenException e) {
            throw new LocalVCForbiddenException(e);
        }
    }

    /**
     * Checks if the provided repository is an auxiliary or test repository.
     * But: for students it only checks for test repository, and assumes the requested repository is not an auxiliary repository.
     * This avoids an unnecessary database call, and postpones the actual check to
     * {@link LocalVCServletService#tryToLoadParticipation(boolean, String, LocalVCRepositoryUri, ProgrammingExercise)}
     * and only checks it if it is really needed.
     *
     * @param exercise                 the exercise, where the repository belongs to
     * @param repositoryTypeOrUserName the type or username of the repository
     * @param repositoryActionType     the action that should be performed on of the repository
     * @param user                     the user who tries to access the repository
     * @return true if the repository is an Auxiliary or Test repository, and the user has access to it.
     *         false for students if the repository is possibly an auxiliary repository, or
     *         false for TAs if the repository is neither auxiliary nor test
     * @throws LocalVCForbiddenException if the user has no access rights for the requested repository
     */
    private boolean checkIfRepositoryIsAuxiliaryOrTestRepository(ProgrammingExercise exercise, String repositoryTypeOrUserName, RepositoryActionType repositoryActionType,
            User user) throws LocalVCForbiddenException {

        // Students are not able to access Test or Aux repositories.
        // To save on db queries we do not check whether it is an Aux repo here, as we would need to fetch them first.
        try {
            repositoryAccessService.checkAccessTestOrAuxRepositoryElseThrow(false, exercise, user, repositoryTypeOrUserName);
        }
        catch (AccessForbiddenException e) {
            if (repositoryTypeOrUserName.equals(RepositoryType.TESTS.toString())) {
                throw new LocalVCForbiddenException(e);
            }
            // The user is a student, and the repository is not a test repository
            return false;
        }

        // Here we only check if the repository is an auxiliary repository if the user is at least TA.
        // Why? If the requested repository is not an auxiliary repo, we do not need to load auxiliary repositories
        if (auxiliaryRepositoryService.isAuxiliaryRepositoryOfExercise(repositoryTypeOrUserName, exercise) || repositoryTypeOrUserName.equals(RepositoryType.TESTS.toString())) {
            try {
                repositoryAccessService.checkAccessTestOrAuxRepositoryElseThrow(repositoryActionType == RepositoryActionType.WRITE, exercise, user, repositoryTypeOrUserName);
            }
            catch (AccessForbiddenException e) {
                throw new LocalVCForbiddenException(e);
            }
            // The user is at least TA, it is either an Auxiliary repository or a Test repository, and the user has access to it
            return true;
        }
        // The repository is neither an Auxiliary repository nor a Test repository
        return false;
    }

    /**
     * When cloning/pushing with SSH we can keep data loaded inside the SSH session, to avoid unnecessary database queries.
     *
     * @param user                    the user accessing the repository
     * @param optionalParticipation   the participation associated with the repository
     * @param repositoryActionType    the action performed on the repository (READ or WRITE)
     * @param authenticationMechanism the mechanism used for authentication (e.g., token, basic auth)
     * @param ipAddress               the IP address of the user accessing the repository
     * @param localVCRepositoryUri    the URI of the localVC repository
     * @param serverSession           the SSH serverSession, where the data gets stored
     */
    public void cacheAttributesInSshSession(User user, Optional<ProgrammingExerciseParticipation> optionalParticipation, RepositoryActionType repositoryActionType,
            AuthenticationMechanism authenticationMechanism, String ipAddress, LocalVCRepositoryUri localVCRepositoryUri, ServerSession serverSession) {
        if (optionalParticipation.isPresent()) {
            ProgrammingExerciseParticipation participation = optionalParticipation.get();
            try {
                String commitHash;
                String relativeRepositoryPath = localVCRepositoryUri.getRelativeRepositoryPath().toString();
                try (Repository repository = resolveRepository(relativeRepositoryPath)) {
                    commitHash = getLatestCommitHash(repository);
                }

                var finalRepositoryActionType = repositoryActionType == RepositoryActionType.READ ? RepositoryActionType.PULL : RepositoryActionType.PUSH;
                var preliminaryAccessLog = new VcsAccessLog(user, (Participation) participation, user.getName(), user.getEmail(), finalRepositoryActionType,
                        authenticationMechanism, commitHash, ipAddress);

                serverSession.setAttribute(SshConstants.VCS_ACCESS_LOG_KEY, preliminaryAccessLog);
                serverSession.setAttribute(SshConstants.PARTICIPATION_KEY, participation);
            }
            catch (Exception e) {
                log.warn("Failed to obtain commit hash or store access log for repository {}. Error: {}", localVCRepositoryUri.getRelativeRepositoryPath().toString(),
                        e.getMessage());
            }
        }
    }

    /**
     * Returns the HTTP status code for the given exception thrown by the above method "authenticateAndAuthorizeGitRequest".
     *
     * @param exception     The exception thrown.
     * @param repositoryUri The URL of the repository that was accessed.
     * @return The HTTP status code.
     */
    public int getHttpStatusForException(Exception exception, String repositoryUri) {
        switch (exception) {
            case LocalVCAuthException _ -> {
                return HttpStatus.UNAUTHORIZED.value();
            }
            case LocalVCForbiddenException _ -> {
                return HttpStatus.FORBIDDEN.value();
            }
            case RateLimitExceededException _ -> {
                return HttpStatus.TOO_MANY_REQUESTS.value();
            }
            default -> {
                log.error("Internal server error while trying to access repository {}: {}", repositoryUri, exception.getMessage(), exception);
                return HttpStatus.INTERNAL_SERVER_ERROR.value();
            }
        }
    }

    /**
     * Create a submission, trigger the respective build, and process the results.
     * This method can be called with some values, to avoid loading them again from the database
     *
     * @param commitHash          the hash of the last commit.
     * @param repository          the remote repository which was pushed to.
     * @param user                the user who pushed the commit, used for logging and access control.
     * @param cachedExercise      the exercise which is potentially already loaded
     * @param cachedParticipation the participation which is potentially already loaded
     * @param vcsAccessLog        the vcsAccessLog which is potentially already loaded
     * @throws ContinuousIntegrationException if something goes wrong with the CI configuration.
     * @throws VersionControlException        if the commit belongs to the wrong branch (i.e. not the default branch of the participation).
     */
    public void processNewPush(String commitHash, Repository repository, User user, Optional<ProgrammingExercise> cachedExercise,
            Optional<ProgrammingExerciseParticipation> cachedParticipation, Optional<VcsAccessLog> vcsAccessLog) {
        long timeNanoStart = System.nanoTime();

        Path repositoryFolderPath = repository.getDirectory().toPath();

        LocalVCRepositoryUri localVCRepositoryUri = getLocalVCRepositoryUri(repositoryFolderPath);

        String repositoryTypeOrUserName = localVCRepositoryUri.getRepositoryTypeOrUserName();
        String projectKey = localVCRepositoryUri.getProjectKey();
        ProgrammingExercise exercise = cachedExercise.orElseGet(() -> getProgrammingExercise(projectKey));
        ProgrammingExerciseParticipation participation;
        RepositoryType repositoryType = getRepositoryTypeWithoutAuxiliary(repositoryTypeOrUserName);

        try {
            participation = cachedParticipation.orElseGet(() -> programmingExerciseParticipationService
                    .fetchParticipationWithSubmissionsByRepository(localVCRepositoryUri.getRepositoryTypeOrUserName(), localVCRepositoryUri.toString(), exercise));
        }
        catch (EntityNotFoundException e) {
            repositoryType = getRepositoryType(repositoryTypeOrUserName, exercise);
            if (repositoryType.equals(RepositoryType.AUXILIARY) || repositoryType.equals(RepositoryType.TESTS)) {
                participation = retrieveSolutionParticipation(exercise);
            }
            else {
                throw new VersionControlException("Could not find participation for repository", e);
            }
        }

        try {
            if (exerciseVersionService.isRepositoryTypeVersionable(repositoryType)) {
                exerciseVersionService.createExerciseVersion(exercise, user);
            }

            if (repositoryType.equals(RepositoryType.TESTS)) {
                processNewPushToTestOrAuxRepository(exercise, commitHash, (SolutionProgrammingExerciseParticipation) participation, repositoryType);
                return;
            }

            if (repositoryType.equals(RepositoryType.AUXILIARY)) {
                // Don't provide a commit hash because we want the latest test repo commit to be used
                processNewPushToTestOrAuxRepository(exercise, null, (SolutionProgrammingExerciseParticipation) participation, repositoryType);
                return;
            }

            if (commitHash == null) {
                commitHash = getLatestCommitHash(repository);
            }

            Commit commit = extractCommitInfo(commitHash, repository);

            // Process push to any repository other than the test repository.
            processNewPushToRepository(participation, commit, user);

            // For push the correct commitHash is only available here, therefore the preliminary value is overwritten
            String finalCommitHash = commitHash;
            if (vcsAccessLog.isPresent()) {
                vcsAccessLog.get().setCommitHash(finalCommitHash);
                vcsAccessLogService.ifPresent(service -> service.saveVcsAccesslog(vcsAccessLog.get()));
            }
            else {
                var finalParticipation = participation;
                vcsAccessLogService.ifPresent(service -> service.updateCommitHash(finalParticipation, finalCommitHash));
            }
        }
        catch (GitAPIException | IOException e) {
            // This catch clause does not catch exceptions that happen during runBuildJob() as that method is called asynchronously.
            // For exceptions happening inside runBuildJob(), the user is notified. See the addBuildJobToQueue() method in the LocalCIBuildJobManagementService for that.
            throw new VersionControlException(
                    "Could not process new push to repository " + localVCRepositoryUri.getURI() + " and commit " + commitHash + ". No build job was queued.", e);
        }

        log.debug("New push processed to repository {} for commit {} in {}. A build job was queued.", localVCRepositoryUri.getURI(), commitHash,
                TimeLogUtil.formatDurationFrom(timeNanoStart));
    }

    private ProgrammingExerciseParticipation retrieveSolutionParticipation(ProgrammingExercise exercise) {
        return programmingExerciseParticipationService.retrieveSolutionParticipation(exercise);
    }

    private ProgrammingExercise getProgrammingExercise(String projectKey) {
        ProgrammingExercise exercise;
        try {
            exercise = programmingExerciseRepository.findOneByProjectKeyOrThrow(projectKey, false);
        }
        catch (EntityNotFoundException e) {
            throw new VersionControlException("Could not find programming exercise for project key " + projectKey, e);
        }
        return exercise;
    }

    private LocalVCRepositoryUri getLocalVCRepositoryUri(Path repositoryFolderPath) {
        try {
            return new LocalVCRepositoryUri(localVCBaseUri, repositoryFolderPath);
        }
        catch (LocalVCInternalException e) {
            // This means something is misconfigured.
            throw new VersionControlException("Could not create valid repository URI from path " + repositoryFolderPath, e);
        }
    }

    private String getLatestCommitHash(Repository repository) throws GitAPIException {
        try (Git git = new Git(repository)) {
            RevCommit latestCommit = git.log().setMaxCount(1).call().iterator().next();
            return latestCommit.getName();
        }
    }

    /**
     * Process a new push to the test repository.
     * Build and test the solution repository to make sure all tests are still passing.
     *
     * @param exercise       the exercise for which the push was made.
     * @param commitHash     the hash of the commit used as the last commit to the test repository.
     * @param repositoryType type of repository that has been pushed to
     * @throws VersionControlException if something unexpected goes wrong when creating the submission or triggering the build.
     */
    private void processNewPushToTestOrAuxRepository(ProgrammingExercise exercise, String commitHash, SolutionProgrammingExerciseParticipation solutionParticipation,
            RepositoryType repositoryType) throws VersionControlException {
        // Create a new submission for the solution repository.
        ProgrammingSubmission submission = getProgrammingSubmission(exercise, commitHash);

        programmingSubmissionMessagingService.notifyUserAboutSubmission(submission, exercise.getId());

        if (repositoryType.equals(RepositoryType.TESTS)) {
            try {
                // Set a flag to inform the instructor that the student results are now outdated.
                programmingExerciseTestCaseChangedService.setTestCasesChanged(exercise.getId(), true);
            }
            catch (EntityNotFoundException e) {
                throw new VersionControlException("Could not set test cases changed flag", e);
            }
        }

        // Trigger the build for the solution repository.
        // The template repository will be built, once the result for the solution repository is available. See LocalCIResultProcessingService.
        ciTriggerService.triggerBuild(solutionParticipation, commitHash, repositoryType);
    }

    private ProgrammingSubmission getProgrammingSubmission(ProgrammingExercise exercise, String commitHash) {
        ProgrammingSubmission submission;
        try {
            submission = programmingSubmissionService.createSolutionParticipationSubmissionWithTypeTest(exercise.getId(), commitHash);
        }
        catch (EntityNotFoundException | IllegalStateException e) {
            throw new VersionControlException("Could not create submission for solution participation", e);
        }
        return submission;
    }

    private RepositoryType getRepositoryType(String repositoryTypeOrUserName, ProgrammingExercise exercise) {
        if (repositoryTypeOrUserName.equals("exercise")) {
            return RepositoryType.TEMPLATE;
        }
        else if (repositoryTypeOrUserName.equals("solution")) {
            return RepositoryType.SOLUTION;
        }
        else if (repositoryTypeOrUserName.equals("tests")) {
            return RepositoryType.TESTS;
        }
        else if (auxiliaryRepositoryService.isAuxiliaryRepositoryOfExercise(repositoryTypeOrUserName, exercise)) {
            return RepositoryType.AUXILIARY;
        }
        else {
            return RepositoryType.USER;
        }
    }

    private RepositoryType getRepositoryTypeWithoutAuxiliary(String repositoryTypeOrUserName) {
        return switch (repositoryTypeOrUserName) {
            case "exercise" -> RepositoryType.TEMPLATE;
            case "solution" -> RepositoryType.SOLUTION;
            case "tests" -> RepositoryType.TESTS;
            default -> RepositoryType.USER;
        };
    }

    /**
     * TODO: this could be done asynchronously to shorten the duration of the push operation
     * Process a new push to a student's repository or to the template or solution repository of the exercise.
     *
     * @param participation the participation for which the push was made
     * @param commit        the commit that was pushed
     * @param user          the user who pushed the commit, used for logging and access control
     * @throws VersionControlException if the commit belongs to the wrong branch (i.e. not the default branch of the participation)
     */
    private void processNewPushToRepository(ProgrammingExerciseParticipation participation, Commit commit, User user) {
        // The 'user' is not properly logged into Artemis, this leads to an issue when accessing custom repository methods.
        // Therefore, a mock auth object has to be created.
        SecurityUtils.setAuthorizationObject();
        ProgrammingSubmission submission;
        try {
            submission = programmingSubmissionService.processNewProgrammingSubmission(participation, commit, user);
        }
        catch (EntityNotFoundException | IllegalStateException | IllegalArgumentException e) {
            throw new VersionControlException("Could not process submission for participation: " + e.getMessage(), e);
        }

        // Remove unnecessary information from the new submission.
        submission.getParticipation().setSubmissions(null);
        programmingSubmissionMessagingService.notifyUserAboutSubmission(submission, participation.getExercise().getId());
    }

    private Commit extractCommitInfo(String commitHash, Repository repository) throws IOException, GitAPIException, VersionControlException {
        RevCommit revCommit;
        String branch = null;

        ObjectId objectId = repository.resolve(commitHash);

        if (objectId == null) {
            throw new VersionControlException("Could not resolve commit hash " + commitHash + " in repository");
        }

        revCommit = repository.parseCommit(objectId);

        // Get the branch name.
        try (Git git = new Git(repository)) {
            // Look in the 'refs/heads' namespace for a ref that points to the commit.
            // The returned map contains at most one entry where the key is the commit id and the value denotes the branch which points to it.
            Map<ObjectId, String> objectIdBranchNameMap = git.nameRev().addPrefix("refs/heads").add(objectId).call();
            if (!objectIdBranchNameMap.isEmpty()) {
                branch = objectIdBranchNameMap.get(objectId);
            }
        }

        if (revCommit == null || branch == null) {
            throw new VersionControlException("Something went wrong retrieving the revCommit or the branch.");
        }

        var author = revCommit.getAuthorIdent();
        return new Commit(commitHash, author.getName(), revCommit.getFullMessage(), author.getEmailAddress(), branch);
    }

    /**
     * Updates the VCS (Version Control System) access log for clone and pull actions using HTTPS.
     * <p>
     * This method logs the access information based on the incoming HTTP request. It checks if the action
     * is performed by a build job user and, if not, records the user's repository action (clone or pull).
     * The action type is determined based on the number of offers (`clientOffered`).
     *
     * @param request             the request from the user
     * @param authorizationHeader the authorization header containing the user's credentials
     * @param clientOffered       the number of objects offered by the client in the operation, used to determine
     *                                if the action is a clone (if 0) or a pull (if greater than 0).
     */
    public void updateAndStoreVCSAccessLogForCloneAndPullHTTPS(HttpServletRequest request, String authorizationHeader, int clientOffered) {
        if (!request.getMethod().equals("POST")) {
            return;
        }
        try {
            UsernameAndPassword usernameAndPassword = extractUsernameAndPassword(authorizationHeader);
            String userName = usernameAndPassword.username();
            if (userName.equals(BUILD_USER_NAME)) {
                return;
            }
            LocalVCRepositoryUri localVCRepositoryUri = parseRepositoryUri(request);
            RepositoryActionType repositoryActionType = getRepositoryActionReadType(clientOffered);

            vcsAccessLogService.ifPresent(service -> service.updateRepositoryActionType(localVCRepositoryUri, repositoryActionType));
        }
        catch (Exception ignored) {
        }
    }

    /**
     * Updates the VCS access log for clone and pull actions performed over SSH.
     * <p>
     * This method logs access information based on the SSH session and the root directory of the repository.
     * It determines the repository action (clone or pull) based on the number of offers (`clientOffered`) and
     * fetches participation details from the local VC repository URI.
     *
     * @param session       the {@link ServerSession} representing the SSH session.
     * @param clientOffered the number of objects offered by the client in the operation, used to determine
     *                          if the action is a clone (if 0) or a pull (if greater than 0).
     */
    public void updateAndStoreVCSAccessLogForCloneAndPullSSH(ServerSession session, int clientOffered) {
        try {
            if (session.getAttribute(SshConstants.USER_KEY).getName().equals(BUILD_USER_NAME)) {
                return;
            }
            var accessLog = session.getAttribute(SshConstants.VCS_ACCESS_LOG_KEY);
            RepositoryActionType repositoryActionType = getRepositoryActionReadType(clientOffered);
            accessLog.setRepositoryActionType(repositoryActionType);
            vcsAccessLogService.ifPresent(service -> service.saveVcsAccesslog(accessLog));
        }
        catch (Exception ignored) {
        }
    }

    /**
     * Adds a failed VCS access attempt to the log.
     * <p>
     * This method logs a failed clone attempt, associating it with the user and participation retrieved
     * from the incoming HTTP request.
     *
     * @param servletRequest the {@link HttpServletRequest} containing the HTTP request data.
     */
    public void createVCSAccessLogForFailedAuthenticationAttempt(HttpServletRequest servletRequest) {
        try {
            String authorizationHeader = servletRequest.getHeader(HttpHeaders.AUTHORIZATION);
            UsernameAndPassword usernameAndPassword = extractUsernameAndPassword(authorizationHeader);
            User user = userRepository.findOneByLogin(usernameAndPassword.username()).orElseThrow(LocalVCAuthException::new);
            AuthenticationMechanism mechanism = usernameAndPassword.password().startsWith("vcpat-") ? AuthenticationMechanism.VCS_ACCESS_TOKEN : AuthenticationMechanism.PASSWORD;
            LocalVCRepositoryUri localVCRepositoryUri = parseRepositoryUri(servletRequest);
            var participation = programmingExerciseParticipationService.fetchParticipationWithSubmissionsByRepository(localVCRepositoryUri.getRepositoryTypeOrUserName(),
                    localVCRepositoryUri.toString(), null);
            var ipAddress = servletRequest.getRemoteAddr();
            vcsAccessLogService.ifPresent(service -> service.saveAccessLog(user, participation, RepositoryActionType.CLONE_FAIL, mechanism, "", ipAddress));
        }
        catch (LocalVCAuthException | EntityNotFoundException ignored) {
            // Caught when: 1) no user, or 2) no participation was found. In both cases it does not make sense to write a log
        }
    }

    /**
     * Determines the repository action type for read operations (clone or pull).
     * <p>
     * This method returns a {@link RepositoryActionType} based on the number of objects offered.
     * If no objects are offered (0), it is considered a clone; otherwise, it is a pull action.
     *
     * @param clientOffered the number of objects offered to the client in the operation.
     * @return the {@link RepositoryActionType} based on the number of objects offered (clone if 0, pull if greater than 0).
     */
    private RepositoryActionType getRepositoryActionReadType(int clientOffered) {
        return clientOffered == 0 ? RepositoryActionType.CLONE : RepositoryActionType.PULL;
    }

    record UsernameAndPassword(String username, String password) {
    }
}
