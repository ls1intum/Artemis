package de.tum.cit.aet.artemis.programming.service.localvc;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALVC;
import static de.tum.cit.aet.artemis.programming.service.localvc.LocalVCPersonalAccessTokenManagementService.TOKEN_PREFIX;
import static de.tum.cit.aet.artemis.programming.service.localvc.LocalVCPersonalAccessTokenManagementService.VCS_ACCESS_TOKEN_LENGTH;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

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
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.exception.ContinuousIntegrationException;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.exception.VersionControlException;
import de.tum.cit.aet.artemis.core.exception.localvc.LocalVCAuthException;
import de.tum.cit.aet.artemis.core.exception.localvc.LocalVCForbiddenException;
import de.tum.cit.aet.artemis.core.exception.localvc.LocalVCInternalException;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.SecurityUtils;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.util.TimeLogUtil;
import de.tum.cit.aet.artemis.programming.domain.AuthenticationMechanism;
import de.tum.cit.aet.artemis.programming.domain.Commit;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.domain.SolutionProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.repository.ParticipationVCSAccessTokenRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.service.AuxiliaryRepositoryService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseParticipationService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingMessagingService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingSubmissionService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingTriggerService;
import de.tum.cit.aet.artemis.programming.service.RepositoryAccessService;
import de.tum.cit.aet.artemis.programming.service.ci.ContinuousIntegrationTriggerService;
import de.tum.cit.aet.artemis.programming.service.localvc.ssh.SshConstants;
import de.tum.cit.aet.artemis.programming.web.repository.RepositoryActionType;

/**
 * This service is responsible for authenticating and authorizing git requests as well as for retrieving the requested Git repositories from disk.
 * It is used by the ArtemisGitServletService, the LocalVCFetchFilter, and the LocalVCPushFilter.
 */
@Service
@Profile(PROFILE_LOCALVC)
// TODO: we should rename this because its used in the context of https and ssh git operations
public class LocalVCServletService {

    private static final Logger log = LoggerFactory.getLogger(LocalVCServletService.class);

    private final AuthenticationManager authenticationManager;

    private final UserRepository userRepository;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final RepositoryAccessService repositoryAccessService;

    private final AuthorizationCheckService authorizationCheckService;

    private final ProgrammingExerciseParticipationService programmingExerciseParticipationService;

    private final AuxiliaryRepositoryService auxiliaryRepositoryService;

    private final ContinuousIntegrationTriggerService ciTriggerService;

    private final ProgrammingSubmissionService programmingSubmissionService;

    private final ProgrammingMessagingService programmingMessagingService;

    private final ProgrammingTriggerService programmingTriggerService;

    // TODO As soon as only LocalVC is supported, this Optional can be removed
    private final Optional<VcsAccessLogService> vcsAccessLogService;

    private static URL localVCBaseUrl;

    private final ParticipationVCSAccessTokenRepository participationVCSAccessTokenRepository;

    @Value("${artemis.version-control.url}")
    public void setLocalVCBaseUrl(URL localVCBaseUrl) {
        LocalVCServletService.localVCBaseUrl = localVCBaseUrl;
    }

    @Value("${artemis.version-control.local-vcs-repo-path}")
    private String localVCBasePath;

    @Value("${artemis.version-control.build-agent-git-username}")
    private String buildAgentGitUsername;

    @Value("${artemis.version-control.build-agent-git-password}")
    private String buildAgentGitPassword;

    /**
     * Name of the header containing the authorization information.
     */
    public static final String AUTHORIZATION_HEADER = "Authorization";

    // Cache the retrieved repositories for quicker access.
    // The resolveRepository method is called multiple times per request.
    // Key: repositoryPath --> Value: Repository
    private final Map<String, Repository> repositories = new HashMap<>();

