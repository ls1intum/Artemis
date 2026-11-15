package de.tum.cit.aet.artemis.localvc.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALVC;
import static de.tum.cit.aet.artemis.core.util.HttpRequestUtils.getPeerIpAddress;
import static de.tum.cit.aet.artemis.localvc.service.LocalVCCredentials.extractUsernameAndPassword;
import static de.tum.cit.aet.artemis.localvc.service.LocalVCCredentials.secretMatches;
import static de.tum.cit.aet.artemis.localvc.service.LocalVCPersonalAccessTokenManagementService.TOKEN_PREFIX;
import static de.tum.cit.aet.artemis.localvc.service.LocalVCPersonalAccessTokenManagementService.VCS_ACCESS_TOKEN_LENGTH;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import jakarta.servlet.http.HttpServletRequest;

import org.eclipse.jgit.lib.Repository;
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

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.admin.service.RateLimitService;
import de.tum.cit.aet.artemis.core.config.BuildAgentNetworkPolicy;
import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.exception.RateLimitExceededException;
import de.tum.cit.aet.artemis.core.security.RateLimitType;
import de.tum.cit.aet.artemis.core.security.SecurityUtils;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.util.TimeLogUtil;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.localvc.exception.LocalVCAuthException;
import de.tum.cit.aet.artemis.localvc.exception.LocalVCForbiddenException;
import de.tum.cit.aet.artemis.localvc.exception.LocalVCInternalException;
import de.tum.cit.aet.artemis.localvc.service.LocalVCCredentials.UsernameAndPassword;
import de.tum.cit.aet.artemis.programming.domain.AuthenticationMechanism;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildConfig;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.dto.GitRepositoryAccessDTO;
import de.tum.cit.aet.artemis.programming.repository.ParticipationVCSAccessTokenRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseBuildConfigRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.repository.RepositoryVCSAccessTokenRepository;
import de.tum.cit.aet.artemis.programming.service.AuxiliaryRepositoryService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseParticipationService;
import de.tum.cit.aet.artemis.programming.service.RepositoryAccessService;
import de.tum.cit.aet.artemis.programming.web.repository.RepositoryActionType;
import inet.ipaddr.IPAddress;

/**
 * This service is responsible for authenticating and authorizing git requests, over https and over ssh.
 * It is used by the ArtemisGitServletService, the LocalVCFetchFilter, and the LocalVCPushFilter.
 * <p>
 * The parts that do not decide who may access a repository live in their own services: {@link LocalVCRepositoryResolverService} finds repositories on disk,
 * {@link LocalVCBuildAgentAuthenticationService} recognises build agents, {@link LocalVCAccessLogRecorderService} writes the access log, {@link LocalVCPushProcessingService}
 * handles what a push leads to, and {@link PasswordAuthenticationNoticeService} tells users who authenticate with a password to switch.
 */
@Lazy
@Service
@Profile(PROFILE_LOCALVC)
// TODO: we should rename this because its used in the context of https and ssh git operations
public class LocalVCServletService {

    static final String AUTHORIZED_EXERCISE_ATTRIBUTE = LocalVCServletService.class.getName() + ".authorizedExercise";

    private static final Logger log = LoggerFactory.getLogger(LocalVCServletService.class);

    private static final String AUTHENTICATION_MECHANISM_REQUEST_ATTRIBUTE = "artemis.authenticationMechanism";

    private final AuthenticationManager authenticationManager;

    private final UserRepository userRepository;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final ProgrammingExerciseBuildConfigRepository programmingExerciseBuildConfigRepository;

    private final RepositoryAccessService repositoryAccessService;

    private final ProgrammingExerciseParticipationService programmingExerciseParticipationService;

    private final AuxiliaryRepositoryService auxiliaryRepositoryService;

    // TODO As soon as only LocalVC is supported, this Optional can be removed
    private final Optional<VcsAccessLogService> vcsAccessLogService;

    private final ParticipationVCSAccessTokenRepository participationVCSAccessTokenRepository;

    private final RepositoryVCSAccessTokenRepository repositoryVCSAccessTokenRepository;

    private final AuthorizationCheckService authorizationCheckService;

    private final RateLimitService rateLimitService;

    private final UserVcsAccessTokenService userVcsAccessTokenService;

