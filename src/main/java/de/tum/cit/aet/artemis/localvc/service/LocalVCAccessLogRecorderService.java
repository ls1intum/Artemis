package de.tum.cit.aet.artemis.localvc.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALVC;
import static de.tum.cit.aet.artemis.localvc.service.LocalVCBuildAgentAuthenticationService.BUILD_AGENT_CLONE_REQUEST_ATTRIBUTE;
import static de.tum.cit.aet.artemis.localvc.service.LocalVCCredentials.extractUsernameAndPassword;
import static de.tum.cit.aet.artemis.localvc.service.LocalVCCredentials.secretMatches;
import static de.tum.cit.aet.artemis.localvc.service.LocalVCPersonalAccessTokenManagementService.TOKEN_PREFIX;

import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;

import org.apache.sshd.server.session.ServerSession;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
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
 * <p>
 * The preliminary entry fixes the kind of operation: a fetch is written as a pull and a push as a push. The later steps refine the entry they belong to, and only within its
 * kind: a pull may become a clone, a push gains the hash of the pushed commit, and no step turns one kind into the other. That is what keeps the log trustworthy when the steps of
 * an operation arrive out of order or overlap with other operations. The steps are stored before the request is answered, so the next request of the same operation finds the
 * entry, whichever node answers it.
 */
@Lazy
@Service
@Profile(PROFILE_LOCALVC)
public class LocalVCAccessLogRecorderService {

    private static final Logger log = LoggerFactory.getLogger(LocalVCAccessLogRecorderService.class);

    /** The request attribute that tells the data transfer request of an HTTPS git operation whose entry to amend. */
    private static final String ACCESS_LOG_SUBJECT_ATTRIBUTE = LocalVCAccessLogRecorderService.class.getName() + ".accessLogSubject";

    /**
     * Whose entry a request amends: the user and the participation of the repository it addresses.
     *
     * @param userId          the id of the authenticated user
     * @param participationId the id of the participation that owns the repository
     */
    private record AccessLogSubject(long userId, long participationId) {
    }

    // Optional until LocalVC is the only supported version control, like the same field of LocalVCServletService
    private final Optional<VcsAccessLogService> vcsAccessLogService;

    private final LocalVCRepositoryResolverService repositoryResolver;

    private final UserRepository userRepository;

    private final UserVcsAccessTokenService userVcsAccessTokenService;

    private final RepositoryVCSAccessTokenRepository repositoryVCSAccessTokenRepository;

    private final ProgrammingExerciseParticipationService programmingExerciseParticipationService;

    /**
     * Creates the recorder.
     *
     * @param vcsAccessLogService                     writes the entries, absent on an installation without LocalVC
     * @param repositoryResolver                      reads the latest commit of a repository and parses repository URIs
     * @param userRepository                          finds the user of a rejected request
     * @param userVcsAccessTokenService               tells whether a credential is the personal access token of a user
     * @param repositoryVCSAccessTokenRepository      tells whether a credential is the token of a user for one repository
     * @param programmingExerciseParticipationService finds the participation an entry is written for
     */
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
     * Remembers on an authorized HTTPS request whose access log entry it belongs to, so that the data transfer request of a git operation can amend the entry that the
     * handshake request wrote.
     *
     * @param request               the authorized request
     * @param user                  the authenticated user
     * @param optionalParticipation the participation the request was authorized for, empty for a repository that has none
     */
    public void rememberAccessLogSubject(HttpServletRequest request, User user, Optional<ProgrammingExerciseParticipation> optionalParticipation) {
        optionalParticipation.ifPresent(participation -> request.setAttribute(ACCESS_LOG_SUBJECT_ATTRIBUTE, new AccessLogSubject(user.getId(), participation.getId())));
    }