    public LocalVCServletService(AuthenticationManager authenticationManager, UserRepository userRepository, ProgrammingExerciseRepository programmingExerciseRepository,
            RepositoryAccessService repositoryAccessService, AuthorizationCheckService authorizationCheckService,
            ProgrammingExerciseParticipationService programmingExerciseParticipationService, AuxiliaryRepositoryService auxiliaryRepositoryService,
            ContinuousIntegrationTriggerService ciTriggerService, ProgrammingSubmissionService programmingSubmissionService,
            ProgrammingMessagingService programmingMessagingService, ProgrammingTriggerService programmingTriggerService,
            ParticipationVCSAccessTokenRepository participationVCSAccessTokenRepository, Optional<VcsAccessLogService> vcsAccessLogService) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.repositoryAccessService = repositoryAccessService;
        this.authorizationCheckService = authorizationCheckService;
        this.programmingExerciseParticipationService = programmingExerciseParticipationService;
        this.auxiliaryRepositoryService = auxiliaryRepositoryService;
        this.ciTriggerService = ciTriggerService;
        this.programmingSubmissionService = programmingSubmissionService;
        this.programmingMessagingService = programmingMessagingService;
        this.programmingTriggerService = programmingTriggerService;
        this.participationVCSAccessTokenRepository = participationVCSAccessTokenRepository;
        this.vcsAccessLogService = vcsAccessLogService;
    }

    /**
     * Resolves the repository for the given path by first trying to use a cached one.
     * If the cache does not hit, it creates a JGit repository and opens the local repository.
     *
     * @param repositoryPath the path of the repository, as parsed out of the URL (everything after /git).
     * @return the opened repository instance.
     * @throws RepositoryNotFoundException if the repository could not be found.
     */
    public Repository resolveRepository(String repositoryPath) throws RepositoryNotFoundException {

        long timeNanoStart = System.nanoTime();
        // Find the local repository depending on the name.
        Path repositoryDir = Paths.get(localVCBasePath, repositoryPath);

        log.debug("Path to resolve repository from: {}", repositoryDir);
        if (!Files.exists(repositoryDir)) {
            log.error("Could not find local repository with name {}", repositoryPath);
            throw new RepositoryNotFoundException(repositoryPath);
        }

        if (repositories.containsKey(repositoryPath)) {
            log.debug("Retrieving cached local repository {}", repositoryPath);
            Repository repository = repositories.get(repositoryPath);
            repository.incrementOpen();
            log.debug("Resolving repository for repository {} took {}", repositoryPath, TimeLogUtil.formatDurationFrom(timeNanoStart));
            return repository;
        }
        else {
            log.debug("Opening local repository {}", repositoryPath);
            try (Repository repository = FileRepositoryBuilder.create(repositoryDir.toFile())) {
                // Enable pushing without credentials, authentication is handled by the LocalVCPushFilter.
                repository.getConfig().setBoolean("http", null, "receivepack", true);

                this.repositories.put(repositoryPath, repository);
                repository.incrementOpen();
                log.debug("Resolving repository for repository {} took {}", repositoryPath, TimeLogUtil.formatDurationFrom(timeNanoStart));
                return repository;
            }
            catch (IOException e) {
                log.error("Unable to open local repository {}", repositoryPath);
                throw new RepositoryNotFoundException(repositoryPath, e);
            }
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

        String authorizationHeader = request.getHeader(LocalVCServletService.AUTHORIZATION_HEADER);

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

        LocalVCRepositoryUri localVCRepositoryUri = parseRepositoryUri(request);
        String projectKey = localVCRepositoryUri.getProjectKey();
        String repositoryTypeOrUserName = localVCRepositoryUri.getRepositoryTypeOrUserName();

        ProgrammingExercise exercise = getProgrammingExerciseOrThrow(projectKey);

        User user = authenticateUser(authorizationHeader, exercise, localVCRepositoryUri);

        // Check that offline IDE usage is allowed.
        if (Boolean.FALSE.equals(exercise.isAllowOfflineIde()) && authorizationCheckService.isOnlyStudentInCourse(exercise.getCourseViaExerciseGroupOrCourseMember(), user)) {
            throw new LocalVCForbiddenException();
        }

        var authenticationMechanism = resolveAuthenticationMechanism(authorizationHeader, user);
        var ipAddress = request.getRemoteAddr();
        authorizeUser(repositoryTypeOrUserName, user, exercise, repositoryAction, authenticationMechanism, ipAddress, localVCRepositoryUri);

        request.setAttribute("user", user);

        log.debug("Authorizing user {} for repository {} took {}", user.getLogin(), localVCRepositoryUri, TimeLogUtil.formatDurationFrom(timeNanoStart));
    }

    /**
     * Resolves the user's authentication mechanism for the repository
     *
     * @param authorizationHeader the request's authorizationHeader, containing the token or password
     * @param user                the user
     * @return the authentication type
     * @throws LocalVCAuthException if extracting the token or password from the authorizationHeader fails
     */
    private AuthenticationMechanism resolveAuthenticationMechanism(String authorizationHeader, User user) throws LocalVCAuthException {
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

    private User authenticateUser(String authorizationHeader, ProgrammingExercise exercise, LocalVCRepositoryUri localVCRepositoryUri)
            throws LocalVCAuthException, AuthenticationException {

        UsernameAndPassword usernameAndPassword = extractUsernameAndPassword(authorizationHeader);

        String username = usernameAndPassword.username();
        String password = usernameAndPassword.password();
        User user = userRepository.findOneByLogin(username).orElseThrow(LocalVCAuthException::new);

        try {
            SecurityUtils.checkUsernameAndPasswordValidity(username, password);
        }
        catch (AccessForbiddenException | AuthenticationException e) {
            if (!StringUtils.isEmpty(password)) {
                log.warn("Failed login attempt for user {} with password {} due to issue: {}", username, password, e.getMessage());
            }
            throw new LocalVCAuthException(e.getMessage());
        }

        // check user VCS access token
        if (Objects.equals(user.getVcsAccessToken(), password) && user.getVcsAccessTokenExpiryDate() != null && user.getVcsAccessTokenExpiryDate().isAfter(ZonedDateTime.now())) {
            return user;
        }

        // Note: we first check if the user has used a vcs access token instead of a password
        if (password.startsWith(TOKEN_PREFIX) && password.length() == VCS_ACCESS_TOKEN_LENGTH) {
            try {

                // check participation vcs access token
                // var part = programmingExerciseParticipationService.findTeamParticipationByExerciseAndTeamShortNameOrThrow()
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
                    var token = participationVCSAccessTokenRepository.findByUserIdAndParticipationId(user.getId(), studentParticipation.get().getId());
                    if (token.isPresent() && Objects.equals(token.get().getVcsAccessToken(), password)) {
                        user.setVcsAccessToken(token.get().getVcsAccessToken());
                        return user;
                    }
                }
            }
            catch (EntityNotFoundException e) {
                throw new LocalVCAuthException();
            }
        }

        // if the user does not have an access token or has used a password, we try to authenticate the user with it

        // Try to authenticate the user based on the configured options, this can include sending the data to an external system (e.g. LDAP) or using internal authentication.
        UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(username, password);
        authenticationManager.authenticate(authenticationToken);

        return user;
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

        boolean isAllowedRepository = repositoryTypeOrUserName.equals(RepositoryType.TEMPLATE.toString()) || repositoryTypeOrUserName.equals(RepositoryType.SOLUTION.toString())
                || repositoryTypeOrUserName.equals(RepositoryType.TESTS.toString());

        return isAllowedRepository && authorizationCheckService.isAtLeastEditorInCourse(exercise.getCourseViaExerciseGroupOrCourseMember(), user);
    }

    private LocalVCRepositoryUri parseRepositoryUri(HttpServletRequest request) {
        return new LocalVCRepositoryUri(request.getRequestURL().toString().replace("/info/refs", ""));
    }

    private LocalVCRepositoryUri parseRepositoryUri(Path repositoryPath) {
        return new LocalVCRepositoryUri(repositoryPath, localVCBaseUrl);
    }

    private ProgrammingExercise getProgrammingExerciseOrThrow(String projectKey) {
        try {
            return programmingExerciseRepository.findOneByProjectKeyOrThrow(projectKey, true);
        }
        catch (EntityNotFoundException e) {
            throw new LocalVCInternalException("Could not find single programming exercise with project key " + projectKey, e);
        }
    }

    private String checkAuthorizationHeader(String authorizationHeader) throws LocalVCAuthException {
        if (authorizationHeader == null) {
            throw new LocalVCAuthException("No authorization header provided");
        }

        String[] basicAuthCredentialsEncoded = authorizationHeader.split(" ");

        if (!("Basic".equals(basicAuthCredentialsEncoded[0]))) {
            throw new LocalVCAuthException();
        }

        // Return decoded basic auth credentials which contain the username and the password.
        return new String(Base64.getDecoder().decode(basicAuthCredentialsEncoded[1]));
    }

    private UsernameAndPassword extractUsernameAndPassword(String authorizationHeader) throws LocalVCAuthException {
        String basicAuthCredentials = checkAuthorizationHeader(authorizationHeader);
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
     * @param authenticationMechanism  The authentication mechanism used by the user to authenticate to the repository
     * @param ipAddress                The ip address of the user
     * @param localVCRepositoryUri     The URI of the local repository.
     *
     * @throws LocalVCForbiddenException If the user is not allowed to access the repository.
     */
    public void authorizeUser(String repositoryTypeOrUserName, User user, ProgrammingExercise exercise, RepositoryActionType repositoryActionType,
            AuthenticationMechanism authenticationMechanism, String ipAddress, LocalVCRepositoryUri localVCRepositoryUri) throws LocalVCForbiddenException {

        if (repositoryTypeOrUserName.equals(RepositoryType.TESTS.toString()) || auxiliaryRepositoryService.isAuxiliaryRepositoryOfExercise(repositoryTypeOrUserName, exercise)) {
            // Test and auxiliary repositories are only accessible by instructors and higher.
            try {
                repositoryAccessService.checkAccessTestOrAuxRepositoryElseThrow(repositoryActionType == RepositoryActionType.WRITE, exercise, user, repositoryTypeOrUserName);
            }
            catch (AccessForbiddenException e) {
                throw new LocalVCForbiddenException(e);
            }
            return;
        }

        ProgrammingExerciseParticipation participation;
        try {
            participation = programmingExerciseParticipationService.getParticipationForRepository(exercise, repositoryTypeOrUserName, localVCRepositoryUri.isPracticeRepository(),
                    false);
        }
        catch (EntityNotFoundException e) {
            throw new LocalVCInternalException(
                    "No participation found for repository with repository type or username " + repositoryTypeOrUserName + " in exercise " + exercise.getId(), e);
        }

        try {
            repositoryAccessService.checkAccessRepositoryElseThrow(participation, user, exercise, repositoryActionType);
        }
        catch (AccessForbiddenException e) {
            throw new LocalVCForbiddenException(e);
        }
        // TODO: retrieving the git commit hash should be done ASYNC together with storing the log in the database to avoid long waiting times during permission check
        String commitHash = null;
        try {
            String relativeRepositoryPath = localVCRepositoryUri.getRelativeRepositoryPath().toString();
            try (Repository repository = resolveRepository(relativeRepositoryPath)) {
                commitHash = getLatestCommitHash(repository);
            }

            // Write a access log entry to the database
            RepositoryActionType finalRepositoryActionType = repositoryActionType == RepositoryActionType.READ ? RepositoryActionType.PULL : RepositoryActionType.PUSH;
            String finalCommitHash = commitHash;
            vcsAccessLogService.ifPresent(service -> service.storeAccessLog(user, participation, finalRepositoryActionType, authenticationMechanism, finalCommitHash, ipAddress));
        }
        // NOTE: we intentionally catch all issues here to avoid that the user is blocked from accessing the repository
        catch (Exception e) {
            log.warn("Failed to obtain commit hash or store access log for repository {}. Error: {}", localVCRepositoryUri.getRelativeRepositoryPath().toString(), e.getMessage());
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
        if (exception instanceof LocalVCAuthException) {
            return HttpStatus.UNAUTHORIZED.value();
        }
        else if (exception instanceof LocalVCForbiddenException) {
            return HttpStatus.FORBIDDEN.value();
        }
        else {
            log.error("Internal server error while trying to access repository {}: {}", repositoryUri, exception.getMessage());
            return HttpStatus.INTERNAL_SERVER_ERROR.value();
        }
    }

    /**
     * Create a submission, trigger the respective build, and process the results.
     *
     * @param commitHash the hash of the last commit.
     * @param repository the remote repository which was pushed to.
     * @throws ContinuousIntegrationException if something goes wrong with the CI configuration.
     * @throws VersionControlException        if the commit belongs to the wrong branch (i.e. not the default branch of the participation).
     */
    public void processNewPush(String commitHash, Repository repository) {
        long timeNanoStart = System.nanoTime();

        Path repositoryFolderPath = repository.getDirectory().toPath();

        LocalVCRepositoryUri localVCRepositoryUri = getLocalVCRepositoryUri(repositoryFolderPath);

        String repositoryTypeOrUserName = localVCRepositoryUri.getRepositoryTypeOrUserName();
        String projectKey = localVCRepositoryUri.getProjectKey();

        ProgrammingExercise exercise = getProgrammingExercise(projectKey);

        ProgrammingExerciseParticipation participation = getProgrammingExerciseParticipation(localVCRepositoryUri, repositoryTypeOrUserName, exercise);

        RepositoryType repositoryType = getRepositoryType(repositoryTypeOrUserName, exercise);

        try {
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
            processNewPushToRepository(participation, commit);

            try {
                // For push the correct commitHash is only available here, therefore the preliminary null value is overwritten
                String finalCommitHash = commitHash;
                vcsAccessLogService.ifPresent(service -> service.updateCommitHash(participation, finalCommitHash));
            }
            // NOTE: we intentionally catch all issues here to avoid that the user is blocked from accessing the repository
            catch (Exception e) {
                log.warn("Failed to obtain commit hash or store access log for repository {}. Error: {}", localVCRepositoryUri.getRelativeRepositoryPath().toString(),
                        e.getMessage());
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

    private ProgrammingExerciseParticipation getProgrammingExerciseParticipation(LocalVCRepositoryUri localVCRepositoryUri, String repositoryTypeOrUserName,
            ProgrammingExercise exercise) {
        ProgrammingExerciseParticipation participation;
        try {
            participation = programmingExerciseParticipationService.getParticipationForRepository(exercise, repositoryTypeOrUserName, localVCRepositoryUri.isPracticeRepository(),
                    true);
        }
        catch (EntityNotFoundException e) {
            throw new VersionControlException("Could not find participation for repository " + repositoryTypeOrUserName + " of exercise " + exercise, e);
        }
        return participation;
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

    private static LocalVCRepositoryUri getLocalVCRepositoryUri(Path repositoryFolderPath) {
        try {
            return new LocalVCRepositoryUri(repositoryFolderPath, localVCBaseUrl);
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

        programmingMessagingService.notifyUserAboutSubmission(submission, exercise.getId());

        if (repositoryType.equals(RepositoryType.TESTS)) {
            try {
                // Set a flag to inform the instructor that the student results are now outdated.
                programmingTriggerService.setTestCasesChanged(exercise.getId(), true);
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

    /**
     * TODO: this could be done asynchronously to shorten the duration of the push operation
     * Process a new push to a student's repository or to the template or solution repository of the exercise.
     *
     * @param participation the participation for which the push was made
     * @param commit        the commit that was pushed
     * @throws VersionControlException if the commit belongs to the wrong branch (i.e. not the default branch of the participation)
     */
    private void processNewPushToRepository(ProgrammingExerciseParticipation participation, Commit commit) {
        // The 'user' is not properly logged into Artemis, this leads to an issue when accessing custom repository methods.
        // Therefore, a mock auth object has to be created.
        SecurityUtils.setAuthorizationObject();
        ProgrammingSubmission submission;
        try {
            submission = programmingSubmissionService.processNewProgrammingSubmission(participation, commit);
        }
        catch (EntityNotFoundException | IllegalStateException | IllegalArgumentException e) {
            throw new VersionControlException("Could not process submission for participation: " + e.getMessage(), e);
        }

        // Remove unnecessary information from the new submission.
        submission.getParticipation().setSubmissions(null);
        programmingMessagingService.notifyUserAboutSubmission(submission, participation.getExercise().getId());
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
        Git git = new Git(repository);
        // Look in the 'refs/heads' namespace for a ref that points to the commit.
        // The returned map contains at most one entry where the key is the commit id and the value denotes the branch which points to it.
        Map<ObjectId, String> objectIdBranchNameMap = git.nameRev().addPrefix("refs/heads").add(objectId).call();
        if (!objectIdBranchNameMap.isEmpty()) {
            branch = objectIdBranchNameMap.get(objectId);
        }
        git.close();

        if (revCommit == null || branch == null) {
            throw new VersionControlException("Something went wrong retrieving the revCommit or the branch.");
        }

        var author = revCommit.getAuthorIdent();
        return new Commit(commitHash, author.getName(), revCommit.getFullMessage(), author.getEmailAddress(), branch);
    }

    private User getUserFromRequest(HttpServletRequest request) throws LocalVCAuthException {
        String authorizationHeader = request.getHeader(LocalVCServletService.AUTHORIZATION_HEADER);

        UsernameAndPassword usernameAndPassword = extractUsernameAndPassword(authorizationHeader);
        return userRepository.findOneByLogin(usernameAndPassword.username()).orElseThrow(LocalVCAuthException::new);
    }

    private ProgrammingExerciseParticipation getExerciseParticipationFromLocalVCRepositoryUri(LocalVCRepositoryUri localVCRepositoryUri) {
        String projectKey = localVCRepositoryUri.getProjectKey();
        String repositoryTypeOrUserName = localVCRepositoryUri.getRepositoryTypeOrUserName();
        ProgrammingExercise exercise = getProgrammingExerciseOrThrow(projectKey);

        return programmingExerciseParticipationService.getParticipationForRepository(exercise, repositoryTypeOrUserName, localVCRepositoryUri.isPracticeRepository(), false);
    }

    private ProgrammingExerciseParticipation getExerciseParticipationFromRequest(HttpServletRequest request) {
        LocalVCRepositoryUri localVCRepositoryUri = parseRepositoryUri(request);
        return getExerciseParticipationFromLocalVCRepositoryUri(localVCRepositoryUri);
    }

    /**
     * Determine the default branch of the given repository.
     *
     * @param repository the repository for which the default branch should be determined.
     * @return the name of the default branch.
     */
    public static String getDefaultBranchOfRepository(Repository repository) {
        Path repositoryFolderPath = repository.getDirectory().toPath();
        return LocalVCService.getDefaultBranchOfRepository(repositoryFolderPath.toString());
    }

    public void logFailedAttempt(HttpServletRequest servletRequest) {
        try {
            User user = getUserFromRequest(servletRequest);
            var participation = getExerciseParticipationFromRequest(servletRequest);
            vcsAccessLogService.ifPresent(service -> service.storeAccessLog(user, participation, RepositoryActionType.CLONE_FAIL, AuthenticationMechanism.PASSWORD, "", ""));
        }
        catch (LocalVCAuthException ignored) {
        }
    }

    public void addVCSAccessLogForCloneAndPull(HttpServletRequest request, int cntOffered) {
        try {
            String authorizationHeader = request.getHeader("Authorization");
            UsernameAndPassword usernameAndPassword = extractUsernameAndPassword(authorizationHeader);
            String userName = usernameAndPassword.username();
            if (userName.equals("buildjob_user")) {
                return;
            }
            RepositoryActionType repositoryActionType = getRepositoryActionReadType(cntOffered);
            var participation = getExerciseParticipationFromRequest(request);

            vcsAccessLogService.ifPresent(service -> service.updateRepositoryActionType(participation, repositoryActionType));

            log.info("username {} int {}", usernameAndPassword.username(), cntOffered);
        }
        catch (Exception ignored) {
        }
    }

    public void addVCSAccessLogForCloneAndPulloverSSH(ServerSession session, Path rootDir, int cntOffered) {
        try {
            if (session.getAttribute(SshConstants.USER_KEY).getName().equals("buildjob_user")) {
                return;
            }
            RepositoryActionType repositoryActionType = getRepositoryActionReadType(cntOffered);
            var parti = getExerciseParticipationFromLocalVCRepositoryUri(getLocalVCRepositoryUri(rootDir));
            vcsAccessLogService.ifPresent(service -> service.updateRepositoryActionType(parti, repositoryActionType));
        }
        catch (Exception ignored) {
        }
    }

    @Async
    public void addVCSAccessLogForPush(HttpServletRequest request) {
        if (!request.getMethod().equals("POST")) {
            return;
        }
        try {
            String authorizationHeader = request.getHeader("Authorization");
            UsernameAndPassword usernameAndPassword = extractUsernameAndPassword(authorizationHeader);
            String userName = usernameAndPassword.username();

            if (userName.equals("buildjob_user")) {
                return;
            }
            RepositoryActionType repositoryActionType = RepositoryActionType.PUSH;

            var participation = getExerciseParticipationFromRequest(request);

            vcsAccessLogService.ifPresent(service -> service.updateRepositoryActionType(participation, repositoryActionType));

        }
        catch (Exception ignored) {
        }
    }

    @Async
    public void addVcsAccessLogForSSH(User user, LocalVCRepositoryUri localVCRepositoryUri, Repository repo, RepositoryActionType repositoryAction, ServerSession session) {
        try {
            String commitHash = null;
            try {
                commitHash = getLatestCommitHash(repo);
            }
            catch (Exception e) {
                log.warn("failed attempt to add VCS access log for user " + user.getName() + ": " + e.getMessage());
            }
            var participation = getExerciseParticipationFromLocalVCRepositoryUri(localVCRepositoryUri);
            var ipAddress = ((InetSocketAddress) session.getRemoteAddress()).getAddress().getHostAddress();
            RepositoryActionType finalActionType = repositoryAction == RepositoryActionType.READ ? RepositoryActionType.PULL : RepositoryActionType.PUSH;
            String finalCommitHash = commitHash;
            vcsAccessLogService.ifPresent(service -> service.storeAccessLog(user, participation, finalActionType, AuthenticationMechanism.SSH, finalCommitHash, ipAddress));
        }
        catch (Exception ignored) {
        }
    }

    public RepositoryActionType getRepositoryActionReadType(int cntOffered) {
        if (cntOffered == 0) {
            return RepositoryActionType.CLONE;
        }
        else {
            return RepositoryActionType.PULL;
        }
    }

    record UsernameAndPassword(String username, String password) {
    }
}