    private final LocalVCRepositoryResolverService repositoryResolver;

    private final LocalVCAccessLogRecorderService accessLogRecorder;

    private final LocalVCBuildAgentAuthenticationService buildAgentAuthenticationService;

    private final PasswordAuthenticationNoticeService passwordAuthenticationNoticeService;

    private final BuildAgentNetworkPolicy buildAgentNetworkPolicy;

    private final long maxRepositorySizeMb;

    private final long maxTestsAndSolutionRepositorySizeMb;

    public LocalVCServletService(AuthenticationManager authenticationManager, UserRepository userRepository, ProgrammingExerciseRepository programmingExerciseRepository,
            ProgrammingExerciseBuildConfigRepository programmingExerciseBuildConfigRepository, RepositoryAccessService repositoryAccessService,
            ProgrammingExerciseParticipationService programmingExerciseParticipationService, AuxiliaryRepositoryService auxiliaryRepositoryService,
            Optional<VcsAccessLogService> vcsAccessLogService, ParticipationVCSAccessTokenRepository participationVCSAccessTokenRepository,
            RepositoryVCSAccessTokenRepository repositoryVCSAccessTokenRepository, AuthorizationCheckService authorizationCheckService, RateLimitService rateLimitService,
            UserVcsAccessTokenService userVcsAccessTokenService, LocalVCRepositoryResolverService repositoryResolver, LocalVCAccessLogRecorderService accessLogRecorder,
            LocalVCBuildAgentAuthenticationService buildAgentAuthenticationService, PasswordAuthenticationNoticeService passwordAuthenticationNoticeService,
            BuildAgentNetworkPolicy buildAgentNetworkPolicy, @Value("${artemis.version-control.max-repository-size-mb:20}") long maxRepositorySizeMb,
            @Value("${artemis.version-control.max-tests-and-solution-repository-size-mb:50}") long maxTestsAndSolutionRepositorySizeMb) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.programmingExerciseBuildConfigRepository = programmingExerciseBuildConfigRepository;
        this.repositoryAccessService = repositoryAccessService;
        this.programmingExerciseParticipationService = programmingExerciseParticipationService;
        this.auxiliaryRepositoryService = auxiliaryRepositoryService;
        this.vcsAccessLogService = vcsAccessLogService;
        this.participationVCSAccessTokenRepository = participationVCSAccessTokenRepository;
        this.repositoryVCSAccessTokenRepository = repositoryVCSAccessTokenRepository;
        this.authorizationCheckService = authorizationCheckService;
        this.rateLimitService = rateLimitService;
        this.userVcsAccessTokenService = userVcsAccessTokenService;
        this.repositoryResolver = repositoryResolver;
        this.accessLogRecorder = accessLogRecorder;
        this.buildAgentAuthenticationService = buildAgentAuthenticationService;
        this.passwordAuthenticationNoticeService = passwordAuthenticationNoticeService;
        this.buildAgentNetworkPolicy = buildAgentNetworkPolicy;
        this.maxRepositorySizeMb = maxRepositorySizeMb;
        this.maxTestsAndSolutionRepositorySizeMb = maxTestsAndSolutionRepositorySizeMb;
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
        if (repositoryAction == RepositoryActionType.READ && buildAgentAuthenticationService.authenticateBuildJobCloneToken(request, authorizationHeader)) {
            return;
        }

        // A CI system cloning with the credential that the whole installation shares. The shortcut grants repository-wide
        // read ahead of the rate limit, the authorization checks and the access log, which is why it is closed wherever
        // local CI or ssh can do the job.
        if (repositoryAction == RepositoryActionType.READ && buildAgentAuthenticationService.authenticateSharedBuildAgentCredential(request, authorizationHeader)) {
            return;
        }

        // The initial handshake request of an operation (/info/refs) spends one token before its credentials are checked. A correct password gives it back in authenticateUser.
        // The data transfer requests (git-upload-pack, git-receive-pack) reuse the credentials of the handshake, so they spend
        // nothing here. A data transfer request that carries a password is charged in authenticateUser instead and gets the token back when the password is correct.
        if (isHandshakeRequest(request)) {
            spendAuthenticationRateLimit(request);
        }

