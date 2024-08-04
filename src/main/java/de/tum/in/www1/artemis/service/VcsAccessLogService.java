package de.tum.in.www1.artemis.service;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_LOCALVC;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.VcsAccessLog;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.repository.VcsAccessLogRepository;

@Profile(PROFILE_LOCALVC)
@Service
public class VcsAccessLogService {

    private static final Logger log = LoggerFactory.getLogger(VcsAccessLogService.class);

    private final VcsAccessLogRepository vcsAccessLogRepository;

    VcsAccessLogService(VcsAccessLogRepository vcsAccessLogRepository) {
        this.vcsAccessLogRepository = vcsAccessLogRepository;
    }

    public void storeOperation(User user, ProgrammingExerciseParticipation participation, String requestURI, String authenticationMechanism, HttpServletRequest request) {
        log.debug("Storing access operation for user {}", user);

        VcsAccessLog accessLogEntry = new VcsAccessLog(user, (Participation) participation, "op", "mec", "ip");
        vcsAccessLogRepository.save(accessLogEntry);
    }
}
