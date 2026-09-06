package de.tum.cit.aet.artemis.localvc.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALVC;
import static de.tum.cit.aet.artemis.core.util.HttpRequestUtils.getIpStringFromRequest;
import static de.tum.cit.aet.artemis.core.util.HttpRequestUtils.getPeerIpString;
import static de.tum.cit.aet.artemis.localvc.service.LocalVCPersonalAccessTokenManagementService.TOKEN_PREFIX;
import static de.tum.cit.aet.artemis.localvc.service.LocalVCPersonalAccessTokenManagementService.VCS_ACCESS_TOKEN_LENGTH;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import jakarta.servlet.http.HttpServletRequest;

import org.apache.sshd.server.session.ServerSession;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
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
import org.springframework.util.StringUtils;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.admin.service.RateLimitService;
import de.tum.cit.aet.artemis.buildagent.dto.BuildJobQueueItem;
import de.tum.cit.aet.artemis.core.config.BuildAgentNetworkPolicy;
import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.exception.RateLimitExceededException;
import de.tum.cit.aet.artemis.core.security.RateLimitType;
import de.tum.cit.aet.artemis.core.security.SecurityUtils;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.core.service.distributed.api.map.DistributedMap;
import de.tum.cit.aet.artemis.core.util.TimeLogUtil;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participation;
import de.tum.cit.aet.artemis.exercise.service.ExerciseVersionService;
import de.tum.cit.aet.artemis.localci.service.BuildAgentAddressRegistryService;
import de.tum.cit.aet.artemis.localci.service.BuildJobCloneTokenService;
import de.tum.cit.aet.artemis.localci.service.DistributedDataAccessService;
import de.tum.cit.aet.artemis.localci.service.ci.ContinuousIntegrationTriggerService;
import de.tum.cit.aet.artemis.localvc.exception.LocalVCAuthException;
import de.tum.cit.aet.artemis.localvc.exception.LocalVCForbiddenException;
import de.tum.cit.aet.artemis.localvc.exception.LocalVCInternalException;
import de.tum.cit.aet.artemis.localvc.service.ssh.SshConstants;
import de.tum.cit.aet.artemis.notification.dto.MailRecipientDTO;
import de.tum.cit.aet.artemis.notification.service.notifications.MailSendingService;
import de.tum.cit.aet.artemis.programming.domain.AuthenticationMechanism;
import de.tum.cit.aet.artemis.programming.domain.Commit;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.domain.SolutionProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.VcsAccessLog;
import de.tum.cit.aet.artemis.programming.exception.ContinuousIntegrationException;
import de.tum.cit.aet.artemis.programming.exception.VersionControlException;
import de.tum.cit.aet.artemis.programming.repository.ParticipationVCSAccessTokenRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.repository.RepositoryVCSAccessTokenRepository;
import de.tum.cit.aet.artemis.programming.service.AuxiliaryRepositoryService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseParticipationService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseTestCaseChangedService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingSubmissionMessagingService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingSubmissionService;
import de.tum.cit.aet.artemis.programming.service.RepositoryAccessService;
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

    private final RepositoryVCSAccessTokenRepository repositoryVCSAccessTokenRepository;

    private final AuthorizationCheckService authorizationCheckService;

    private final RateLimitService rateLimitService;

    private final ExerciseVersionService exerciseVersionService;

    private final UserVcsAccessTokenService userVcsAccessTokenService;

    private final MailSendingService mailSendingService;

    private final DistributedDataProvider distributedDataProvider;

    // Optional: a node running LocalVC with Jenkins has no local CI, so it has neither build jobs nor a registry
    private final Optional<DistributedDataAccessService> distributedDataAccessService;

    private final Optional<BuildAgentAddressRegistryService> buildAgentAddressRegistryService;

    private final Optional<BuildJobCloneTokenService> buildJobCloneTokenService;

    private final BuildAgentNetworkPolicy buildAgentNetworkPolicy;

    @Value("${artemis.version-control.url}")
    private URI localVCBaseUri;

    @Value("${artemis.version-control.local-vcs-repo-path}")
    private Path localVCBasePath;

    // Optional on purpose: an installation whose build agents authenticate with an ssh key never uses this credential
    // pair, and then must not have to configure one. Every read of these fields is guarded by StringUtils.hasText, and
    // LocalVCBuildAgentCredentialsValidator fails startup when the https case leaves them unset.
    @Value("${artemis.version-control.build-agent-git-username:}")
    private String buildAgentGitUsername;

    @Value("${artemis.version-control.build-agent-git-password:}")
    private String buildAgentGitPassword;

    /**
     * Whether the build agents of this installation clone over ssh, using the key pair they generate at startup and
     * publish to the core nodes, rather than over https with {@code build-agent-git-username} and
     * {@code build-agent-git-password}. The two mechanisms are alternatives, not a fallback chain: a build agent picks
     * exactly one in {@code BuildJobGitService.authenticate}, so when ssh is configured, this node stops honouring the
     * shortcut below rather than leaving a second repository-wide read path open that nothing uses. The credentials are
     * still processed as ordinary Basic credentials afterwards, which grants only whatever the named account may access.
     * <p>
     * This closes the https door only. {@code GitPublickeyAuthenticatorService} keeps authenticating a registered build
     * agent by its public key whatever this property says, deliberately: a key is per-agent and reaches this node only
     * through an agent that has joined the cluster, so there is no shared secret to withdraw, and agents can be moved to
     * ssh one at a time before the core nodes follow.
     */
    @Value("${artemis.version-control.build-agent-use-ssh:false}")
    private boolean useSshForBuildAgent;

    public static final String BUILD_USER_NAME = "buildjob_user";

    public static final String HTTPS_CLONE_EMAIL_CACHE = "httpsCloneWarningEmailCache";

    /**
     * Marks a request that was authorized as a build agent cloning for one of its build jobs. Set once the credential
     * has been accepted, so later stages can recognise build agent traffic without guessing from the username.
     */
    private static final String BUILD_AGENT_CLONE_REQUEST_ATTRIBUTE = "artemis.buildAgentClone";

    private static final String AUTHENTICATED_USER_REQUEST_ATTRIBUTE = "artemis.authenticatedUser";

    private static final String AUTHENTICATION_MECHANISM_REQUEST_ATTRIBUTE = "artemis.authenticationMechanism";

    public LocalVCServletService(AuthenticationManager authenticationManager, UserRepository userRepository, ProgrammingExerciseRepository programmingExerciseRepository,
            RepositoryAccessService repositoryAccessService, ProgrammingExerciseParticipationService programmingExerciseParticipationService,
            AuxiliaryRepositoryService auxiliaryRepositoryService, ContinuousIntegrationTriggerService ciTriggerService, ProgrammingSubmissionService programmingSubmissionService,
            ProgrammingSubmissionMessagingService programmingSubmissionMessagingService, ProgrammingExerciseTestCaseChangedService programmingExerciseTestCaseChangedService,
            ParticipationVCSAccessTokenRepository participationVCSAccessTokenRepository, RepositoryVCSAccessTokenRepository repositoryVCSAccessTokenRepository,
            Optional<VcsAccessLogService> vcsAccessLogService, AuthorizationCheckService authorizationCheckService, RateLimitService rateLimitService,
            ExerciseVersionService exerciseVersionService, UserVcsAccessTokenService userVcsAccessTokenService, Optional<DistributedDataAccessService> distributedDataAccessService,
            Optional<BuildAgentAddressRegistryService> buildAgentAddressRegistryService, Optional<BuildJobCloneTokenService> buildJobCloneTokenService,
            BuildAgentNetworkPolicy buildAgentNetworkPolicy, MailSendingService mailSendingService, DistributedDataProvider distributedDataProvider) {
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
        this.repositoryVCSAccessTokenRepository = repositoryVCSAccessTokenRepository;
        this.vcsAccessLogService = vcsAccessLogService;
        this.authorizationCheckService = authorizationCheckService;
        this.rateLimitService = rateLimitService;
        this.exerciseVersionService = exerciseVersionService;
        this.userVcsAccessTokenService = userVcsAccessTokenService;
        this.distributedDataAccessService = distributedDataAccessService;
        this.buildAgentAddressRegistryService = buildAgentAddressRegistryService;
        this.buildJobCloneTokenService = buildJobCloneTokenService;
        this.buildAgentNetworkPolicy = buildAgentNetworkPolicy;
        this.mailSendingService = mailSendingService;
        this.distributedDataProvider = distributedDataProvider;
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
        // Sanitize once for all log statements to prevent CRLF injection
        String sanitizedPath = repositoryPath.replaceAll("[\\r\\n]", "_");

        // Find the local repository depending on the name.
        Path normalizedBasePath = localVCBasePath.normalize();
        Path repositoryDir = normalizedBasePath.resolve(repositoryPath).normalize();

        // Prevent path traversal attacks by ensuring the resolved path stays within the base path
        if (!repositoryDir.startsWith(normalizedBasePath)) {
            log.error("Blocked path traversal attempt for repository path: {}", sanitizedPath);
            throw new RepositoryNotFoundException(repositoryPath);
        }

        log.debug("Path to resolve repository from: {}", sanitizedPath);
        if (!Files.exists(repositoryDir)) {
            log.error("Could not find local repository with name {}", sanitizedPath);
            throw new RepositoryNotFoundException(repositoryPath);
        }

        // After confirming the path exists, resolve symlinks and verify the real path is still within the base
        try {
            Path realBasePath = localVCBasePath.toRealPath();
            Path realRepositoryDir = repositoryDir.toRealPath();
            if (!realRepositoryDir.startsWith(realBasePath)) {
                log.error("Blocked symlink-based path traversal for repository path: {}", sanitizedPath);
                throw new RepositoryNotFoundException(repositoryPath);
            }
        }
        catch (IOException e) {
            throw new RepositoryNotFoundException(repositoryPath, e);
        }

        log.debug("Opening local repository {}", sanitizedPath);
        try {
            Repository repository = FileRepositoryBuilder.create(repositoryDir.toFile());
            // Enable pushing without credentials, authentication is handled by the LocalVCPushFilter.
            repository.getConfig().setBoolean("http", null, "receivepack", true);

            log.debug("Resolving repository for repository {} took {}", sanitizedPath, TimeLogUtil.formatDurationFrom(timeNanoStart));
            return repository;
        }
        catch (IOException e) {
            log.error("Unable to open local repository {}", sanitizedPath);
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
            throw new LocalVCAuthException("No authorization header provided", true);
        }

        // A build agent cloning for a build job it is currently running. Ahead of the rate limiter on purpose: agents
        // are exempt from it today only because the shortcut below returns early, and throttling them would stall
        // every build during an exam peak. Unlike that shortcut this grants nothing installation-wide - it opens the
        // repositories of one running job, to the agent that holds it, from the address that agent is connected from.
        if (repositoryAction == RepositoryActionType.READ && authenticateBuildJobCloneToken(request, authorizationHeader)) {
            return;
        }

        // If it is a fetch request, we check if it is the build agent that is fetching the repository. Two conditions
        // close this shortcut entirely rather than narrowing it, because what it grants - repository-wide read, ahead
        // of the rate limit, the authorization checks and the access log - is worth strictly less than the attack
        // surface it carries wherever something else can do the job.
        //
        // Local CI is one of them: every build job there carries a token scoped to its own repositories, so no Artemis
        // build agent has any use for a shared credential. LocalVCBuildAgentCredentialsValidator already refuses to
        // start such a node with one configured; this makes the shortcut unreachable rather than merely unconfigured,
        // so a credential that arrives by some other route still opens nothing. What remains is a local VC node
        // without local CI, whose client is Jenkins - not an Artemis build agent, and with neither key nor build job.
        //
        // The other is ssh: build agents that authenticate with a key never present this pair.
        if (repositoryAction == RepositoryActionType.READ && !useSshForBuildAgent && distributedDataAccessService.isEmpty()) {
            UsernameAndPassword usernameAndPassword = extractUsernameAndPassword(authorizationHeader);
            // A blank configured credential must never match: this shortcut returns ahead of the rate limit, the
            // repository authorization checks and the access log, so an empty configured password would hand
            // repository-wide read access to anyone presenting the build-agent username. ConfigurationValidator
            // rejects that configuration under prod, but this path also runs where that validation does not.
            // The hasText guard is not made redundant by the constant-time comparison below: a blank configured
            // password would still match an equally blank provided one.
            if (StringUtils.hasText(buildAgentGitUsername) && StringUtils.hasText(buildAgentGitPassword) && Objects.equals(usernameAndPassword.username(), buildAgentGitUsername)
                    && secretMatches(buildAgentGitPassword, usernameAndPassword.password())) {
                // Authentication successful
                return;
            }
        }

        // Only count rate limit on /info/refs (the initial handshake request per git operation).
        // The data transfer requests (git-upload-pack, git-receive-pack) reuse the same credentials
        // and should not consume additional rate limit budget.
        if (request.getRequestURI().endsWith("/info/refs")) {
            String ipString = getIpStringFromRequest(request);
            final IPAddress ipAddress = new IPAddressString(ipString).getAddress();
            rateLimitService.enforcePerMinute(ipAddress, RateLimitType.AUTHENTICATION);
        }

        LocalVCRepositoryUri localVCRepositoryUri = parseRepositoryUri(request);
        log.debug("Parsed repository URI from request: {}", localVCRepositoryUri);
        String projectKey = localVCRepositoryUri.getProjectKey();
        String repositoryTypeOrUserName = localVCRepositoryUri.getRepositoryTypeOrUserName();

        ProgrammingExercise exercise = getProgrammingExerciseOrThrow(projectKey);

        // The participation behind this repository is needed twice: once to find the participation-scoped token during
        // authentication, and once to authorize the repository access. Resolving it once, lazily, means the common case
        // (a student pushing or fetching with their participation token) loads it a single time instead of twice, and
        // the requests that never need it (a staff token, a failed credential) still do not pay for it.
        Supplier<ProgrammingExerciseParticipation> participationForRepository = participationResolver(repositoryTypeOrUserName, localVCRepositoryUri, exercise);

        var authenticated = authenticateUser(authorizationHeader, exercise, localVCRepositoryUri, participationForRepository);
        User user = authenticated.user();
        request.setAttribute(AUTHENTICATED_USER_REQUEST_ATTRIBUTE, user);
        request.setAttribute(AUTHENTICATION_MECHANISM_REQUEST_ATTRIBUTE, authenticated.mechanism());

        // Check that offline IDE usage is allowed.
        try {
            repositoryAccessService.checkHasAccessToOfflineIDEElseThrow(exercise, user);
        }
        catch (AccessForbiddenException e) {
            throw new LocalVCForbiddenException(e);
        }

        try {
            var optionalParticipation = authorizeUser(repositoryTypeOrUserName, user, exercise, repositoryAction, localVCRepositoryUri, false, participationForRepository);
            // Only create the preliminary access log on /info/refs requests.
            // The data transfer requests (git-upload-pack, git-receive-pack) will update this log entry
            // via PreUploadHook / processNewPush rather than creating a duplicate.
            if (request.getRequestURI().endsWith("/info/refs")) {
                savePreliminaryVcsAccessLogForHTTPs(request, localVCRepositoryUri, user, repositoryAction, optionalParticipation, authenticated.mechanism());
            }
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
     * @param request                 The object containing all information about the incoming request.
     * @param localVCRepositoryUri    The uri of the requested repository
     * @param user                    The user
     * @param repositoryAction        Indicates whether the method should authenticate a fetch or a push request. For a push request, additional checks are conducted.
     * @param optionalParticipation   The participation for which the access log should be stored. If an empty Optional is provided, the method does nothing
     * @param authenticationMechanism The credential the request authenticated with, as reported by the authentication itself
     * @throws LocalVCAuthException If the user authentication fails or the user is not authorized to access a certain repository.
     */
    private void savePreliminaryVcsAccessLogForHTTPs(HttpServletRequest request, LocalVCRepositoryUri localVCRepositoryUri, User user, RepositoryActionType repositoryAction,
            Optional<ProgrammingExerciseParticipation> optionalParticipation, AuthenticationMechanism authenticationMechanism) throws LocalVCAuthException {
        if (optionalParticipation.isPresent()) {
            ProgrammingExerciseParticipation participation = optionalParticipation.get();
            var ipAddress = request.getRemoteAddr();

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
        try {
            var participation = tryToLoadParticipation(false, repositoryTypeOrUserName, localVCRepositoryUri, (ProgrammingExercise) exercise,
                    participationResolver(repositoryTypeOrUserName, localVCRepositoryUri, (ProgrammingExercise) exercise));
            var commitHash = getCommitHash(localVCRepositoryUri);
            var authenticationMechanism = resolveAuthenticationMechanismFromSessionOrRequest(context, user, localVCRepositoryUri);
            var action = repositoryAction == RepositoryActionType.WRITE ? RepositoryActionType.PUSH_FAIL : RepositoryActionType.CLONE_FAIL;
            var ipAddress = context.getIpAddress();
            vcsAccessLogService.ifPresent(service -> service.saveAccessLog(user, participation, action, authenticationMechanism, commitHash, ipAddress));
        }
        catch (Exception e) {
            log.warn("Failed to save VCS access log for failed access attempt by user {} to repository {}: {}", user.getLogin(), localVCRepositoryUri, e.getMessage());
        }
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
     * @param context              the Authentication context
     * @param user                 the user for whom authentication is being determined
     * @param localVCRepositoryUri the URI of the repository the user tried to access (used to recognize repository-scoped tokens)
     * @return the resolved {@link AuthenticationMechanism}
     */
    private AuthenticationMechanism resolveAuthenticationMechanismFromSessionOrRequest(AuthenticationContext context, User user, LocalVCRepositoryUri localVCRepositoryUri) {
        switch (context) {
            case AuthenticationContext.Session ignored -> {
                return AuthenticationMechanism.SSH;
            }
            case AuthenticationContext.Request request -> {
                try {
                    return resolveHTTPSAuthenticationMechanism(request.request().getHeader(HttpHeaders.AUTHORIZATION), user, localVCRepositoryUri);
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
    private AuthenticationMechanism resolveHTTPSAuthenticationMechanism(String authorizationHeader, User user, LocalVCRepositoryUri localVCRepositoryUri)
            throws LocalVCAuthException {
        UsernameAndPassword usernameAndPassword = extractUsernameAndPassword(authorizationHeader);

        String password = usernameAndPassword.password();
        if (!password.startsWith(TOKEN_PREFIX)) {
            return AuthenticationMechanism.PASSWORD;
        }
        if (secretMatches(userVcsAccessTokenService.findToken(user.getId()), password)) {
            return AuthenticationMechanism.USER_VCS_ACCESS_TOKEN;
        }
        if (localVCRepositoryUri != null) {
            var repositoryToken = repositoryVCSAccessTokenRepository.findByUserIdAndRepositoryUri(user.getId(), localVCRepositoryUri.toString());
            if (repositoryToken.isPresent() && secretMatches(repositoryToken.get().getVcsAccessToken(), password)) {
                return AuthenticationMechanism.REPOSITORY_VCS_ACCESS_TOKEN;
            }
        }
        return AuthenticationMechanism.PARTICIPATION_VCS_ACCESS_TOKEN;
    }

    /**
     * Decides whether a fetch is a build agent cloning a repository of a build job it is currently running.
     * <p>
     * Replaces the installation-wide build agent credential on the https path with something bounded on three axes:
     * the caller must be a registered agent connected from the address it is calling from, must present the token of a
     * job that agent currently holds, and may only read the repositories that job declares. Nothing here is time
     * based - a job leaves the processing list when it finishes, is cancelled or hits the build timeout, and the token
     * stops working at that moment.
     * <p>
     * The username is the agent's short name, which is an identifier and not a credential: it is the Hazelcast client
     * name, the key of the build agent information map, and is shown in the admin UI. It selects whose jobs and whose
     * addresses to check; the token is what authenticates.
     * <p>
     * Every failure falls through to normal user authentication rather than rejecting, because a short name could
     * collide with a real login and that person must still be able to use their own credentials.
     *
     * @param request             the incoming git request
     * @param authorizationHeader the Basic authorization header of that request
     * @return whether the request is an authorized build agent clone
     */
    private boolean authenticateBuildJobCloneToken(HttpServletRequest request, String authorizationHeader) {
        if (distributedDataAccessService.isEmpty() || buildAgentAddressRegistryService.isEmpty() || buildJobCloneTokenService.isEmpty()) {
            // No local CI on this node, so there are no build jobs and nothing can present a valid token
            return false;
        }

        try {
            UsernameAndPassword usernameAndPassword = extractUsernameAndPassword(authorizationHeader);
            String agentName = usernameAndPassword.username();
            String presentedToken = usernameAndPassword.password();
            if (!StringUtils.hasText(agentName) || !StringUtils.hasText(presentedToken)) {
                return false;
            }

            // Cheapest possible gates first, and deliberately so. This method runs for every read request that carries
            // any Basic header, ahead of the rate limiter, so anything expensive here is reachable by an unauthenticated
            // caller in a loop - and on the ordinary student clone it is pure overhead that must stay off the hot path.
            //
            // The prefix is a purely local check and rejects every credential that is not a clone token at all: a
            // password, a vcpat- access token, an ssh key request. Every token this installation mints carries it, so
            // nothing that could match is turned away, and the prefix is not a secret - it exists to make a credential
            // recognisable in a log. Only past it is a single-key lookup performed to reject a username that is not a
            // build agent, and only past that may the whole-map reads below run.
            if (!presentedToken.startsWith(BuildJobCloneTokenService.CLONE_TOKEN_PREFIX)) {
                return false;
            }
            if (distributedDataAccessService.get().getDistributedBuildAgentInformation().get(agentName) == null) {
                return false;
            }

            // The allowlist is pure local state and costs nothing, so it stays here: a caller outside the configured
            // build agent networks is rejected before any of the work below.
            String peerIpAddress = getPeerIpString(request, buildAgentNetworkPolicy::isTrustedProxy);
            if (!buildAgentNetworkPolicy.isWithinAllowedRanges(peerIpAddress)) {
                log.warn("Rejecting a build agent clone for agent {} from {}, which is outside the configured build agent networks", agentName, peerIpAddress);
                return false;
            }

            // Last gate before the first expensive read, and the only one that bounds repetition. Everything above is
            // O(1) local or single-key work; getProcessingJobsForAgentByName below reads the whole distributed
            // processing job map and deserializes every entry to filter it. A caller inside the build agent networks
            // who knows a registered agent name passes both cheap gates with any password at all, so without this the
            // scan is reachable in a loop by a caller that ordinary authentication would already be throttling.
            //
            // Two things keep the limit off legitimate agents, so it can be sized for guessing rather than for build
            // throughput. An address some agent is registered at skips it entirely, which follows the agents around
            // without an operator maintaining a list. And a check that succeeds spends nothing: only a decline does,
            // below. That covers the agents with no registration to go by - one sharing a JVM with a core node has no
            // observable connection - whose clone rate is otherwise the highest of all.
            //
            // Over the limit falls through rather than rejecting, matching the contract documented above: this method
            // never rejects a request, it only declines to treat it as a build agent clone. The request then meets the
            // ordinary authentication rate limiter and user authentication, which is what should be answering a caller
            // behaving like this anyway.
            IPAddress peerAddress = new IPAddressString(peerIpAddress).getAddress();
            boolean registeredAgentAddress = buildAgentAddressRegistryService.get().isRegisteredBuildAgentAddress(peerIpAddress);
            if (!registeredAgentAddress && !rateLimitService.hasRemainingBudget(peerAddress, RateLimitType.BUILD_AGENT_CLONE_TOKEN)) {
                log.warn("Rate limiting the build agent clone token check for agent {} from {}; falling through to user authentication", agentName, peerIpAddress);
                return false;
            }

            // Parsed before the scan although it is only needed after it. This is local string work that can throw, and
            // the catch at the end of this method returns without spending budget - deliberately, since a malformed
            // request is not a guess at a credential. Doing it after the scan would make an unparsable path a way to
            // run the scan for free, repeatedly.
            LocalVCRepositoryUri localVCRepositoryUri = parseRepositoryUri(request);

            var processingJobs = distributedDataAccessService.get().getProcessingJobsForAgentByName(agentName);
            if (processingJobs.isEmpty()) {
                // A registered agent running nothing, so no token can match
                spendCloneTokenBudget(registeredAgentAddress, peerAddress);
                return false;
            }

            var tokenService = buildJobCloneTokenService.get();
            BuildJobQueueItem matchingBuildJob = null;
            for (BuildJobQueueItem buildJob : processingJobs) {
                if (tokenService.tokenMatches(buildJob, presentedToken)) {
                    matchingBuildJob = buildJob;
                    break;
                }
            }
            if (matchingBuildJob == null) {
                // The one case that looks like credential guessing against a live agent, so it must not be the one case
                // that leaves no trace.
                log.warn("Build agent {} from {} presented a credential matching none of its {} running build jobs", agentName, peerIpAddress, processingJobs.size());
                spendCloneTokenBudget(registeredAgentAddress, peerAddress);
                return false;
            }

            // Origin after the token, which is the opposite of what this used to do. The origin check is no longer
            // answerable from local state alone: on a miss it reconciles against the middleware, which queries the
            // connected clients and takes a lock other requests wait on. The name that reaches this far is only an
            // identifier - it is the middleware's client name, rendered in the admin UI and guessable - so with the
            // origin check first, a caller presenting any password at all could force that work in a loop, ahead of the
            // rate limiter this path deliberately sits in front of. Requiring the token first means only a caller who
            // already holds a live job's secret can cause it. Both conditions still have to pass, so nothing is
            // weakened: a token read out of the queue by another party remains useless away from the agent's address.
            if (!buildAgentAddressRegistryService.get().isRegisteredAddressOfAgent(agentName, peerIpAddress)) {
                // Also the signal for a misconfigured proxy, where the token is right but the address the request
                // appears to come from is not the agent's.
                log.warn("Rejecting a build agent clone claiming to be agent {} from {}, which is not an address that agent is connected from", agentName, peerIpAddress);
                spendCloneTokenBudget(registeredAgentAddress, peerAddress);
                return false;
            }

            if (!tokenService.coversRepository(matchingBuildJob, localVCRepositoryUri)) {
                log.warn("Build agent {} presented the token of build job {} for repository {}, which is not one of that job's repositories {}", agentName, matchingBuildJob.id(),
                        localVCRepositoryUri, tokenService.getRepositoryIdentities(matchingBuildJob));
                spendCloneTokenBudget(registeredAgentAddress, peerAddress);
                return false;
            }
            // Tells the pre-upload hook that this request is a build agent clone, so that it does not relabel
            // whichever access log entry happens to be newest for this repository. It used to recognise a build
            // agent by the literal buildjob_user, which an agent presenting its own short name never matches.
            request.setAttribute(BUILD_AGENT_CLONE_REQUEST_ATTRIBUTE, agentName);
            // Only on the handshake, like the rate limiter above: git follows /info/refs with a git-upload-pack
            // using the same credentials, and one clone should leave one audit entry rather than two.
            if (request.getRequestURI().endsWith("/info/refs")) {
                saveBuildAgentVcsAccessLog(localVCRepositoryUri, agentName, matchingBuildJob.id(), peerIpAddress, AuthenticationMechanism.BUILD_JOB_TOKEN);
            }
            return true;
        }
        catch (Exception e) {
            // Anything unexpected here means this is not a valid build agent clone. Fall through rather than reject,
            // so a malformed header or an unparsable repository path is still handled by the normal path below.
            log.debug("Could not authenticate the request as a build agent clone", e);
            return false;
        }
    }

    /**
     * Charges one attempt against a caller's clone-token budget, for a check that reached the distributed scan and then
     * declined.
     * <p>
     * Only declines are charged. A build agent whose checks succeed therefore never approaches the limit however many
     * repositories it clones, which is what lets the limit be sized like any other guessing bound instead of having to
     * clear the busiest plausible agent - the sizing that made an earlier default an order of magnitude too permissive.
     *
     * @param registeredAgentAddress whether this address is already exempt because some agent is registered at it
     * @param peerAddress            the resolved client address, may be null if it could not be parsed
     */
    private void spendCloneTokenBudget(boolean registeredAgentAddress, @Nullable IPAddress peerAddress) {
        if (!registeredAgentAddress) {
            rateLimitService.consumePerMinute(peerAddress, RateLimitType.BUILD_AGENT_CLONE_TOKEN);
        }
    }

    /**
     * Records a build agent clone in the VCS access log, which the old shared-credential shortcut never did.
     * <p>
     * Shared by both mechanisms rather than reimplemented per transport: the ssh path resolves the same participation
     * from the same repository uri, and having one implementation is what stops the two from drifting into logging
     * different things - or, as ssh originally did, nothing at all.
     * <p>
     * Best effort: an audit entry that cannot be written must not fail the build.
     *
     * @param localVCRepositoryUri the repository being read
     * @param agentName            the short name of the build agent
     * @param buildJobId           the id of the build job the read belongs to
     * @param ipAddress            the address the agent connected from
     * @param mechanism            how the agent authenticated
     */
    public void saveBuildAgentVcsAccessLog(LocalVCRepositoryUri localVCRepositoryUri, String agentName, String buildJobId, String ipAddress, AuthenticationMechanism mechanism) {
        try {
            ProgrammingExercise exercise = getProgrammingExerciseOrThrow(localVCRepositoryUri.getProjectKey());
            var participation = programmingExerciseParticipationService.fetchParticipationWithSubmissionsByRepository(localVCRepositoryUri.getRepositoryTypeOrUserName(),
                    localVCRepositoryUri.toString(), exercise);
            String commitHash = getCommitHash(localVCRepositoryUri);
            vcsAccessLogService.ifPresent(service -> service.saveBuildAgentAccessLog(participation, agentName, buildJobId, commitHash, ipAddress, mechanism));
        }
        catch (EntityNotFoundException e) {
            // An auxiliary repository has no participation of its own, so there is nothing to attribute the access to.
            // Expected for those, and it happens on every build, so it must not be a warning.
            log.debug("No participation to record a build agent access against for {}", localVCRepositoryUri);
        }
        catch (Exception e) {
            log.warn("Could not write a VCS access log entry for build agent {} cloning {}: {}", agentName, localVCRepositoryUri, e.getMessage());
        }
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
    private AuthenticatedUser authenticateUser(String authorizationHeader, ProgrammingExercise exercise, LocalVCRepositoryUri localVCRepositoryUri,
            Supplier<ProgrammingExerciseParticipation> participationForRepository) throws LocalVCAuthException, AuthenticationException {

        UsernameAndPassword usernameAndPassword = extractUsernameAndPassword(authorizationHeader);
        String username = usernameAndPassword.username();
        String passwordOrToken = usernameAndPassword.password();

        // Load the course roles and authorities together with the user. Authorization below runs four course-role checks
        // for this user and this course (one in checkAccessToStaffRepository, three in checkAccessRepositoryElseThrow).
        // Without the roles, each of them falls back to its own EXISTS query; without the authorities, each of them also
        // re-reads the whole user row through AuthorizationCheckService#loadUserIfNeeded, because User#authorities is
        // lazy. That is eight extra queries on the single hottest path of an exam, all answerable from this one load.
        User user = userRepository.findOneWithCourseRolesAndAuthoritiesByLogin(username).orElseThrow(LocalVCAuthException::new);

        try {
            SecurityUtils.checkUsernameAndPasswordValidity(username, passwordOrToken);
        }
        catch (AccessForbiddenException | AuthenticationException e) {
            // Git clients routinely send a request with an empty password (e.g. before a credential helper supplies one or when only the username is baked into the remote URL).
            // That is expected probing noise rather than a genuine failed login attempt, so log it at debug to keep the production logs focused on real credential issues.
            boolean missingPassword = passwordOrToken.isEmpty();
            if (missingPassword) {
                log.debug("Login attempt for user {} without a password; no credentials provided", username);
            }
            else {
                log.warn("Failed login attempt for user {} due to issue: {}", username, e.getMessage());
            }
            throw new LocalVCAuthException(e.getMessage(), missingPassword);
        }

        // Account state is checked here, before any credential is compared, because it has to hold for every credential
        // type. Only the password fall-through below goes through the authenticationManager, which checks `activated`
        // itself; the three token branches return the user directly, so without this a deactivated or soft-deleted user
        // kept full repository access through any token they had been issued earlier.
        if (!user.getActivated() || user.isDeleted()) {
            log.warn("Git authentication attempt for user {} whose account is deactivated or deleted", username);
            throw new LocalVCAuthException("Account is not active");
        }

        // check user VCS access token. findUsableToken already excludes an expired one, so the expiry is not compared here.
        var personalToken = userVcsAccessTokenService.findUsableToken(user.getId());
        if (personalToken.isPresent() && secretMatches(personalToken.get().getToken(), passwordOrToken)) {
            return new AuthenticatedUser(user, AuthenticationMechanism.USER_VCS_ACCESS_TOKEN);
        }

        // check user participation VCS access token
        if (tryAuthenticationWithParticipationVCSAccessToken(user, passwordOrToken, exercise, participationForRepository)) {
            return new AuthenticatedUser(user, AuthenticationMechanism.PARTICIPATION_VCS_ACCESS_TOKEN);
        }

        // check repository-scoped VCS access token (course staff token bound to a single base repository)
        if (tryAuthenticationWithRepositoryVcsAccessToken(user, passwordOrToken, localVCRepositoryUri)) {
            return new AuthenticatedUser(user, AuthenticationMechanism.REPOSITORY_VCS_ACCESS_TOKEN);
        }

        // if the user does not have an access token or used a password, we try to authenticate the user with it
        // Try to authenticate the user based on the configured options, this can include sending the data to an external system (e.g. LDAP) or using internal authentication.
        UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(username, passwordOrToken);
        authenticationManager.authenticate(authenticationToken);

        return new AuthenticatedUser(user, AuthenticationMechanism.PASSWORD);
    }

    /**
     * The account a git request authenticated as, together with the credential it used.
     * <p>
     * The mechanism is reported here rather than derived again for the access log. Deriving it again means repeating the
     * lookups the authentication just did - and getting it right at all depended on the matched participation token being
     * copied onto the in-memory user, which made a participation token show up in the log as the user's own.
     *
     * @param user      the authenticated account
     * @param mechanism the credential it authenticated with
     */
    private record AuthenticatedUser(User user, AuthenticationMechanism mechanism) {
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
    private boolean tryAuthenticationWithParticipationVCSAccessToken(User user, String providedToken, ProgrammingExercise exercise,
            Supplier<ProgrammingExerciseParticipation> participationForRepository) throws LocalVCAuthException {

        // Note: we first check if the user has used a vcs access token instead of a password
        if (providedToken.startsWith(TOKEN_PREFIX) && providedToken.length() == VCS_ACCESS_TOKEN_LENGTH) {
            try {
                // check participation vcs access token. For an individual exercise this is the participation behind the
                // requested repository, which authorization resolves anyway, so it is shared rather than looked up again.
                Optional<Long> participationId;
                if (exercise.isTeamMode()) {
                    participationId = programmingExerciseParticipationService.findTeamParticipationByExerciseAndUser(exercise, user).map(DomainObject::getId);
                }
                else {
                    participationId = resolveQuietly(participationForRepository).map(ProgrammingExerciseParticipation::getId);
                }
                if (participationId.isPresent()) {
                    // Only the token itself is compared, so only the token is read.
                    var storedToken = participationVCSAccessTokenRepository.findTokenByUserIdAndParticipationId(user.getId(), participationId.get());
                    if (storedToken.isPresent() && secretMatches(storedToken.get(), providedToken)) {
                        // The matched token is deliberately not copied onto the user. Copying it would make this participation
                        // token indistinguishable from the user's own one, and the access log would record the wrong mechanism.
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
     * Returns whether the provided secret matches the expected one. This method is in comparison to
     * {@link Objects#equals(Object, Object)} is based on {@link MessageDigest#isEqual(byte[], byte[])} to
     * guarantee nearly time-constant comparison.
     *
     * @param expectedSecret expected secret. May be null to allow for nullable types to be used with this.
     *                           Since {@code providedSecret} is never {@code null}, this will result in {@code false.}
     * @param providedSecret the value that was provided for the secret.
     * @return the result of {@code Objects.equals(expectedSecret, providedSecret)} but with a time-constant comparison.
     * @implNote The expected secret is allowed to be null to be compatible with {@link Objects#equals(Object, Object)}.
     *           Normally, a missing secret should raise some warning. However, the current usage of this method never passes
     *           {@code null} for {@code providedSecret}. Therefore, the result for such a case is always {@code false}.
     *           To reaffirm this, the {@code providedSecret} is expected to be non-null, making it obvious,
     *           that a {@code expectedSecret == null} will always result in {@code false}.
     */
    private boolean secretMatches(@Nullable String expectedSecret, @NonNull String providedSecret) {
        if (expectedSecret == null) {
            return false;
        }
        final var expectedBytes = expectedSecret.getBytes(StandardCharsets.UTF_8);
        final var actualBytes = providedSecret.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expectedBytes, actualBytes);
    }

    /**
     * Attempts to authenticate a user with a repository-scoped VCS access token (course staff token bound to a single base repository).
     * <p>
     * The token is looked up by the exact repository URI, which enforces that a token is only valid for the one repository it was issued for. This method only authenticates the
     * user; the authorization (at least tutor to read, at least editor to write) is still enforced afterwards in {@link #authorizeUser}.
     *
     * @param user                 the user attempting authentication
     * @param providedToken        the token provided by the user
     * @param localVCRepositoryUri the URI of the repository the user tries to access
     * @return {@code true} if the token matches a repository token the user owns for this repository, {@code false} otherwise
     */
    private boolean tryAuthenticationWithRepositoryVcsAccessToken(User user, String providedToken, LocalVCRepositoryUri localVCRepositoryUri) {
        if (providedToken.startsWith(TOKEN_PREFIX) && providedToken.length() == VCS_ACCESS_TOKEN_LENGTH) {
            var storedToken = repositoryVCSAccessTokenRepository.findByUserIdAndRepositoryUri(user.getId(), localVCRepositoryUri.toString());
            if (storedToken.isPresent() && secretMatches(storedToken.get().getVcsAccessToken(), providedToken)) {
                return true;
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
            throw new LocalVCAuthException("No authorization header provided", true);
        }
        String[] basicAuthCredentialsEncoded = authorizationHeader.split(" ");

        if (basicAuthCredentialsEncoded.length < 2 || !("Basic".equals(basicAuthCredentialsEncoded[0]))) {
            throw new LocalVCAuthException("Invalid authorization header format");
        }

        // Decode the Base64-encoded credentials (username:password).
        String basicAuthCredentials;
        try {
            basicAuthCredentials = new String(Base64.getDecoder().decode(basicAuthCredentialsEncoded[1]));
        }
        catch (IllegalArgumentException e) {
            throw new LocalVCAuthException("Invalid Base64 encoding in authorization header");
        }

        int separatorIndex = basicAuthCredentials.indexOf(":");

        if (separatorIndex == -1) {
            throw new LocalVCAuthException("Missing colon separator in Basic auth credentials");
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
        return authorizeUser(repositoryTypeOrUserName, user, exercise, repositoryActionType, localVCRepositoryUri, usingSSH,
                participationResolver(repositoryTypeOrUserName, localVCRepositoryUri, exercise));
    }

    /**
     * Authorizes a user for a repository, reusing a participation the caller has already resolved.
     *
     * @param repositoryTypeOrUserName   the repository type or the user name taken from the repository URI
     * @param user                       the user requesting access
     * @param exercise                   the programming exercise the repository belongs to
     * @param repositoryActionType       whether the request reads or writes
     * @param localVCRepositoryUri       the URI of the requested repository
     * @param usingSSH                   whether the request arrived over SSH
     * @param participationForRepository the participation behind the repository, if the caller already resolved it
     * @return the participation the access was authorized against, empty for repositories that have none
     * @throws LocalVCForbiddenException if the user is not allowed to access the repository
     */
    public Optional<ProgrammingExerciseParticipation> authorizeUser(String repositoryTypeOrUserName, User user, ProgrammingExercise exercise,
            RepositoryActionType repositoryActionType, LocalVCRepositoryUri localVCRepositoryUri, boolean usingSSH,
            Supplier<ProgrammingExerciseParticipation> participationForRepository) throws LocalVCForbiddenException {

        if (checkAccessToStaffRepository(exercise, repositoryTypeOrUserName, repositoryActionType, user)) {
            // For tests and auxiliary repos, no participation is needed (they don't have dedicated participations).
            // For template and solution repos, load the participation so callers can use it for access logging.
            if (repositoryTypeOrUserName.equals(RepositoryType.TEMPLATE.toString()) || repositoryTypeOrUserName.equals(RepositoryType.SOLUTION.toString())) {
                try {
                    return Optional.of(tryToLoadParticipation(usingSSH, repositoryTypeOrUserName, localVCRepositoryUri, exercise, participationForRepository));
                }
                catch (LocalVCInternalException e) {
                    log.warn("Missing participation for staff repository {} in exercise {}. Continuing without participation-based logging.", localVCRepositoryUri,
                            exercise.getId(), e);
                    return Optional.empty();
                }
            }
            return Optional.empty();
        }

        ProgrammingExerciseParticipation participation = tryToLoadParticipation(usingSSH, repositoryTypeOrUserName, localVCRepositoryUri, exercise, participationForRepository);

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
        // This user is handed to the push hooks, and the submission handling behind them checks whether the pusher is an
        // instructor. Loading the course roles and authorities here keeps that check in memory; without them it re-reads
        // the whole user row (User#authorities is lazy) and then issues its own membership query, on every push.
        return userRepository.findOneWithCourseRolesAndAuthoritiesByLogin(username).orElseThrow(LocalVCAuthException::new);
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
            ProgrammingExercise exercise, Supplier<ProgrammingExerciseParticipation> participationForRepository) throws LocalVCInternalException {
        ProgrammingExerciseParticipation participation;
        try {
            if (usingSSH) {
                participation = programmingExerciseParticipationService.fetchParticipationWithSubmissionsByRepository(repositoryTypeOrUserName, localVCRepositoryUri.toString(),
                        exercise);
            }
            else {
                // Over HTTPS the caller resolved this during authentication, so reuse it rather than reading the same row again.
                participation = participationForRepository.get();
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
     * Checks if the repository is a staff-only repository (template, solution, tests, or auxiliary) and whether the user has access.
     * <p>
     * Students are denied access to template, solution, tests, and auxiliary repositories.
     * TAs can read but not write to these repositories. Editors and above have full access.
     * <p>
     * For auxiliary repositories, the check is only performed for users who are at least TA,
     * to avoid an unnecessary database query for students (since loading auxiliary repositories requires a DB call).
     * If a student requests an auxiliary repository, this method returns {@code false} and the check is deferred to
     * {@link LocalVCServletService#tryToLoadParticipation(boolean, String, LocalVCRepositoryUri, ProgrammingExercise)}.
     *
     * @param exercise                 the exercise the repository belongs to
     * @param repositoryTypeOrUserName the repository type name (e.g. "exercise", "solution", "tests") or the username for student repos
     * @param repositoryActionType     the action to be performed (READ or WRITE)
     * @param user                     the user requesting access
     * @return {@code true} if the repository is a staff-only repository and the user has access (caller can skip further checks).
     *         {@code false} if the repository is not a known staff-only type (caller should proceed with student participation checks).
     * @throws LocalVCForbiddenException if the user does not have the required permissions for the requested repository
     */
    private boolean checkAccessToStaffRepository(ProgrammingExercise exercise, String repositoryTypeOrUserName, RepositoryActionType repositoryActionType, User user)
            throws LocalVCForbiddenException {

        boolean isTemplateOrSolutionOrTestsRepo = repositoryTypeOrUserName.equals(RepositoryType.TESTS.toString())
                || repositoryTypeOrUserName.equals(RepositoryType.TEMPLATE.toString()) || repositoryTypeOrUserName.equals(RepositoryType.SOLUTION.toString());

        var course = exercise.getCourseViaExerciseGroupOrCourseMember();

        if (isTemplateOrSolutionOrTestsRepo) {
            // For WRITE operations, check editor permission first (avoids a second role check later)
            if (repositoryActionType == RepositoryActionType.WRITE) {
                if (!authorizationCheckService.isAtLeastEditorInCourse(course, user)) {
                    throw new LocalVCForbiddenException("You are not allowed to push to the " + repositoryTypeOrUserName + " repository of this programming exercise.");
                }
            }
            else if (!authorizationCheckService.isAtLeastTeachingAssistantInCourse(course, user)) {
                throw new LocalVCForbiddenException("You are not allowed to access the " + repositoryTypeOrUserName + " repository of this programming exercise.");
            }
            return true;
        }

        // For auxiliary repositories, only check if the user is at least TA (avoids unnecessary DB query for students)
        boolean isAtLeastTA = authorizationCheckService.isAtLeastTeachingAssistantInCourse(course, user);
        boolean isAuxiliaryRepo = isAtLeastTA && auxiliaryRepositoryService.isAuxiliaryRepositoryOfExercise(repositoryTypeOrUserName, exercise);

        if (!isAuxiliaryRepo) {
            // Not a staff-only repository — proceed with student participation checks
            return false;
        }

        // Auxiliary repository: TAs can read; writing requires at least editor permissions.
        if (repositoryActionType == RepositoryActionType.WRITE && !authorizationCheckService.isAtLeastEditorInCourse(course, user)) {
            throw new LocalVCForbiddenException("You are not allowed to push to the " + repositoryTypeOrUserName + " repository of this programming exercise.");
        }

        return true;
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
        // A git push knows the id it pushed, so it is also the commit that triggered this call
        processNewPush(commitHash, repository, user, cachedExercise, cachedParticipation, vcsAccessLog, commitHash);
    }

    /**
     * Process a new push, identifying the commit that triggered it.
     * <p>
     * The online editor commits through {@code RepositoryService} and reaches this method with no pushed hash, because the
     * hash the build is triggered for is resolved later. The id of the commit the request actually created still has to be
     * known here, so the resulting new commit alert can be attributed to the client that made that commit and to no other.
     *
     * @param commitHash           the hash of the last commit, may be null for a commit from the online editor
     * @param repository           the remote repository which was pushed to
     * @param user                 the user who pushed the commit
     * @param cachedExercise       the exercise which is potentially already loaded
     * @param cachedParticipation  the participation which is potentially already loaded
     * @param vcsAccessLog         the vcsAccessLog which is potentially already loaded
     * @param triggeringCommitHash the id of the commit this request created, or null when the caller does not know it
     */
    public void processNewPush(String commitHash, Repository repository, User user, Optional<ProgrammingExercise> cachedExercise,
            Optional<ProgrammingExerciseParticipation> cachedParticipation, Optional<VcsAccessLog> vcsAccessLog, @Nullable String triggeringCommitHash) {
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
                // The identified commit, not the repository head. Attribution has to name the commit this request created, and
                // re-reading the head here would be a race: the online editor shares one working copy per repository, so a
                // concurrent commit can move it and the alert would then be attributed to the wrong client.
                // An alert about an auxiliary repository names one specific repository by id, so attributing it needs that id too
                Long triggeringAuxiliaryRepositoryId = repositoryType == RepositoryType.AUXILIARY
                        ? auxiliaryRepositoryService.findAuxiliaryRepositoryIdOfExercise(repositoryTypeOrUserName, exercise).orElse(null)
                        : null;
                exerciseVersionService.createExerciseVersion(exercise, user, repositoryType, triggeringAuxiliaryRepositoryId, triggeringCommitHash);
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

            // Process push to any repository other than the test repository. The repository type is passed on so the build
            // trigger can reuse the hash this push already carries instead of reading it back off the repository.
            processNewPushToRepository(participation, commit, user, repositoryType);

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

    /**
     * Resolves the participation behind a repository at most once per request, and hands every caller exactly what a
     * direct call would have given them, including a failure.
     * <p>
     * Authentication and authorization both need it, so resolving it twice was two reads of the same row plus the eager
     * associations each of them brings. The outcome is cached rather than just the value, because the two callers treat
     * a missing participation differently: authentication moves on to the next credential, while authorization lets the
     * failure through so the auxiliary-repository fallback can handle it.
     *
     * @param repositoryTypeOrUserName the repository type or the user name taken from the repository URI
     * @param localVCRepositoryUri     the URI of the requested repository
     * @param exercise                 the programming exercise the repository belongs to
     * @return a supplier that resolves the participation once
     */
    private Supplier<ProgrammingExerciseParticipation> participationResolver(String repositoryTypeOrUserName, LocalVCRepositoryUri localVCRepositoryUri,
            ProgrammingExercise exercise) {
        return new Supplier<>() {

            private boolean resolved;

            private ProgrammingExerciseParticipation participation;

            private RuntimeException failure;

            @Override
            public ProgrammingExerciseParticipation get() {
                if (!resolved) {
                    resolved = true;
                    try {
                        participation = programmingExerciseParticipationService.fetchParticipationByRepository(repositoryTypeOrUserName, localVCRepositoryUri.toString(), exercise);
                    }
                    catch (RuntimeException e) {
                        failure = e;
                    }
                }
                if (failure != null) {
                    throw failure;
                }
                return participation;
            }
        };
    }

    /**
     * Resolves the participation for the credential check, where not finding one simply means this credential does not
     * apply and the next one should be tried.
     *
     * @param participationForRepository the shared resolver
     * @return the participation, or empty if there is none
     */
    private static Optional<ProgrammingExerciseParticipation> resolveQuietly(Supplier<ProgrammingExerciseParticipation> participationForRepository) {
        try {
            return Optional.ofNullable(participationForRepository.get());
        }
        catch (EntityNotFoundException e) {
            return Optional.empty();
        }
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
        if (repositoryTypeOrUserName.equals(RepositoryType.TEMPLATE.toString())) {
            return RepositoryType.TEMPLATE;
        }
        else if (repositoryTypeOrUserName.equals(RepositoryType.SOLUTION.toString())) {
            return RepositoryType.SOLUTION;
        }
        else if (repositoryTypeOrUserName.equals(RepositoryType.TESTS.toString())) {
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
        if (repositoryTypeOrUserName.equals(RepositoryType.TEMPLATE.toString())) {
            return RepositoryType.TEMPLATE;
        }
        else if (repositoryTypeOrUserName.equals(RepositoryType.SOLUTION.toString())) {
            return RepositoryType.SOLUTION;
        }
        else if (repositoryTypeOrUserName.equals(RepositoryType.TESTS.toString())) {
            return RepositoryType.TESTS;
        }
        return RepositoryType.USER;
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
    private void processNewPushToRepository(ProgrammingExerciseParticipation participation, Commit commit, User user, RepositoryType pushedRepositoryType) {
        // The 'user' is not properly logged into Artemis, this leads to an issue when accessing custom repository methods.
        // Therefore, a mock auth object has to be created.
        SecurityUtils.setAuthorizationObject();
        ProgrammingSubmission submission;
        try {
            submission = programmingSubmissionService.processNewProgrammingSubmission(participation, commit, user, pushedRepositoryType);
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
        // A build agent clone has its own audit entry already, and this method updates whichever entry is newest for
        // the repository, so running it here would relabel a student's entry as the agent's clone. Keyed on the
        // attribute the authorization set rather than on a username: an agent presenting its own short name never
        // matches the literal below, and neither does an installation that renamed the shared credential.
        if (request.getAttribute(BUILD_AGENT_CLONE_REQUEST_ATTRIBUTE) != null) {
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

            if (repositoryActionType == RepositoryActionType.CLONE) {
                User user = (User) request.getAttribute(AUTHENTICATED_USER_REQUEST_ATTRIBUTE);
                AuthenticationMechanism mechanism = (AuthenticationMechanism) request.getAttribute(AUTHENTICATION_MECHANISM_REQUEST_ATTRIBUTE);
                if (mechanism == AuthenticationMechanism.PASSWORD && user != null) {
                    try {
                        checkAndSendHttpsCloneEmail(user);
                    }
                    catch (Exception e) {
                        log.warn("Could not send HTTPS clone tip email for user {}: {}", user.getId(), e.getMessage());
                    }
                }
            }
        }
        catch (Exception e) {
            log.debug("Could not update VCS access log for HTTPS clone/pull: {}", e.getMessage());
        }
    }

    /**
     * Sends an email tip about using Token/SSH authentication instead of HTTPS password,
     * limited to at most once per 24 hours per user using Hazelcast cache.
     *
     * @param user The user performing the clone operation.
     */
    private void checkAndSendHttpsCloneEmail(User user) {
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            return;
        }

        DistributedMap<Long, Boolean> cache = distributedDataProvider.getExpiringMap(HTTPS_CLONE_EMAIL_CACHE, Duration.ofHours(24));
        // putIfAbsent returns null if cache is empty
        boolean isFirstTimeIn24Hours = cache.putIfAbsent(user.getId(), Boolean.TRUE, Duration.ofHours(24)) == null;

        // If cache was empty then send an email
        if (isFirstTimeIn24Hours) {
            MailRecipientDTO mailRecipient = new MailRecipientDTO(user.getEmail(), user.getLangKey(), user.getLogin(), user.getFirstName(), user.getLastName(), null, null);

            mailSendingService.buildAndSendAsync(mailRecipient, "email.httpsCloneTip.title", "mail/httpsCloneTipEmail", Map.of());
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
        catch (Exception e) {
            log.debug("Could not update VCS access log for SSH clone/pull: {}", e.getMessage());
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

    record UsernameAndPassword(@NonNull String username, @NonNull String password) {
    }
}
