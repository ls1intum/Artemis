package de.tum.cit.aet.artemis.localvc.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALVC;
import static de.tum.cit.aet.artemis.localvc.service.LocalVCCredentials.extractUsernameAndPassword;
import static de.tum.cit.aet.artemis.localvc.service.LocalVCCredentials.secretMatches;
import static de.tum.cit.aet.artemis.localvc.service.LocalVCPersonalAccessTokenManagementService.TOKEN_PREFIX;

import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;

import org.apache.sshd.server.session.ServerSession;
import org.eclipse.jgit.http.server.ServletUtils;
import org.eclipse.jgit.lib.Repository;
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
import de.tum.cit.aet.artemis.programming.repository.RepositoryVCSAccessTokenRepository;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseParticipationService;
import de.tum.cit.aet.artemis.programming.web.repository.RepositoryActionType;

/**
 * Writes the VCS access log entries of git operations that are served, over HTTPS and over SSH, and works out how a request authenticated.
 * <p>
 * An operation is written once, as one entry, when it is over and everything about it is known. Git over HTTPS is several requests that nothing links, so no step looks for
 * the entry of another one, and no entry is ever changed. The hooks of the request or command fill a {@link GitOperationAudit} while it runs, and the filter of the request or
 * the SSH command hands it over afterwards:
 * <ul>
 * <li>A fetch that sent data is a clone if the client offered nothing and a pull otherwise.</li>
 * <li>A push that brought commands is a push with the pushed commit if the repository accepted it, and a failed push with the commit the repository had before if it did not.</li>
 * <li>Over SSH a command that moves no data, a fetch that is up to date or a push with nothing new, is an access of its own and is written as a read or a write.</li>
 * <li>Over HTTPS the request that advertises the references is written as a read or a write, because it is all there is of an operation that is up to date. When data follows,
 * the operation is a clone, pull or push, and the log shows that instead: the advertisement and the transfer cannot be linked here, so they are joined when the log is read, by
 * {@code VcsAccessLogOperations}. A request that only negotiates, or the check before a large push, leaves no entry.</li>
 * <li>An attempt that was rejected is a failed clone or push, whether the credentials or the authorization did not hold.</li>
 * </ul>
 */
@Lazy
@Service
@Profile(PROFILE_LOCALVC)
public class LocalVCAccessLogRecorderService {

    private static final Logger log = LoggerFactory.getLogger(LocalVCAccessLogRecorderService.class);

    /** The {@link AccessLogSubject} the authorization stored on an HTTPS request, absent if the repository has no participation. */
    private static final String SUBJECT_ATTRIBUTE = LocalVCAccessLogRecorderService.class.getName() + ".subject";

    /** The {@link GitOperationAudit} of an HTTPS request. */
    private static final String AUDIT_ATTRIBUTE = LocalVCAccessLogRecorderService.class.getName() + ".audit";

    // Optional until LocalVC is the only supported version control, like the same field of LocalVCServletService
    private final Optional<VcsAccessLogService> vcsAccessLogService;

    private final UserRepository userRepository;

    private final UserVcsAccessTokenService userVcsAccessTokenService;

    private final RepositoryVCSAccessTokenRepository repositoryVCSAccessTokenRepository;

    private final ProgrammingExerciseParticipationService programmingExerciseParticipationService;

    /**
     * Creates the recorder.
     *
     * @param vcsAccessLogService                     writes the entries, absent on an installation without LocalVC
     * @param userRepository                          finds the user of a rejected request
     * @param userVcsAccessTokenService               tells whether a credential is the personal access token of a user
     * @param repositoryVCSAccessTokenRepository      tells whether a credential is the token of a user for one repository
     * @param programmingExerciseParticipationService finds the participation a rejected attempt is written for
     */
    public LocalVCAccessLogRecorderService(Optional<VcsAccessLogService> vcsAccessLogService, UserRepository userRepository, UserVcsAccessTokenService userVcsAccessTokenService,
            RepositoryVCSAccessTokenRepository repositoryVCSAccessTokenRepository, ProgrammingExerciseParticipationService programmingExerciseParticipationService) {
        this.vcsAccessLogService = vcsAccessLogService;
        this.userRepository = userRepository;
        this.userVcsAccessTokenService = userVcsAccessTokenService;
        this.repositoryVCSAccessTokenRepository = repositoryVCSAccessTokenRepository;
        this.programmingExerciseParticipationService = programmingExerciseParticipationService;
    }

