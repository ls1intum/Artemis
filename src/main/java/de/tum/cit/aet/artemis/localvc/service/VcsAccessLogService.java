package de.tum.cit.aet.artemis.localvc.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALVC;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participation;
import de.tum.cit.aet.artemis.exercise.repository.ParticipationRepository;
import de.tum.cit.aet.artemis.programming.domain.AuthenticationMechanism;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.programming.domain.VcsAccessLog;
import de.tum.cit.aet.artemis.programming.repository.VcsAccessLogRepository;
import de.tum.cit.aet.artemis.programming.web.repository.RepositoryActionType;

@Profile(PROFILE_LOCALVC)
@Lazy
@Service
public class VcsAccessLogService {

    private static final Logger log = LoggerFactory.getLogger(VcsAccessLogService.class);

    /** Matches the width of the {@code vcs_access_log.name} column. */
    private static final int ACCESSOR_NAME_MAX_LENGTH = 100;

    /**
     * How long after its entry was written an HTTPS git operation may still amend it. The data transfer request follows the handshake request immediately, and the longest wait
     * is for a push to arrive over a slow connection, so an hour is generous. An entry older than that belongs to an earlier operation.
     */
    private static final Duration AMENDMENT_WINDOW = Duration.ofHours(1);

    /** How many entries one clone tries to claim before it gives up, which is more than the clones of one user that can overlap on one repository. */
    private static final int MAX_CLAIM_ATTEMPTS = 10;

    private static final List<RepositoryActionType> PULL_ACTION_TYPES = List.of(RepositoryActionType.PULL);

    private static final List<RepositoryActionType> PUSH_ACTION_TYPES = List.of(RepositoryActionType.PUSH);

    private final VcsAccessLogRepository vcsAccessLogRepository;

    private final ParticipationRepository participationRepository;

    VcsAccessLogService(VcsAccessLogRepository vcsAccessLogRepository, ParticipationRepository participationRepository) {
        this.vcsAccessLogRepository = vcsAccessLogRepository;
        this.participationRepository = participationRepository;
    }

    /**
     * Creates a vcs access log entry in the background and stores it to the database.
     * <p>
     * For an entry that nothing amends afterwards, such as the record of a rejected request. An entry that a later step of the operation has to find is written with
     * {@link #createAccessLog}.
     *
     * @param user                    The user accessing the repository
     * @param participation           The participation which owns the repository
     * @param actionType              The action type, for example a failed clone or push
     * @param authenticationMechanism The used authentication mechanism: password, vcs token (user/participation), SSH or code editor
     * @param commitHash              The latest commit hash
     * @param ipAddress               The ip address of the user accessing the repository
     */
    @Async("vcsAccessLogExecutor")
    public void saveAccessLog(User user, ProgrammingExerciseParticipation participation, RepositoryActionType actionType, AuthenticationMechanism authenticationMechanism,
            String commitHash, String ipAddress) {
        createAccessLog(user, participation, actionType, authenticationMechanism, commitHash, ipAddress);
    }

    /**
     * Creates a vcs access log entry for a build agent cloning a repository for one of its build jobs.
     * <p>
     * Build agent clones used to leave no trace at all: the old shared-credential shortcut returned before this log was
     * reached, so the most privileged reader in the installation was the only one nobody could audit. There is no user
     * to attribute the access to, because the agent authenticates with the build job's token rather than as a person,
     * so the agent and job identify the access instead.
     *
     * @param participation  The participation which owns the repository
     * @param buildAgentName The short name of the build agent that cloned
     * @param buildJobId     The id of the build job the clone belongs to
     * @param commitHash     The latest commit hash
     * @param ipAddress      The address the build agent connected from
     * @param mechanism      How the agent authenticated: {@link AuthenticationMechanism#BUILD_JOB_TOKEN} over https,
     *                           {@link AuthenticationMechanism#SSH} with its key. Both are audited, so which one an
     *                           installation uses stays visible in the log rather than being inferred from its
     *                           configuration.
     */
    @Async("vcsAccessLogExecutor")
    public void saveBuildAgentAccessLog(ProgrammingExerciseParticipation participation, String buildAgentName, String buildJobId, String commitHash, String ipAddress,
            AuthenticationMechanism mechanism) {
        log.debug("Storing access operation for build agent {} running build job {}", buildAgentName, buildJobId);

        // The name column is NOT NULL and is what identifies the accessor in the audit UI, so it carries both parts.
        // The email column is likewise NOT NULL and has nothing meaningful to hold for an agent.
        String accessor = "Build agent " + buildAgentName + " (build job " + buildJobId + ")";
        if (accessor.length() > ACCESSOR_NAME_MAX_LENGTH) {
            // The column is varchar(100); a long agent short name must not turn an audit entry into a failed insert
            accessor = accessor.substring(0, ACCESSOR_NAME_MAX_LENGTH);
        }
        VcsAccessLog accessLogEntry = new VcsAccessLog(null, (Participation) participation, accessor, "", RepositoryActionType.PULL, mechanism, commitHash, ipAddress);
        vcsAccessLogRepository.save(accessLogEntry);
    }

