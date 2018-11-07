package de.tum.in.www1.artemis.service;

import de.tum.in.www1.artemis.domain.Participation;
import de.tum.in.www1.artemis.domain.ProgrammingSubmission;
import de.tum.in.www1.artemis.domain.enumeration.SubmissionType;
import de.tum.in.www1.artemis.repository.ParticipationRepository;
import de.tum.in.www1.artemis.repository.ProgrammingSubmissionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.Optional;

@Service
@Transactional
public class ProgrammingSubmissionService {

    private final Logger log = LoggerFactory.getLogger(ProgrammingSubmissionService.class);

    private final ProgrammingSubmissionRepository programmingSubmissionRepository;
    private final ParticipationRepository participationRepository;
    private final Optional<VersionControlService> versionControlService;

    public ProgrammingSubmissionService(ProgrammingSubmissionRepository programmingSubmissionRepository, ParticipationRepository participationRepository,
                                        Optional<VersionControlService> versionControlService) {
        this.programmingSubmissionRepository = programmingSubmissionRepository;
        this.participationRepository = participationRepository;
        this.versionControlService = versionControlService;
    }

    public void notifyPush(Long participationId, Object requestBody) {
        Participation participation = participationRepository.getOne(participationId);
        if (participation == null) {
            log.error("Invalid participation received while notifying about push: " + participationId);
            return;
        }

        ProgrammingSubmission programmingSubmission = new ProgrammingSubmission();

        try {
            String lastCommitHash = versionControlService.get().getLastCommitHash(requestBody);
            programmingSubmission.setCommitHash(lastCommitHash);
            log.info("create new programmingSubmission with commitHash: " + lastCommitHash);
        } catch (Exception ex) {
            log.error("Commit hash could not be parsed for submission from participation " + participation, ex);
        }

        programmingSubmission.setSubmitted(true);
        programmingSubmission.setSubmissionDate(ZonedDateTime.now());
        programmingSubmission.setType(SubmissionType.MANUAL);

        participation.addSubmissions(programmingSubmission);

        programmingSubmissionRepository.save(programmingSubmission);
    }

}