    /**
     * Remembers who an authorized HTTPS request is for, so that the entry of its operation can be written when the operation is over. Nothing is written yet.
     *
     * @param request                 the authorized request
     * @param user                    the authenticated user
     * @param optionalParticipation   the participation that owns the repository, if any
     * @param authenticationMechanism the credential used by this request
     */
    public void rememberSubjectHTTPS(HttpServletRequest request, User user, Optional<ProgrammingExerciseParticipation> optionalParticipation,
            AuthenticationMechanism authenticationMechanism) {
        request.removeAttribute(SUBJECT_ATTRIBUTE);
        optionalParticipation.ifPresent(
                participation -> request.setAttribute(SUBJECT_ATTRIBUTE, new AccessLogSubject(user, participation.getId(), authenticationMechanism, request.getRemoteAddr())));
    }

    /**
     * Gives the audit of an HTTPS request, which the hooks fill and the filter reads afterwards. It is created on first use.
     *
     * @param request the request
     * @return the audit of this request
     */
    public GitOperationAudit auditOf(HttpServletRequest request) {
        if (request.getAttribute(AUDIT_ATTRIBUTE) instanceof GitOperationAudit audit) {
            return audit;
        }
        var audit = new GitOperationAudit();
        request.setAttribute(AUDIT_ATTRIBUTE, audit);
        return audit;
    }

    /**
     * Writes the entry of the fetch that an HTTPS request was, if it was one. Called when the request is over.
     *
     * @param request the request
     */
    public void recordFetchHTTPS(HttpServletRequest request) {
        var subject = subjectOf(request);
        var audit = auditOf(request);
        if (subject != null && !audit.isPackSent() && audit.isAccessed()) {
            write(subject, RepositoryActionType.READ, audit.getCommitHashWhenAccessed(), audit.getAccessedAt());
            return;
        }
        recordFetch(subject, audit);
    }

    /**
     * Writes the entry of the push that an HTTPS request was, if it was one. Called when the request is over.
     *
     * @param request the request
     */
    public void recordPushHTTPS(HttpServletRequest request) {
        var subject = subjectOf(request);
        var audit = auditOf(request);
        if (subject != null && !audit.isPushReceived() && audit.isAccessed()) {
            write(subject, RepositoryActionType.WRITE, audit.getCommitHashWhenAccessed(), audit.getAccessedAt());
            return;
        }
        recordPush(subject, audit);
    }

    /**
     * Gives the newest commit of the repository that an HTTPS request is for.
     *
     * @param request a request that the git servlet has resolved a repository for
     * @return the hash of the newest commit, null if there is none or the repository is not known
     */
    static @Nullable String headCommitOf(HttpServletRequest request) {
        try {
            return LocalVCFetchPreUploadHook.headCommitOf(ServletUtils.getRepository(request));
        }
        catch (RuntimeException e) {
            log.debug("Could not read the newest commit of the repository of {}", request.getRequestURI(), e);
            return null;
        }
    }

    private static @Nullable AccessLogSubject subjectOf(HttpServletRequest request) {
        return request.getAttribute(SUBJECT_ATTRIBUTE) instanceof AccessLogSubject subject ? subject : null;
    }

    /**
     * Writes the entry of a fetch that sent data: a clone if the client offered nothing, otherwise a pull. A fetch that sent nothing is not an operation.
     *
     * @param subject who fetched from which repository, null if that is unknown, for example for a build agent, which has entries of its own
     * @param audit   what the hooks learned about the fetch
     */
    public void recordFetch(@Nullable AccessLogSubject subject, GitOperationAudit audit) {
        if (subject == null || !audit.isPackSent()) {
            return;
        }
        var action = audit.getObjectsOffered() == 0 ? RepositoryActionType.CLONE : RepositoryActionType.PULL;
        write(subject, action, audit.getCommitHashWhenSent());
    }

