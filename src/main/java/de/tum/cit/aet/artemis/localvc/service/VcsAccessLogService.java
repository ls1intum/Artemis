package de.tum.cit.aet.artemis.localvc.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALVC;

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
     * Creates a vcs access log entry and stores it to the database
     *
     * @param user                    The user accessing the repository
     * @param participation           The participation which owns the repository
     * @param actionType              The action type: READ or WRITE
     * @param authenticationMechanism The used authentication mechanism: password, vcs token (user/participation), SSH or code editor
     * @param commitHash              The latest commit hash
     * @param ipAddress               The ip address of the user accessing the repository
     */
    @Async("vcsAccessLogExecutor")
    public void saveAccessLog(User user, ProgrammingExerciseParticipation participation, RepositoryActionType actionType, AuthenticationMechanism authenticationMechanism,
            String commitHash, String ipAddress) {
        log.debug("Storing access operation for user {}", user);

        VcsAccessLog accessLogEntry = new VcsAccessLog(user, (Participation) participation, user.getName(), user.getEmail(), actionType, authenticationMechanism, commitHash,
                ipAddress);
        vcsAccessLogRepository.save(accessLogEntry);
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
     * Updates the commit hash of the newest log entry
     *
     * @param participation The participation to which the repository belongs to
     * @param commitHash    The newest commit hash which should get set for the access log entry
     */
    @Async("vcsAccessLogExecutor")
    public void updateCommitHash(ProgrammingExerciseParticipation participation, String commitHash) {
        var vcsAccessLog = vcsAccessLogRepository.findNewestUserEntryByParticipationId(participation.getId());
        if (vcsAccessLog.isPresent()) {
            vcsAccessLog.get().setCommitHash(commitHash);
            vcsAccessLogRepository.save(vcsAccessLog.get());
        }
    }

    /**
     * Updates the commit hash of the newest log entry
     *
     * @param localVCRepositoryUri The localVCRepositoryUri of the participation to which vcsAccessLog belongs to
     * @param repositoryActionType The repository action type to which the vcsAccessLog should get updated to
     */
    @Async("vcsAccessLogExecutor")
    public void updateRepositoryActionType(LocalVCRepositoryUri localVCRepositoryUri, RepositoryActionType repositoryActionType) {
        var repositoryURL = localVCRepositoryUri.toString().replace("/git-upload-pack", "").replace("/git-receive-pack", "");
        var vcsAccessLog = vcsAccessLogRepository.findNewestUserEntryByRepositoryUri(repositoryURL);
        if (vcsAccessLog.isPresent()) {
            vcsAccessLog.get().setRepositoryActionType(repositoryActionType);
            vcsAccessLogRepository.save(vcsAccessLog.get());
        }
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
