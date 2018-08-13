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
            log.error("Invalid participation received while notifying about push: " + participationId);
            return;
        }

        ProgrammingSubmission programmingSubmission = new ProgrammingSubmission();

        try {
            String lastCommitHash = versionControlService.getLastCommitHash(requestBody);
            programmingSubmission.setCommitHash(lastCommitHash);
        } catch (Exception e) {
            programmingSubmission.setCommitHash("INVALID-COMMIT-HASH"); // TODO: check if this is ok
        }

        programmingSubmission.setSubmitted(true);
        programmingSubmission.setParticipation(participation);
        programmingSubmission.setSubmissionDate(ZonedDateTime.now());
        programmingSubmission.setType(SubmissionType.MANUAL); // TODO: check if this is correct/needed

        participation.addSubmissions(programmingSubmission);

        programmingSubmissionRepository.save(programmingSubmission);
    }

}