    /**
     * Writes the entry of a push that brought commands: a push with the pushed commit if the repository accepted it, a failed push with the commit from before otherwise.
     * A push without commands is not an operation.
     *
     * @param subject who pushed to which repository, null if that is unknown
     * @param audit   what the hooks learned about the push
     */
    public void recordPush(@Nullable AccessLogSubject subject, GitOperationAudit audit) {
        if (subject == null || !audit.isPushReceived()) {
            return;
        }
        var pushedCommitHash = audit.getPushedCommitHash();
        if (pushedCommitHash != null) {
            write(subject, RepositoryActionType.PUSH, pushedCommitHash);
        }
        else {
            write(subject, RepositoryActionType.PUSH_FAIL, audit.getCommitHashBeforePush());
        }
    }

    /**
     * Writes the entry of an SSH command that fetched: a clone or pull if data was sent, otherwise a read. Over SSH one command is one operation, so a fetch that moves no
     * data, because the client is up to date, or that only lists the references, is still an access and is written.
     *
     * @param subject    who fetched from which repository, null if that is unknown
     * @param audit      what the hooks learned about the fetch
     * @param repository the repository of the command, which says what the newest commit was
     */
    public void recordFetchSSH(@Nullable AccessLogSubject subject, GitOperationAudit audit, Repository repository) {
        if (subject != null && !audit.isPackSent()) {
            write(subject, RepositoryActionType.READ, LocalVCFetchPreUploadHook.headCommitOf(repository));
            return;
        }
        recordFetch(subject, audit);
    }

    /**
     * Writes the entry of an SSH command that pushed: a push or failed push if it brought commands, otherwise a write. A push that has nothing new is still an access.
     *
     * @param subject    who pushed to which repository, null if that is unknown
     * @param audit      what the hooks learned about the push
     * @param repository the repository of the command, which says what the newest commit is
     */
    public void recordPushSSH(@Nullable AccessLogSubject subject, GitOperationAudit audit, Repository repository) {
        if (subject != null && !audit.isPushReceived()) {
            write(subject, RepositoryActionType.WRITE, LocalVCFetchPreUploadHook.headCommitOf(repository));
            return;
        }
        recordPush(subject, audit);
    }

    private void write(AccessLogSubject subject, RepositoryActionType action, @Nullable String commitHash) {
        write(subject, action, commitHash, null);
    }

