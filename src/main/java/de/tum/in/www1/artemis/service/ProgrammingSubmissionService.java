package de.tum.in.www1.artemis.service;

import de.tum.in.www1.artemis.domain.Participation;
import de.tum.in.www1.artemis.domain.ProgrammingSubmission;
import de.tum.in.www1.artemis.domain.enumeration.InitializationState;
import de.tum.in.www1.artemis.domain.enumeration.SubmissionType;
import de.tum.in.www1.artemis.repository.ParticipationRepository;
import de.tum.in.www1.artemis.repository.ProgrammingSubmissionRepository;
import de.tum.in.www1.artemis.service.connectors.ContinuousIntegrationService;
import de.tum.in.www1.artemis.service.connectors.VersionControlService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.Optional;

@Service
@Transactional
public class ProgrammingSubmissionService {

    private final Logger log = LoggerFactory.getLogger(ProgrammingSubmissionService.class);

    private final ProgrammingSubmissionRepository programmingSubmissionRepository;
    private final ParticipationService participationService;
    private final ParticipationRepository participationRepository;
    private final Optional<VersionControlService> versionControlService;
    private final Optional<ContinuousIntegrationService> continuousIntegrationService;
    private final SimpMessageSendingOperations messagingTemplate;

    public ProgrammingSubmissionService(ProgrammingSubmissionRepository programmingSubmissionRepository, ParticipationRepository participationRepository,
                                        Optional<VersionControlService> versionControlService, Optional<ContinuousIntegrationService> continuousIntegrationService,
                                        ParticipationService participationService, SimpMessageSendingOperations messagingTemplate) {
        this.programmingSubmissionRepository = programmingSubmissionRepository;
        this.participationRepository = participationRepository;
        this.versionControlService = versionControlService;
        this.continuousIntegrationService = continuousIntegrationService;
        this.participationService = participationService;
        this.messagingTemplate = messagingTemplate;
    }

    public void notifyPush(Long participationId, Object requestBody) {
        Participation participation = participationRepository.getOne(participationId);
        if (participation == null) {
            log.error("Invalid participation received while notifying about push: " + participationId);
            return;
        }
        if (participation.getInitializationState() == InitializationState.INACTIVE) {
            //the build plan was deleted before, e.g. due to cleanup, therefore we need to reactivate the build plan by resuming the participation
            participationService.resume(participation.getExercise(), participation);
            //in addition we need to trigger a build so that we receive a result in a few seconds
            continuousIntegrationService.get().triggerBuild(participation);
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

        // notify user via websocket
        messagingTemplate.convertAndSend("/topic/participation/" + participation.getId() + "/newSubmission", programmingSubmission);
    }

}