        LocalVCRepositoryUri localVCRepositoryUri = repositoryResolver.parseRepositoryUri(request);
        log.debug("Parsed repository URI from request: {}", localVCRepositoryUri);
        String projectKey = localVCRepositoryUri.getProjectKey();
        String repositoryTypeOrUserName = localVCRepositoryUri.getRepositoryTypeOrUserName();

        // A projection rather than the exercise: this runs on every clone, fetch and push, and the entity carried its
        // course with it - twice for an exam exercise, since the course is reachable both directly and through the
        // exercise group's exam. Nothing below reads more than an id, a flag or a date.
        GitRepositoryAccessDTO exercise = getAccessProjectionOrThrow(projectKey);

        // The participation behind this repository is needed twice: once to find the participation-scoped token during
        // authentication, and once to authorize the repository access. Resolving it once, lazily, means the common case
        // (a student pushing or fetching with their participation token) loads it a single time instead of twice, and
        // the requests that never need it (a staff token, a failed credential) still do not pay for it.
        Supplier<ProgrammingExerciseParticipation> participationForRepository = participationResolver(repositoryTypeOrUserName, localVCRepositoryUri, exercise.exerciseId());

        var authenticated = authenticateUser(request, authorizationHeader, exercise, localVCRepositoryUri, participationForRepository);
        User user = authenticated.user();
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
                accessLogRecorder.savePreliminaryVcsAccessLogForHTTPs(request, localVCRepositoryUri, user, repositoryAction, optionalParticipation, authenticated.mechanism());
            }
        }
        catch (LocalVCForbiddenException e) {
            log.warn("User {} does not have access to the repository {}", user.getLogin(), localVCRepositoryUri);
            saveFailedAccessVcsAccessLog(new AuthenticationContext.Request(request), repositoryTypeOrUserName, exercise.exerciseId(), localVCRepositoryUri, user, repositoryAction);
            throw e;
        }

        request.setAttribute(AUTHORIZED_EXERCISE_ATTRIBUTE, exercise);

        if (authenticated.mechanism() == AuthenticationMechanism.PASSWORD) {
            passwordAuthenticationNoticeService.sendPasswordAuthenticationEmail(user, exercise);
        }

        log.debug("Authorizing user {} for repository {} took {}", user.getLogin(), localVCRepositoryUri, TimeLogUtil.formatDurationFrom(timeNanoStart));
    }

    /**
     * Logs a failed attempt to access a repository.
     *
     * @param context                  the Authentication context
     * @param repositoryTypeOrUserName A string representing either the repository type or the username associated with the repository.
     * @param exerciseId               The id of the exercise associated with the repository.
     * @param localVCRepositoryUri     The {@link LocalVCRepositoryUri} representing the repository location.
     * @param user                     The {@link User} attempting the access.
     * @param repositoryAction         The {@link RepositoryActionType} action that was attempted.
     */
    public void saveFailedAccessVcsAccessLog(AuthenticationContext context, String repositoryTypeOrUserName, long exerciseId, LocalVCRepositoryUri localVCRepositoryUri, User user,
            RepositoryActionType repositoryAction) {
        try {
            var participation = tryToLoadParticipation(false, repositoryTypeOrUserName, localVCRepositoryUri, exerciseId,
                    participationResolver(repositoryTypeOrUserName, localVCRepositoryUri, exerciseId));
            var commitHash = repositoryResolver.getCommitHash(localVCRepositoryUri);
            var authenticationMechanism = accessLogRecorder.resolveAuthenticationMechanismFromSessionOrRequest(context, user, localVCRepositoryUri);
            var action = repositoryAction == RepositoryActionType.WRITE ? RepositoryActionType.PUSH_FAIL : RepositoryActionType.CLONE_FAIL;
            var ipAddress = context.getIpAddress();
            vcsAccessLogService.ifPresent(service -> service.saveAccessLog(user, participation, action, authenticationMechanism, commitHash, ipAddress));
        }
        catch (Exception e) {
            log.warn("Failed to save VCS access log for failed access attempt by user {} to repository {}: {}", user.getLogin(), localVCRepositoryUri, e.getMessage());
        }
    }

    /**
     * Authenticates a user based on the provided authorization header for a specific programming exercise/repository.
     * Authentication is tried with: 1) user VCS access token, 2) user participation VCS access token 3) password
     *
     * @param authorizationHeader  the authorization header containing authentication credentials
     * @param exercise             the projected exercise the user is attempting to access
     * @param localVCRepositoryUri the URI of the local version control repository the user is attempting to access
     * @return the authenticated {@link User} if authentication is successful
     * @throws LocalVCAuthException    if an error occurs during authentication with the local version control system
     * @throws AuthenticationException if the authentication credentials are invalid or authentication fails
     */
    private AuthenticatedUser authenticateUser(HttpServletRequest request, String authorizationHeader, GitRepositoryAccessDTO exercise, LocalVCRepositoryUri localVCRepositoryUri,
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
        // The handshake request was already charged one token before its credentials were looked at. A data transfer request that skips the handshake and sends a password
        // spends one token before the password is checked, so that parallel requests cannot all pass a budget check first. Either token is given back when the password is
        // correct, so a regular clone or push is not charged for its several requests, and it can complete even where the allowance is a single token.
        boolean dataTransferRequest = !isHandshakeRequest(request);
        if (dataTransferRequest) {
            spendAuthenticationRateLimit(request);
        }
        UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(username, passwordOrToken);
        authenticationManager.authenticate(authenticationToken);
        rateLimitService.refundPerMinute(resolveClientAddress(request), RateLimitType.GIT_AUTHENTICATION);

        return new AuthenticatedUser(user, AuthenticationMechanism.PASSWORD);
    }

    private static boolean isHandshakeRequest(HttpServletRequest request) {
        return request.getRequestURI().endsWith("/info/refs");
    }

    /**
     * The address of the client as the proxies of this installation report it. The servlet container already resolves X-Forwarded-For for the proxies in
     * server.tomcat.remoteip.internal-proxies (the private networks by default), and the trusted proxies of the build agent network policy are believed in addition,
     * exactly as for the other git requests. A header sent by any other peer does not choose the budget.
     */
    private IPAddress resolveClientAddress(HttpServletRequest request) {
        return getPeerIpAddress(request, buildAgentNetworkPolicy::isTrustedProxy).orElse(null);
    }

    /**
     * Spends one token of the authentication budget of the client address and throws once the budget is spent.
     *
     * @param request the request to charge
     */
    private void spendAuthenticationRateLimit(HttpServletRequest request) {
        rateLimitService.enforcePerMinute(resolveClientAddress(request), RateLimitType.GIT_AUTHENTICATION);
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
     * @param exercise             the projected exercise containing the repository the user tries to access
     * @param localVCRepositoryUri the URI of the local version control repository the user tries to access
     * @return {@code true} if the authentication is successful, {@code false} otherwise
     */
    private boolean tryAuthenticationWithParticipationVCSAccessToken(User user, String providedToken, GitRepositoryAccessDTO exercise,
            Supplier<ProgrammingExerciseParticipation> participationForRepository) throws LocalVCAuthException {

        // Note: we first check if the user has used a vcs access token instead of a password
        if (providedToken.startsWith(TOKEN_PREFIX) && providedToken.length() == VCS_ACCESS_TOKEN_LENGTH) {
            try {
                // check participation vcs access token. For an individual exercise this is the participation behind the
                // requested repository, which authorization resolves anyway, so it is shared rather than looked up again.
                // The same holds for a practice repository of a team exercise: it belongs to the student alone, not to the team.
                Optional<ProgrammingExerciseParticipation> requestedParticipation = resolveQuietly(participationForRepository);
                boolean practiceRepository = requestedParticipation
                        .filter(participation -> participation instanceof StudentParticipation studentParticipation && studentParticipation.isPracticeMode()).isPresent();
                Optional<Long> participationId;
                if (exercise.isTeamMode() && !practiceRepository) {
                    participationId = programmingExerciseParticipationService.findTeamParticipationByExerciseAndUser(exercise.exerciseId(), user).map(DomainObject::getId);
                }
                else {
                    participationId = requestedParticipation.map(ProgrammingExerciseParticipation::getId);
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
        LocalVCRepositoryUri localVCRepositoryUri = repositoryResolver.parseRepositoryUri(repository.getDirectory().toPath());
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
        LocalVCRepositoryUri localVCRepositoryUri = repositoryResolver.parseRepositoryUri(repository.getDirectory().toPath());
        String projectKey = localVCRepositoryUri.getProjectKey();

        return getBuildConfigOrThrow(projectKey).isAllowBranching();
    }

    /**
     * Returns the maximum size of the files of a repository, which a push must not exceed. Template repositories and student repositories (and everything that is not a tests or
     * solution repository) have a smaller limit than tests and solution repositories.
     *
     * @param repository the repository that receives the push
     * @return the limit in bytes, or {@link Long#MAX_VALUE} if the limit is disabled by configuration
     */
    public long getMaxRepositorySizeBytes(Repository repository) {
        String repositoryTypeOrUserName = repositoryResolver.parseRepositoryUri(repository.getDirectory().toPath()).getRepositoryTypeOrUserName();
        boolean isTestsOrSolutionRepository = repositoryTypeOrUserName.equals(RepositoryType.TESTS.toString())
                || repositoryTypeOrUserName.equals(RepositoryType.SOLUTION.toString());
        long limitInMegabytes = isTestsOrSolutionRepository ? maxTestsAndSolutionRepositorySizeMb : maxRepositorySizeMb;
        return limitInMegabytes > 0 ? limitInMegabytes * 1024 * 1024 : Long.MAX_VALUE;
    }

    /** The longest branch name that is matched against the branch expression of an exercise. */
    private static final int MAX_BRANCH_NAME_LENGTH = 255;

    private static final int MAX_COMPILED_BRANCH_REGEXES = 1000;

    private static final Cache<String, Pattern> COMPILED_BRANCH_REGEXES = Caffeine.newBuilder().maximumSize(MAX_COMPILED_BRANCH_REGEXES).build();

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
        LocalVCRepositoryUri localVCRepositoryUri = repositoryResolver.parseRepositoryUri(repository.getDirectory().toPath());
        String projectKey = localVCRepositoryUri.getProjectKey();

        ProgrammingExerciseBuildConfig buildConfig = getBuildConfigOrThrow(projectKey);

        if (!buildConfig.isAllowBranching() || buildConfig.getBranchRegex() == null) {
            return BranchingStatus.BRANCHING_DISABLED;
        }

        if (branchName.length() > MAX_BRANCH_NAME_LENGTH) {
            return BranchingStatus.NAME_DOES_NOT_MATCH_REGEX;
        }

        // The expression is compiled once and then reused by the following pushes
        String branchRegex = buildConfig.getBranchRegex();
        Pattern pattern = COMPILED_BRANCH_REGEXES.getIfPresent(branchRegex);
        if (pattern == null) {
            try {
                pattern = Pattern.compile(branchRegex);
            }
            catch (PatternSyntaxException e) {
                return BranchingStatus.NAME_DOES_NOT_MATCH_REGEX;
            }
            COMPILED_BRANCH_REGEXES.put(branchRegex, pattern);
        }

        return pattern.matcher(branchName).matches() ? BranchingStatus.BRANCH_ALLOWED : BranchingStatus.NAME_DOES_NOT_MATCH_REGEX;
    }

    private ProgrammingExercise getProgrammingExerciseOrThrow(String projectKey) {
        try {
            return programmingExerciseRepository.findOneByProjectKeyOrThrow(projectKey, false);
        }
        catch (EntityNotFoundException e) {
            throw new LocalVCInternalException("Could not find single programming exercise with project key " + projectKey, e);
        }
    }

    /**
     * Reads the build configuration of the single exercise in a project, without loading the exercise itself: the
     * configuration is a row of its own that names the exercise.
     *
     * @param projectKey the project key taken from the repository URI
     * @return the build configuration of the exercise in that project
     */
    private ProgrammingExerciseBuildConfig getBuildConfigOrThrow(String projectKey) {
        List<ProgrammingExerciseBuildConfig> buildConfigs = programmingExerciseBuildConfigRepository.findAllByProjectKey(projectKey);
        if (buildConfigs.size() != 1) {
            throw new LocalVCInternalException("Could not find single programming exercise with project key " + projectKey);
        }
        return buildConfigs.getFirst();
    }

    /**
     * Reads the values the authorization decision needs, without the exercise entity behind them.
     *
     * @param projectKey the project key taken from the repository URI
     * @return the projection for that project key
     */
    private GitRepositoryAccessDTO getAccessProjectionOrThrow(String projectKey) {
        List<GitRepositoryAccessDTO> exercises = programmingExerciseRepository.findAccessProjectionByProjectKey(projectKey);
        if (exercises.size() != 1) {
            throw new LocalVCInternalException("Could not find single programming exercise with project key " + projectKey);
        }
        return exercises.getFirst();
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
        return authorizeUser(repositoryTypeOrUserName, user, GitRepositoryAccessDTO.of(exercise), repositoryActionType, localVCRepositoryUri, usingSSH,
                participationResolver(repositoryTypeOrUserName, localVCRepositoryUri, exercise.getId()));
    }

    /**
     * Authorizes a user for a repository, reusing a participation the caller has already resolved.
     *
     * @param repositoryTypeOrUserName   the repository type or the user name taken from the repository URI
     * @param user                       the user requesting access
     * @param exercise                   the projected exercise the repository belongs to
     * @param repositoryActionType       whether the request reads or writes
     * @param localVCRepositoryUri       the URI of the requested repository
     * @param usingSSH                   whether the request arrived over SSH
     * @param participationForRepository the participation behind the repository, if the caller already resolved it
     * @return the participation the access was authorized against, empty for repositories that have none
     * @throws LocalVCForbiddenException if the user is not allowed to access the repository
     */
    public Optional<ProgrammingExerciseParticipation> authorizeUser(String repositoryTypeOrUserName, User user, GitRepositoryAccessDTO exercise,
            RepositoryActionType repositoryActionType, LocalVCRepositoryUri localVCRepositoryUri, boolean usingSSH,
            Supplier<ProgrammingExerciseParticipation> participationForRepository) throws LocalVCForbiddenException {

        if (checkAccessToStaffRepository(exercise, repositoryTypeOrUserName, repositoryActionType, user)) {
            // For tests and auxiliary repos, no participation is needed (they don't have dedicated participations).
            // For template and solution repos, load the participation so callers can use it for access logging.
            if (repositoryTypeOrUserName.equals(RepositoryType.TEMPLATE.toString()) || repositoryTypeOrUserName.equals(RepositoryType.SOLUTION.toString())) {
                try {
                    return Optional.of(tryToLoadParticipation(usingSSH, repositoryTypeOrUserName, localVCRepositoryUri, exercise.exerciseId(), participationForRepository));
                }
                catch (LocalVCInternalException e) {
                    log.warn("Missing participation for staff repository {} in exercise {}. Continuing without participation-based logging.", localVCRepositoryUri,
                            exercise.exerciseId(), e);
                    return Optional.empty();
                }
            }
            return Optional.empty();
        }

        ProgrammingExerciseParticipation participation = tryToLoadParticipation(usingSSH, repositoryTypeOrUserName, localVCRepositoryUri, exercise.exerciseId(),
                participationForRepository);

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
    private ProgrammingExerciseParticipation tryToLoadParticipation(boolean usingSSH, String repositoryTypeOrUserName, LocalVCRepositoryUri localVCRepositoryUri, long exerciseId,
            Supplier<ProgrammingExerciseParticipation> participationForRepository) throws LocalVCInternalException {
        ProgrammingExerciseParticipation participation;
        try {
            if (usingSSH) {
                participation = programmingExerciseParticipationService.fetchParticipationWithSubmissionsByRepository(repositoryTypeOrUserName, localVCRepositoryUri.toString(),
                        exerciseId);
            }
            else {
                // Over HTTPS the caller resolved this during authentication, so reuse it rather than reading the same row again.
                participation = participationForRepository.get();
            }
        }
        catch (EntityNotFoundException e) {
            // If the repository was not found, this could mean it is an auxiliary repository (which do not have participations)
            if (auxiliaryRepositoryService.isAuxiliaryRepositoryOfExercise(repositoryTypeOrUserName, exerciseId)) {
                return programmingExerciseParticipationService.findSolutionParticipationByProgrammingExerciseId(exerciseId);
            }
            throw new LocalVCInternalException("No participation found for repository with repository type or username " + repositoryTypeOrUserName + " in exercise " + exerciseId,
                    e);
        }
        return participation;
    }

    private void checkAccessForRepository(ProgrammingExerciseParticipation participation, User user, GitRepositoryAccessDTO exercise, RepositoryActionType repositoryActionType)
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
     * {@link LocalVCServletService#tryToLoadParticipation(boolean, String, LocalVCRepositoryUri, long, Supplier)}.
     *
     * @param exercise                 the projected exercise the repository belongs to
     * @param repositoryTypeOrUserName the repository type name (e.g. "exercise", "solution", "tests") or the username for student repos
     * @param repositoryActionType     the action to be performed (READ or WRITE)
     * @param user                     the user requesting access
     * @return {@code true} if the repository is a staff-only repository and the user has access (caller can skip further checks).
     *         {@code false} if the repository is not a known staff-only type (caller should proceed with student participation checks).
     * @throws LocalVCForbiddenException if the user does not have the required permissions for the requested repository
     */
    private boolean checkAccessToStaffRepository(GitRepositoryAccessDTO exercise, String repositoryTypeOrUserName, RepositoryActionType repositoryActionType, User user)
            throws LocalVCForbiddenException {

        boolean isTemplateOrSolutionOrTestsRepo = repositoryTypeOrUserName.equals(RepositoryType.TESTS.toString())
                || repositoryTypeOrUserName.equals(RepositoryType.TEMPLATE.toString()) || repositoryTypeOrUserName.equals(RepositoryType.SOLUTION.toString());

        long courseId = exercise.courseId();

        if (isTemplateOrSolutionOrTestsRepo) {
            // For WRITE operations, check editor permission first (avoids a second role check later)
            if (repositoryActionType == RepositoryActionType.WRITE) {
                if (!authorizationCheckService.isAtLeastEditorInCourse(courseId, user)) {
                    throw new LocalVCForbiddenException("You are not allowed to push to the " + repositoryTypeOrUserName + " repository of this programming exercise.");
                }
            }
            else if (!authorizationCheckService.isAtLeastTeachingAssistantInCourse(courseId, user)) {
                throw new LocalVCForbiddenException("You are not allowed to access the " + repositoryTypeOrUserName + " repository of this programming exercise.");
            }
            return true;
        }

        // For auxiliary repositories, only check if the user is at least TA (avoids unnecessary DB query for students)
        boolean isAtLeastTA = authorizationCheckService.isAtLeastTeachingAssistantInCourse(courseId, user);
        boolean isAuxiliaryRepo = isAtLeastTA && auxiliaryRepositoryService.isAuxiliaryRepositoryOfExercise(repositoryTypeOrUserName, exercise.exerciseId());

        if (!isAuxiliaryRepo) {
            // Not a staff-only repository — proceed with student participation checks
            return false;
        }

        // Auxiliary repository: TAs can read; writing requires at least editor permissions.
        if (repositoryActionType == RepositoryActionType.WRITE && !authorizationCheckService.isAtLeastEditorInCourse(courseId, user)) {
            throw new LocalVCForbiddenException("You are not allowed to push to the " + repositoryTypeOrUserName + " repository of this programming exercise.");
        }

        return true;
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
     * @param exerciseId               the id of the exercise the repository belongs to
     * @return a supplier that resolves the participation once
     */
    private Supplier<ProgrammingExerciseParticipation> participationResolver(String repositoryTypeOrUserName, LocalVCRepositoryUri localVCRepositoryUri, long exerciseId) {
        return new Supplier<>() {

            private boolean resolved;

            private ProgrammingExerciseParticipation participation;

            private RuntimeException failure;

            @Override
            public ProgrammingExerciseParticipation get() {
                if (!resolved) {
                    resolved = true;
                    try {
                        participation = programmingExerciseParticipationService.fetchParticipationByRepository(repositoryTypeOrUserName, localVCRepositoryUri.toString(),
                                exerciseId);
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

    /**
     * Reads how a request authenticated, as recorded when the request was authorized.
     *
     * @param request a request that went through {@link #authenticateAndAuthorizeGitRequest}
     * @return the mechanism, or {@code null} if the request was not authenticated
     */
    @Nullable
    public AuthenticationMechanism getAuthenticationMechanism(HttpServletRequest request) {
        return (AuthenticationMechanism) request.getAttribute(AUTHENTICATION_MECHANISM_REQUEST_ATTRIBUTE);
    }
}