    /**
     * Writes an entry. The entry of an advertisement carries the time of its request: it is written after the response was sent, and the next request of the same operation can be
     * written before it, which the log reads by the times of the entries.
     */
    private void write(AccessLogSubject subject, RepositoryActionType action, @Nullable String commitHash, @Nullable ZonedDateTime accessedAt) {
        if (vcsAccessLogService.isEmpty()) {
            return;
        }
        // An SSH session is closed as soon as the client has its answer, which is before the entry of the command is written, and closing it interrupts the thread of the command.
        // An interrupted thread cannot wait for a database connection, so the entry is written as if there was no interrupt, and the interrupt is passed on afterwards. If it
        // arrives while the entry is written, the entry is written once more.
        boolean interrupted = Thread.interrupted();
        try {
            for (int attempt = 1; attempt <= 2; attempt++) {
                try {
                    store(subject, action, commitHash, accessedAt);
                    return;
                }
                catch (RuntimeException e) {
                    boolean cancelled = isCausedByInterruption(e);
                    interrupted |= cancelled;
                    if (cancelled && attempt == 1) {
                        Thread.interrupted();
                        continue;
                    }
                    // The operation has happened and its response must not change for it, so a failure to describe it is only reported.
                    log.warn("Could not write the VCS access log entry ({}) of user {} to participation {}", action, subject.user().getLogin(), subject.participationId(), e);
                    return;
                }
            }
        }
        finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void store(AccessLogSubject subject, RepositoryActionType action, @Nullable String commitHash, @Nullable ZonedDateTime accessedAt) {
        if (accessedAt == null) {
            vcsAccessLogService.get().createAccessLog(subject.user(), subject.participationId(), action, subject.mechanism(), Objects.requireNonNullElse(commitHash, ""),
                    subject.ipAddress());
        }
        else {
            vcsAccessLogService.get().createAccessLog(subject.user(), subject.participationId(), action, subject.mechanism(), Objects.requireNonNullElse(commitHash, ""),
                    subject.ipAddress(), accessedAt);
        }
    }

    private static boolean isCausedByInterruption(Throwable failure) {
        int depth = 0;
        for (Throwable cause = failure; cause != null && depth < 10; cause = cause.getCause(), depth++) {
            if (cause instanceof InterruptedException) {
                return true;
            }
        }
        return false;
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
     * Remembers who an SSH command is for, so that the entry of its operation can be written when the operation is over. Nothing is written yet.
     * <p>
     * The state of the command that ran before is dropped first. A connection can carry several commands one after another, and a command whose repository has no participation
     * (an auxiliary repository, say) would otherwise be handled with the participation of the previous one. The command itself reads the subject from the session when it
     * starts, before the next command can replace it.
     *
     * @param user                    the user accessing the repository
     * @param optionalParticipation   the participation associated with the repository
     * @param authenticationMechanism the mechanism used for authentication
     * @param ipAddress               the IP address of the user accessing the repository
     * @param serverSession           the SSH serverSession, where the data gets stored
     */
    public void cacheAttributesInSshSession(User user, Optional<ProgrammingExerciseParticipation> optionalParticipation, AuthenticationMechanism authenticationMechanism,
            String ipAddress, ServerSession serverSession) {
        serverSession.removeAttribute(SshConstants.ACCESS_LOG_SUBJECT_KEY);
        serverSession.removeAttribute(SshConstants.PARTICIPATION_KEY);
        if (optionalParticipation.isEmpty()) {
            return;
        }
        ProgrammingExerciseParticipation participation = optionalParticipation.get();
        serverSession.setAttribute(SshConstants.PARTICIPATION_KEY, participation);
        serverSession.setAttribute(SshConstants.ACCESS_LOG_SUBJECT_KEY, new AccessLogSubject(user, participation.getId(), authenticationMechanism, ipAddress));
    }

    /**
     * Adds a failed HTTPS attempt, whose credentials did not hold, to the log as a failed clone or push.
     * <p>
     * The attempt can only be attributed if the credentials name an existing user and the repository belongs to a participation. The first request of every operation carries no
     * credentials at all, because git waits to be asked, so that case is not an attempt and is not passed here. The write never fails the request that is being rejected.
     * <p>
     * The entry carries no commit, because the repository was not reached. That is what tells a failed push written here from one that the repository refused after its references
     * were advertised: {@code VcsAccessLogOperations} joins only the latter to the advertisement.
     *
     * @param request              the request, which carries the credentials
     * @param localVCRepositoryUri the repository the request was for
     * @param repositoryAction     WRITE for a push, READ for a fetch
     */
    public void recordFailedAuthenticationHTTPS(HttpServletRequest request, LocalVCRepositoryUri localVCRepositoryUri, RepositoryActionType repositoryAction) {
        try {
            String authorizationHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
            UsernameAndPassword usernameAndPassword = extractUsernameAndPassword(authorizationHeader);
            User user = userRepository.findOneByLogin(usernameAndPassword.username()).orElseThrow(LocalVCAuthException::new);
            AuthenticationMechanism mechanism = resolveHTTPSAuthenticationMechanism(authorizationHeader, user, localVCRepositoryUri);
            // One id, no entity: the entry only references the participation.
            var participation = programmingExerciseParticipationService.getParticipationReferenceForRepository(localVCRepositoryUri.getRepositoryTypeOrUserName(),
                    localVCRepositoryUri.toString(), localVCRepositoryUri.getProjectKey());
            if (participation.isEmpty()) {
                return;
            }
            long participationId = participation.get().getId();
            var action = repositoryAction == RepositoryActionType.WRITE ? RepositoryActionType.PUSH_FAIL : RepositoryActionType.CLONE_FAIL;
            vcsAccessLogService.ifPresent(service -> service.saveAccessLog(user, participationId, action, mechanism, "", request.getRemoteAddr()));
        }
        catch (LocalVCAuthException | EntityNotFoundException _) {
            // Caught when: 1) no user, or 2) no exercise or participation was found. In none of these cases is there anybody to attribute an entry to
        }
        catch (RuntimeException e) {
            // This runs while a failed authentication is being answered, so nothing that happens here may replace the response the caller is about to send.
            log.warn("Could not write the VCS access log for a failed authentication on {}", request.getRequestURI(), e);
        }
    }
}