    /**
     * Creates a vcs access log entry and stores it before returning, so the entry exists when the next step of the git operation looks for it.
     * <p>
     * An HTTPS git operation is several requests that may be answered by different nodes, so an entry that is written in the background gives no guarantee that it exists when the
     * next request wants to amend it. The caller must not let a failure here fail the git operation.
     *
     * @param user                    The user accessing the repository
     * @param participation           The participation which owns the repository
     * @param actionType              The kind of operation, which is never changed afterwards: {@code PULL} for a fetch, {@code PUSH} for a push
     * @param authenticationMechanism The used authentication mechanism: password, vcs token (user/participation), SSH or code editor
     * @param commitHash              The latest commit hash
     * @param ipAddress               The ip address of the user accessing the repository
     * @return the stored entry, with its id
     */
    public VcsAccessLog createAccessLog(User user, ProgrammingExerciseParticipation participation, RepositoryActionType actionType, AuthenticationMechanism authenticationMechanism,
            String commitHash, String ipAddress) {
        log.debug("Storing access operation for user {}", user);

        return vcsAccessLogRepository
                .save(new VcsAccessLog(user, (Participation) participation, user.getName(), user.getEmail(), actionType, authenticationMechanism, commitHash, ipAddress));
    }

    /**
     * Stores the changes to an entry that {@link #createAccessLog} returned, before returning.
     *
     * @param vcsAccessLog the entry to store
     */
    public void updateAccessLog(VcsAccessLog vcsAccessLog) {
        vcsAccessLogRepository.save(vcsAccessLog);
    }

    /**
     * Turns the entry of the fetch that is running into a clone.
     * <p>
     * The handshake request of a fetch writes its entry as a pull, because it cannot tell yet whether the client already has the repository. The data transfer request that
     * follows can: a client that offers no objects is cloning. That request finds the entry by user and participation, and only among HTTPS entries that are still a pull, so it
     * cannot reach the entry of a push or an overlapping SSH operation. Looking only at entries that are still a pull also lets overlapping clones each find an entry of their
     * own: the entry that one clone has turned into a clone is not offered to the next.
     *
     * @param userId          the id of the user that is fetching
     * @param participationId the id of the participation that owns the repository
     */
    public void markNewestPullAsClone(long userId, long participationId) {
        // Clones that run at the same moment can find the same entry. The entry is claimed with a conditional update, and a clone that loses the claim looks for the next one.
        for (int attempt = 0; attempt < MAX_CLAIM_ATTEMPTS; attempt++) {
            Optional<VcsAccessLog> pull = vcsAccessLogRepository.findNewestEntryOfUserForParticipation(userId, participationId, PULL_ACTION_TYPES,
                    ZonedDateTime.now().minus(AMENDMENT_WINDOW));
            if (pull.isEmpty() || vcsAccessLogRepository.changeActionTypeIfStill(pull.get().getId(), RepositoryActionType.PULL, RepositoryActionType.CLONE) == 1) {
                return;
            }
        }
    }

    /**
     * Sets the commit hash of the push that has just been received.
     * <p>
     * The handshake request of a push writes its entry with the hash the repository had before the push, because the pushed commit is not known yet. Once it is, the entry is
     * found by user and participation, and only among HTTPS entries of a push, so a fetch or an SSH push that was written in between cannot take the hash.
     *
     * @param userId          the id of the user that pushed
     * @param participationId the id of the participation that owns the repository
     * @param commitHash      the hash of the pushed commit
     */
    public void setCommitHashOfNewestPush(long userId, long participationId, String commitHash) {
        vcsAccessLogRepository.findNewestEntryOfUserForParticipation(userId, participationId, PUSH_ACTION_TYPES, ZonedDateTime.now().minus(AMENDMENT_WINDOW))
                .ifPresent(accessLog -> {
                    accessLog.setCommitHash(commitHash);
                    vcsAccessLogRepository.save(accessLog);
                });
    }

    /**
     * Saves an vcsAccessLog
     *
     * @param vcsAccessLog The vcsAccessLog to save
     */
    @Async("vcsAccessLogExecutor")
    public void saveVcsAccesslog(VcsAccessLog vcsAccessLog) {
        vcsAccessLogRepository.save(vcsAccessLog);
    }

    /**
     * Creates a preliminary access log for a push from the code editor, and returns it
     *
     * @param repo            The repository to which the push is executed
     * @param user            The user submitting the change
     * @param participationId The id of the participation belonging to the repository
     * @return an Optional containing the preliminary VcsAccessLog, if one was created
     * @throws GitAPIException if an error occurs while retrieving the git log
     */
    public Optional<VcsAccessLog> createPreliminaryCodeEditorAccessLog(Repository repo, User user, Long participationId) throws GitAPIException {
        try (Git git = new Git(repo)) {
            String lastCommitHash = git.log().setMaxCount(1).call().iterator().next().getName();
            var participation = participationRepository.findById(participationId);
            if (participation.isPresent() && participation.get() instanceof ProgrammingExerciseParticipation programmingParticipation) {
                log.debug("Storing access operation for user {}", user);

                return Optional.of(new VcsAccessLog(user, (Participation) programmingParticipation, user.getName(), user.getEmail(), RepositoryActionType.WRITE,
                        AuthenticationMechanism.CODE_EDITOR, lastCommitHash, null));
            }
        }
        return Optional.empty();
    }

}
