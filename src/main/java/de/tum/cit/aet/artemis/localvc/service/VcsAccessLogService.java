package de.tum.cit.aet.artemis.localvc.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALVC;

import java.time.ZonedDateTime;
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

    private final VcsAccessLogRepository vcsAccessLogRepository;

    private final ParticipationRepository participationRepository;

    VcsAccessLogService(VcsAccessLogRepository vcsAccessLogRepository, ParticipationRepository participationRepository) {
        this.vcsAccessLogRepository = vcsAccessLogRepository;
        this.participationRepository = participationRepository;
    }

    /**
     * Creates a vcs access log entry in the background and stores it to the database.
     * <p>
     * For the record of a rejected attempt, which nothing waits for. The participation is passed by id: the entry only references it, and an entity or a proxy that is
     * handed to another thread cannot be read there, not even to be logged.
     *
     * @param user                    The user accessing the repository
     * @param participationId         The id of the participation which owns the repository
     * @param actionType              The action type, for example a failed clone or push
     * @param authenticationMechanism The used authentication mechanism: password, vcs token (user/participation), SSH or code editor
     * @param commitHash              The latest commit hash
     * @param ipAddress               The ip address of the user accessing the repository
     */
    @Async("vcsAccessLogExecutor")
    public void saveAccessLog(User user, long participationId, RepositoryActionType actionType, AuthenticationMechanism authenticationMechanism, String commitHash,
            String ipAddress) {
        createAccessLog(user, participationId, actionType, authenticationMechanism, commitHash, ipAddress);
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
        String accessor = VcsAccessLog.BUILD_AGENT_ACCESSOR_PREFIX + buildAgentName + " (build job " + buildJobId + ")";
        if (accessor.length() > ACCESSOR_NAME_MAX_LENGTH) {
            // The column is varchar(100); a long agent short name must not turn an audit entry into a failed insert
            accessor = accessor.substring(0, ACCESSOR_NAME_MAX_LENGTH);
        }
        VcsAccessLog accessLogEntry = new VcsAccessLog(null, (Participation) participation, accessor, "", RepositoryActionType.PULL, mechanism, commitHash, ipAddress);
        vcsAccessLogRepository.save(accessLogEntry);
    }

    /**
     * Creates a vcs access log entry and stores it before returning.
     * <p>
     * An entry is written once, when the operation is over and everything about it is known, and is never changed afterwards. A failure to write it must not fail the Git
     * operation, so the callers catch it.
     *
     * @param user                    The user accessing the repository
     * @param participationId         The id of the participation which owns the repository
     * @param actionType              The action: a clone, pull or push, or a failed one
     * @param authenticationMechanism The used authentication mechanism: password, vcs token (user/participation), SSH or code editor
     * @param commitHash              The commit hash that belongs to the action
     * @param ipAddress               The ip address of the user accessing the repository
     * @return the stored entry
     */
    public VcsAccessLog createAccessLog(User user, long participationId, RepositoryActionType actionType, AuthenticationMechanism authenticationMechanism, String commitHash,
            String ipAddress) {
        return createAccessLog(user, participationId, actionType, authenticationMechanism, commitHash, ipAddress, ZonedDateTime.now());
    }

    /**
     * Stores an entry for an access that happened at the given time. The entry of a request that advertises the references is written after its response was sent, so it carries
     * the time of the request: the next request of the same operation can be answered and written before it, and the log joins the two by their times.
     *
     * @param user                    The user accessing the repository
     * @param participationId         The id of the participation which owns the repository
     * @param actionType              The action: a read or a write that moved no data, or a clone, pull or push, or a failed one
     * @param authenticationMechanism The used authentication mechanism: password, vcs token (user/participation), SSH or code editor
     * @param commitHash              The commit hash that belongs to the action
     * @param ipAddress               The ip address of the user accessing the repository
     * @param timestamp               When the access happened
     * @return the stored entry
     */
    public VcsAccessLog createAccessLog(User user, long participationId, RepositoryActionType actionType, AuthenticationMechanism authenticationMechanism, String commitHash,
            String ipAddress, ZonedDateTime timestamp) {
        log.debug("Storing access operation for user {}", user.getLogin());

        // The entry only references the participation, so the reference is enough and nothing is loaded.
        Participation participation = participationRepository.getReferenceById(participationId);
        return vcsAccessLogRepository
                .save(new VcsAccessLog(user, participation, user.getName(), user.getEmail(), actionType, authenticationMechanism, commitHash, ipAddress, timestamp));
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
