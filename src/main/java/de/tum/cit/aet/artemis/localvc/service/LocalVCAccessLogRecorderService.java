package de.tum.cit.aet.artemis.localvc.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALVC;
import static de.tum.cit.aet.artemis.localvc.service.LocalVCBuildAgentAuthenticationService.BUILD_AGENT_CLONE_REQUEST_ATTRIBUTE;
import static de.tum.cit.aet.artemis.localvc.service.LocalVCBuildAgentAuthenticationService.BUILD_USER_NAME;
import static de.tum.cit.aet.artemis.localvc.service.LocalVCCredentials.extractUsernameAndPassword;
import static de.tum.cit.aet.artemis.localvc.service.LocalVCCredentials.secretMatches;
import static de.tum.cit.aet.artemis.localvc.service.LocalVCPersonalAccessTokenManagementService.TOKEN_PREFIX;

import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;

import org.apache.sshd.server.session.ServerSession;
import org.eclipse.jgit.lib.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participation;
import de.tum.cit.aet.artemis.localvc.exception.LocalVCAuthException;
import de.tum.cit.aet.artemis.localvc.service.LocalVCCredentials.UsernameAndPassword;
import de.tum.cit.aet.artemis.localvc.service.ssh.SshConstants;
import de.tum.cit.aet.artemis.programming.domain.AuthenticationMechanism;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.VcsAccessLog;
import de.tum.cit.aet.artemis.programming.repository.RepositoryVCSAccessTokenRepository;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseParticipationService;
import de.tum.cit.aet.artemis.programming.web.repository.RepositoryActionType;

/**
 * Writes the VCS access log entries of git requests that are served, over HTTPS and over SSH, and works out how a request authenticated.
 * <p>
 * An entry is written in up to three steps: a preliminary entry when the request is authorized, which the clone, pull or push then completes with what only it knows,
 * and a failed entry when authorization or authentication is rejected.
 */
@Lazy
@Service
@Profile(PROFILE_LOCALVC)
public class LocalVCAccessLogRecorderService {

    private static final Logger log = LoggerFactory.getLogger(LocalVCAccessLogRecorderService.class);

    // TODO As soon as only LocalVC is supported, this Optional can be removed
    private final Optional<VcsAccessLogService> vcsAccessLogService;

    private final LocalVCRepositoryResolverService repositoryResolver;

    private final UserRepository userRepository;

    private final UserVcsAccessTokenService userVcsAccessTokenService;

    private final RepositoryVCSAccessTokenRepository repositoryVCSAccessTokenRepository;

    private final ProgrammingExerciseParticipationService programmingExerciseParticipationService;

    public LocalVCAccessLogRecorderService(Optional<VcsAccessLogService> vcsAccessLogService, LocalVCRepositoryResolverService repositoryResolver, UserRepository userRepository,
            UserVcsAccessTokenService userVcsAccessTokenService, RepositoryVCSAccessTokenRepository repositoryVCSAccessTokenRepository,
            ProgrammingExerciseParticipationService programmingExerciseParticipationService) {
        this.vcsAccessLogService = vcsAccessLogService;
        this.repositoryResolver = repositoryResolver;
        this.userRepository = userRepository;
        this.userVcsAccessTokenService = userVcsAccessTokenService;
        this.repositoryVCSAccessTokenRepository = repositoryVCSAccessTokenRepository;
        this.programmingExerciseParticipationService = programmingExerciseParticipationService;
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
    public void savePreliminaryVcsAccessLogForHTTPs(HttpServletRequest request, LocalVCRepositoryUri localVCRepositoryUri, User user, RepositoryActionType repositoryAction,
            Optional<ProgrammingExerciseParticipation> optionalParticipation, AuthenticationMechanism authenticationMechanism) throws LocalVCAuthException {
        if (optionalParticipation.isPresent()) {
            ProgrammingExerciseParticipation participation = optionalParticipation.get();
            var ipAddress = request.getRemoteAddr();

            String finalCommitHash = repositoryResolver.getCommitHash(localVCRepositoryUri);
            RepositoryActionType finalRepositoryAction = repositoryAction == RepositoryActionType.WRITE ? RepositoryActionType.PUSH : RepositoryActionType.PULL;
            vcsAccessLogService.ifPresent(service -> service.saveAccessLog(user, participation, finalRepositoryAction, authenticationMechanism, finalCommitHash, ipAddress));
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
    public AuthenticationMechanism resolveAuthenticationMechanismFromSessionOrRequest(AuthenticationContext context, User user, LocalVCRepositoryUri localVCRepositoryUri) {
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
                try (Repository repository = repositoryResolver.resolveRepository(relativeRepositoryPath)) {
                    commitHash = repositoryResolver.getLatestCommitHash(repository);
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

            LocalVCRepositoryUri localVCRepositoryUri = repositoryResolver.parseRepositoryUri(request);
            RepositoryActionType repositoryActionType = getRepositoryActionReadType(clientOffered);

            vcsAccessLogService.ifPresent(service -> service.updateRepositoryActionType(localVCRepositoryUri, repositoryActionType));
        }
        catch (Exception e) {
            log.debug("Could not update VCS access log for HTTPS clone/pull: {}", e.getMessage());
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
            // Both attributes are absent on a session that never got that far, which is a normal case rather than a
            // failure. They used to be dereferenced straight away, so the absence arrived as a NullPointerException
            // caught below and logged at debug, indistinguishable from an actual problem with the access log.
            var user = session.getAttribute(SshConstants.USER_KEY);
            if (user == null || user.getName().equals(BUILD_USER_NAME)) {
                return;
            }
            var accessLog = session.getAttribute(SshConstants.VCS_ACCESS_LOG_KEY);
            if (accessLog == null) {
                return;
            }
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
            LocalVCRepositoryUri localVCRepositoryUri = repositoryResolver.parseRepositoryUri(servletRequest);
            // One id, no entity. The log stores the participation as a foreign key and reads nothing from it, so
            // nothing more is loaded: the previous call fetched the participation with its submissions, and reached
            // them through an exercise that was passed as null, which is what threw the NullPointerException.
            var participation = programmingExerciseParticipationService.getParticipationReferenceForRepository(localVCRepositoryUri.getRepositoryTypeOrUserName(),
                    localVCRepositoryUri.toString(), localVCRepositoryUri.getProjectKey());
            if (participation.isEmpty()) {
                return;
            }
            var ipAddress = servletRequest.getRemoteAddr();
            vcsAccessLogService.ifPresent(service -> service.saveAccessLog(user, participation.get(), RepositoryActionType.CLONE_FAIL, mechanism, "", ipAddress));
        }
        catch (LocalVCAuthException | EntityNotFoundException ignored) {
            // Caught when: 1) no user, or 2) no exercise or participation was found. In none of these cases does it make sense to write a log
        }
        catch (RuntimeException e) {
            // This runs while a failed authentication is being answered, so nothing that happens here may replace the
            // 401 the caller is about to send. Writing the access log is best effort by nature: it describes an attempt
            // that was already rejected.
            log.warn("Could not write the VCS access log for a failed authentication on {}", servletRequest.getRequestURI(), e);
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
}
