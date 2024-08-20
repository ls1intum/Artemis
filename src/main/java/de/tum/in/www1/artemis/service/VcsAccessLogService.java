package de.tum.in.www1.artemis.service;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_LOCALVC;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.domain.vcstokens.AuthenticationMechanism;
import de.tum.in.www1.artemis.domain.vcstokens.VcsAccessLog;
import de.tum.in.www1.artemis.repository.VcsAccessLogRepository;
import de.tum.in.www1.artemis.web.rest.repository.RepositoryActionType;

@Profile(PROFILE_LOCALVC)
@Service
public class VcsAccessLogService {

    private static final Logger log = LoggerFactory.getLogger(VcsAccessLogService.class);

    private final VcsAccessLogRepository vcsAccessLogRepository;

    VcsAccessLogService(VcsAccessLogRepository vcsAccessLogRepository) {
        this.vcsAccessLogRepository = vcsAccessLogRepository;
    }

    /**
     * Creates a vcs access log entry and stores it to the database
     *
     * @param user                    The user accessing the repository
     * @param participation           The participation which owns the repository
     * @param actionType              The action type: READ or WRITE
     * @param authenticationMechanism The used authentication mechanism: password, vcs token (user/participation) or SSH
     */
    public void storeAccessLog(User user, ProgrammingExerciseParticipation participation, RepositoryActionType actionType, AuthenticationMechanism authenticationMechanism,
            String commitHash, String ipAddress) {
        log.debug("Storing access operation for user {}", user);

        VcsAccessLog accessLogEntry = new VcsAccessLog(user, (Participation) participation, user.getName(), user.getEmail(), actionType, authenticationMechanism, commitHash,
                ipAddress);
        vcsAccessLogRepository.save(accessLogEntry);
    }

    /**
     * Updates the commit hash after a successful push
     *
     * @param participation The participation to which the repository belongs to
     * @param commitHash    The newest commit hash which should get set for the access log entry
     */
    public void updateCommitHash(ProgrammingExerciseParticipation participation, String commitHash) {
        vcsAccessLogRepository.findByParticipationIdWhereCommitHashIsNull(participation.getId()).ifPresent(entry -> {
            entry.setCommitHash(commitHash);
            vcsAccessLogRepository.save(entry);
        });
    }
}
