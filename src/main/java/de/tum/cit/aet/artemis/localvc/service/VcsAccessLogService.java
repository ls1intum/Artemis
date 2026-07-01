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
import de.tum.cit.aet.artemis.programming.domain.ExperimentalGroup;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.programming.domain.VcsAccessLog;
import de.tum.cit.aet.artemis.programming.domain.VcsAnalyticsLog;
import de.tum.cit.aet.artemis.programming.repository.VcsAccessLogRepository;
import de.tum.cit.aet.artemis.programming.repository.VcsAnalyticsLogRepository;
import de.tum.cit.aet.artemis.programming.service.AnalyticsHashUtils;
import de.tum.cit.aet.artemis.programming.web.repository.RepositoryActionType;

@Profile(PROFILE_LOCALVC)
@Lazy
@Service
public class VcsAccessLogService {

    private static final Logger log = LoggerFactory.getLogger(VcsAccessLogService.class);

    private final VcsAccessLogRepository vcsAccessLogRepository;

    private final VcsAnalyticsLogRepository vcsAnalyticsLogRepository;

    private final ParticipationRepository participationRepository;

    VcsAccessLogService(VcsAccessLogRepository vcsAccessLogRepository, VcsAnalyticsLogRepository vcsAnalyticsLogRepository, ParticipationRepository participationRepository) {
        this.vcsAccessLogRepository = vcsAccessLogRepository;
        this.vcsAnalyticsLogRepository = vcsAnalyticsLogRepository;
        this.participationRepository = participationRepository;
    }

    /**
     * Creates a vcs access log entry and stores it to the database
     * Also uses helper function to create and save vcs analytics log entry to the database
     *
     * @param user                    The user accessing the repository
     * @param participation           The participation which owns the repository
     * @param actionType              The action type: READ or WRITE
     * @param authenticationMechanism The used authentication mechanism: password, vcs token (user/participation), SSH or code editor
     * @param commitHash              The latest commit hash
     * @param ipAddress               The ip address of the user accessing the repository
     */
    @Async
    public void saveAccessLog(User user, ProgrammingExerciseParticipation participation, RepositoryActionType actionType, AuthenticationMechanism authenticationMechanism,
            String commitHash, String ipAddress) {
        log.debug("Storing access operation for user {}", user);

        VcsAccessLog accessLogEntry = new VcsAccessLog(user, (Participation) participation, user.getName(), user.getEmail(), actionType, authenticationMechanism, commitHash,
                ipAddress);
        vcsAccessLogRepository.save(accessLogEntry);
        saveAnalyticsLog(user, participation.getId(), participation.getExercise().getId(), actionType, authenticationMechanism);
    }

    /**
     * Helper function to save the analytics entry to vcsAnalyticsRepository
     *
     */
    private void saveAnalyticsLog(User user, Long participationId, Long exerciseId, RepositoryActionType actionType, AuthenticationMechanism authenticationMechanism) {
        Optional<Long> optionalCourseId = vcsAnalyticsLogRepository.findCourseIdByParticipationId(participationId);
        if (optionalCourseId.isPresent()) {
            Long courseId = optionalCourseId.get();
            ExperimentalGroup experimentalGroup = AnalyticsHashUtils.getGroup(user.getId());
            String maskedId = AnalyticsHashUtils.maskUserId(user.getId(), courseId);
            VcsAnalyticsLog analyticsLogEntry = new VcsAnalyticsLog(maskedId, courseId, exerciseId, experimentalGroup, actionType, authenticationMechanism);
            vcsAnalyticsLogRepository.save(analyticsLogEntry);
        }
        else {
            log.warn("Could not save analytics log: courseId is null for participation {}", participationId);
        }
    }

    /**
     * Updates the commit hash of the newest log entry
     *
     * @param participation The participation to which the repository belongs to
     * @param commitHash    The newest commit hash which should get set for the access log entry
     */
    @Async
    public void updateCommitHash(ProgrammingExerciseParticipation participation, String commitHash) {
        var vcsAccessLog = vcsAccessLogRepository.findNewestByParticipationId(participation.getId());
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
    @Async
    public void updateRepositoryActionType(LocalVCRepositoryUri localVCRepositoryUri, RepositoryActionType repositoryActionType) {
        var repositoryURL = localVCRepositoryUri.toString().replace("/git-upload-pack", "").replace("/git-receive-pack", "");
        var vcsAccessLog = vcsAccessLogRepository.findNewestByRepositoryUri(repositoryURL);
        if (vcsAccessLog.isPresent()) {
            vcsAccessLog.get().setRepositoryActionType(repositoryActionType);
            vcsAccessLogRepository.save(vcsAccessLog.get());
            updateAnalyticsActionType(vcsAccessLog.get(), repositoryActionType);
        }
    }

    /**
     * Helper function to update actionType at vcs_analytics_log table
     */
    private void updateAnalyticsActionType(VcsAccessLog vcsAccessLog, RepositoryActionType repositoryActionType) {
        User user = vcsAccessLog.getUser();
        Participation participation = vcsAccessLog.getParticipation();
        if (user == null || participation == null) {
            log.warn("Cannot update analytics log: user or participation is null");
            return;
        }
        Optional<Long> optionalCourseId = vcsAnalyticsLogRepository.findCourseIdByParticipationId(participation.getId());
        if (optionalCourseId.isPresent()) {
            Long courseId = optionalCourseId.get();
            String maskedUserId = AnalyticsHashUtils.maskUserId(user.getId(), courseId);
            Long exerciseId = vcsAccessLog.getParticipation().getExercise().getId();
            Optional<VcsAnalyticsLog> vcsAnalyticsLog = vcsAnalyticsLogRepository.findLatestByMaskedUserIdAndExerciseId(maskedUserId, exerciseId);
            if (vcsAnalyticsLog.isPresent()) {
                vcsAnalyticsLog.get().setRepositoryActionType(repositoryActionType);
                vcsAnalyticsLogRepository.save(vcsAnalyticsLog.get());
            }
        }
    }

    /**
     * Saves an vcsAccessLog and vcsAnalyticsLog
     *
     * @param vcsAccessLog The vcsAccessLog to save
     */
    @Async
    public void saveVcsAccesslog(VcsAccessLog vcsAccessLog) {
        vcsAccessLogRepository.save(vcsAccessLog);
        Participation participation = vcsAccessLog.getParticipation();
        if (participation != null && vcsAccessLog.getUser() != null) {
            saveAnalyticsLog(vcsAccessLog.getUser(), participation.getId(), participation.getExercise().getId(), vcsAccessLog.getRepositoryActionType(),
                    vcsAccessLog.getAuthenticationMechanism());
        }
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
