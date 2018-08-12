package de.tum.in.www1.artemis.service;

import de.tum.in.www1.artemis.domain.Participation;
import de.tum.in.www1.artemis.domain.ProgrammingSubmission;
import de.tum.in.www1.artemis.repository.ParticipationRepository;
import de.tum.in.www1.artemis.repository.ProgrammingSubmissionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ProgrammingSubmissionService {

    private final Logger log = LoggerFactory.getLogger(BitbucketService.class);

    private final ProgrammingSubmissionRepository programmingSubmissionRepository;
    private final ParticipationRepository participationRepository;
    private final VersionControlService versionControlService;

    public ProgrammingSubmissionService(ProgrammingSubmissionRepository programmingSubmissionRepository, ParticipationRepository participationRepository,
                                        VersionControlService versionControlService) {
        this.programmingSubmissionRepository = programmingSubmissionRepository;
        this.participationRepository = participationRepository;
        this.versionControlService = versionControlService;
    }

    public void notifyPush(Long participationId, Object requestBody) {
        Participation participation = participationRepository.getOne(participationId);
        if (participation == null) {
            log.error("Invalid participation received: " + participationId);
            return;
        }

        String lastCommitHash = versionControlService.getLastCommitHash(requestBody);

        // TODO: Check if this creation is correct
        ProgrammingSubmission programmingSubmission = new ProgrammingSubmission();
        programmingSubmission.setCommitHash(lastCommitHash);
    }

}