    /**
     * Writes the preliminary access log entry of an HTTPS git operation, when the request that starts it is authorized.
     * <p>
     * The entry is stored before this method returns. The data transfer request that follows amends it, and it must find it there whichever node it reaches and however busy the
     * node that wrote it is. A failure to write it is logged and does not fail the git operation.
     *
     * @param request                 The object containing all information about the incoming request.
     * @param localVCRepositoryUri    The uri of the requested repository
     * @param user                    The user
     * @param repositoryAction        Indicates whether the operation is a fetch (READ), which is written as a pull, or a push (WRITE).
     * @param optionalParticipation   The participation for which the access log should be stored. If an empty Optional is provided, the method does nothing
     * @param authenticationMechanism The credential the request authenticated with, as reported by the authentication itself
     */
    public void savePreliminaryVcsAccessLogForHTTPs(HttpServletRequest request, LocalVCRepositoryUri localVCRepositoryUri, User user, RepositoryActionType repositoryAction,
            Optional<ProgrammingExerciseParticipation> optionalParticipation, AuthenticationMechanism authenticationMechanism) {
        if (optionalParticipation.isEmpty() || vcsAccessLogService.isEmpty()) {
            return;
        }
        try {
            RepositoryActionType finalRepositoryAction = repositoryAction == RepositoryActionType.WRITE ? RepositoryActionType.PUSH : RepositoryActionType.PULL;
            vcsAccessLogService.get().createAccessLog(user, optionalParticipation.get(), finalRepositoryAction, authenticationMechanism,
                    repositoryResolver.getCommitHash(localVCRepositoryUri), request.getRemoteAddr());
        }
        catch (RuntimeException e) {
            // The git operation is authorized and has to go ahead. The entry is lost, and the amendments that follow find no entry of this operation.
            log.warn("Could not write the VCS access log of a {} of {} by {}", repositoryAction == RepositoryActionType.WRITE ? "push" : "fetch", localVCRepositoryUri,
                    user.getLogin(), e);
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
            case AuthenticationContext.Session _ -> {
                return AuthenticationMechanism.SSH;
            }
            case AuthenticationContext.Request(HttpServletRequest httpRequest) -> {
                try {
                    return resolveHTTPSAuthenticationMechanism(httpRequest.getHeader(HttpHeaders.AUTHORIZATION), user, localVCRepositoryUri);
                }
                catch (LocalVCAuthException _) {
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
     * <p>
     * The state of the command that ran before is dropped first. A connection can carry several commands one after another, and a command whose repository has no participation
     * (an auxiliary repository, say) would otherwise be handled with the participation and the access log of the previous one.
     * <p>
     * The access log entry is stored right away, as a pull or as a push, and kept in the session so that the fetch can turn it into a clone and the push can set its commit hash.
     * Storing it only when the command succeeded would leave no trace of a fetch that has nothing to transfer or of a push that was rejected or went to another branch.
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
        serverSession.removeAttribute(SshConstants.VCS_ACCESS_LOG_KEY);
        serverSession.removeAttribute(SshConstants.PARTICIPATION_KEY);
        if (optionalParticipation.isEmpty()) {
            return;
        }
        ProgrammingExerciseParticipation participation = optionalParticipation.get();
        serverSession.setAttribute(SshConstants.PARTICIPATION_KEY, participation);
        if (vcsAccessLogService.isEmpty()) {
            return;
        }
        try {
            var finalRepositoryActionType = repositoryActionType == RepositoryActionType.READ ? RepositoryActionType.PULL : RepositoryActionType.PUSH;
            VcsAccessLog accessLog = vcsAccessLogService.get().createAccessLog(user, participation, finalRepositoryActionType, authenticationMechanism,
                    repositoryResolver.getCommitHash(localVCRepositoryUri), ipAddress);
            serverSession.setAttribute(SshConstants.VCS_ACCESS_LOG_KEY, accessLog);
        }
        catch (RuntimeException e) {
            // The command is authorized and has to go ahead, without an entry to amend.
            log.warn("Could not write the VCS access log for repository {}", localVCRepositoryUri.getRelativeRepositoryPath(), e);
        }
    }

    /**
     * Turns the access log entry of a fetch over HTTPS into a clone, if the client offered no objects.
     * <p>
     * The handshake request of the fetch wrote the entry as a pull, and a client that offers nothing has nothing yet, so it is cloning. A client that offers objects is pulling,
     * which is what the entry already says. The entry is the one of the user and participation that the request was authorized for, and only an entry of a fetch is changed.
     *
     * @param request       the request from the user
     * @param clientOffered the number of objects offered by the client in the operation, used to determine
     *                          if the action is a clone (if 0) or a pull (if greater than 0).
     */
    public void markFetchAsCloneHTTPS(HttpServletRequest request, int clientOffered) {
        if (clientOffered != 0 || !request.getMethod().equals("POST")) {
            return;
        }
        // A build agent clone has its own audit entry already, so it must not turn the entry of a student into a clone. It is
        // recognised by the attribute the authorization set, which both build agent mechanisms (the build job token and the
        // shared credential) set, and not by a user name that an ordinary user could carry as well.
        if (request.getAttribute(BUILD_AGENT_CLONE_REQUEST_ATTRIBUTE) != null) {
            return;
        }
        if (!(request.getAttribute(ACCESS_LOG_SUBJECT_ATTRIBUTE) instanceof AccessLogSubject subject)) {
            return;
        }
        try {
            vcsAccessLogService.ifPresent(service -> service.markNewestPullAsClone(subject.userId(), subject.participationId()));
        }
        catch (RuntimeException e) {
            log.warn("Could not mark the VCS access log entry of a fetch as a clone", e);
        }
    }

    /**
     * Turns the access log entry of a fetch over SSH into a clone, if the client offered no objects.
     *
     * @param session       the {@link ServerSession} representing the SSH session.
     * @param clientOffered the number of objects offered by the client in the operation, used to determine
     *                          if the action is a clone (if 0) or a pull (if greater than 0).
     */
    public void markFetchAsCloneSSH(ServerSession session, int clientOffered) {
        if (clientOffered != 0) {
            return;
        }
        try {
            // Both attributes are absent on a session that never got that far, which is a normal case rather than a failure.
            var user = session.getAttribute(SshConstants.USER_KEY);
            // A build agent session carries no user and gets an audit entry of its own, so a missing user is all there is to exclude.
            if (user == null) {
                return;
            }
            var accessLog = session.getAttribute(SshConstants.VCS_ACCESS_LOG_KEY);
            // Only the entry of a fetch can become a clone. Were a push entry ever to reach this point, it must stay what it is.
            if (accessLog == null || accessLog.getRepositoryActionType() != RepositoryActionType.PULL) {
                return;
            }
            accessLog.setRepositoryActionType(RepositoryActionType.CLONE);
            vcsAccessLogService.ifPresent(service -> service.updateAccessLog(accessLog));
        }
        catch (RuntimeException e) {
            log.warn("Could not mark the VCS access log entry of a fetch as a clone", e);
        }
    }

    /**
     * Sets the commit hash on the access log entry of a push over HTTPS that has just been received.
     *
     * @param request    the request of the push, which was authorized before
     * @param commitHash the hash of the pushed commit
     */
    public void completePushAccessLogHTTPS(HttpServletRequest request, String commitHash) {
        if (!(request.getAttribute(ACCESS_LOG_SUBJECT_ATTRIBUTE) instanceof AccessLogSubject subject)) {
            return;
        }
        try {
            vcsAccessLogService.ifPresent(service -> service.setCommitHashOfNewestPush(subject.userId(), subject.participationId(), commitHash));
        }
        catch (RuntimeException e) {
            log.warn("Could not set the commit hash {} on the VCS access log entry of a push", commitHash, e);
        }
    }

    /**
     * Sets the commit hash on the access log entry of a push over SSH that has just been received.
     *
     * @param accessLog  the entry that was written when the push command started, null if none could be written
     * @param commitHash the hash of the pushed commit
     */
    public void completePushAccessLogSSH(@Nullable VcsAccessLog accessLog, String commitHash) {
        // Only the entry of a push gets a pushed commit, whatever the session carried.
        if (accessLog == null || accessLog.getRepositoryActionType() != RepositoryActionType.PUSH) {
            return;
        }
        try {
            accessLog.setCommitHash(commitHash);
            vcsAccessLogService.ifPresent(service -> service.updateAccessLog(accessLog));
        }
        catch (RuntimeException e) {
            log.warn("Could not set the commit hash {} on the VCS access log entry of a push", commitHash, e);
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
        catch (LocalVCAuthException | EntityNotFoundException _) {
            // Caught when: 1) no user, or 2) no exercise or participation was found. In none of these cases does it make sense to write a log
        }
        catch (RuntimeException e) {
            // This runs while a failed authentication is being answered, so nothing that happens here may replace the
            // 401 the caller is about to send. Writing the access log is best effort by nature: it describes an attempt
            // that was already rejected.
            log.warn("Could not write the VCS access log for a failed authentication on {}", servletRequest.getRequestURI(), e);
        }
    }
}
