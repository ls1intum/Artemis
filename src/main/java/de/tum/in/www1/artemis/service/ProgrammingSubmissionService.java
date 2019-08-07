package de.tum.in.www1.artemis.service;

import java.time.ZonedDateTime;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.InitializationState;
import de.tum.in.www1.artemis.domain.enumeration.SubmissionType;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseStudentParticipationRepository;
import de.tum.in.www1.artemis.repository.ProgrammingSubmissionRepository;
import de.tum.in.www1.artemis.service.connectors.ContinuousIntegrationService;
import de.tum.in.www1.artemis.service.connectors.VersionControlService;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

@Service
@Transactional
public class ProgrammingSubmissionService {

    private final Logger log = LoggerFactory.getLogger(ProgrammingSubmissionService.class);

    private final ProgrammingSubmissionRepository programmingSubmissionRepository;

    private final ParticipationService participationService;

    private final ProgrammingExerciseParticipationService programmingExerciseParticipationService;

    private final ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository;

    private final Optional<VersionControlService> versionControlService;

    private final Optional<ContinuousIntegrationService> continuousIntegrationService;

    private final SimpMessageSendingOperations messagingTemplate;

    public ProgrammingSubmissionService(ProgrammingSubmissionRepository programmingSubmissionRepository,
            ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository, Optional<VersionControlService> versionControlService,
            Optional<ContinuousIntegrationService> continuousIntegrationService, ParticipationService participationService, SimpMessageSendingOperations messagingTemplate,
            ProgrammingExerciseParticipationService programmingExerciseParticipationService) {
        this.programmingSubmissionRepository = programmingSubmissionRepository;
        this.programmingExerciseStudentParticipationRepository = programmingExerciseStudentParticipationRepository;
        this.versionControlService = versionControlService;
        this.continuousIntegrationService = continuousIntegrationService;
        this.participationService = participationService;
        this.messagingTemplate = messagingTemplate;
        this.programmingExerciseParticipationService = programmingExerciseParticipationService;
    }

    public ProgrammingSubmission notifyPush(Long participationId, Object requestBody) throws EntityNotFoundException, IllegalStateException, IllegalArgumentException {
        Participation participation = participationService.findOne(participationId);
        if (!(participation instanceof ProgrammingExerciseParticipation)) {
            throw new EntityNotFoundException("ProgrammingExerciseParticipation with id " + participationId + " could not be found!");
        }

        ProgrammingExerciseParticipation peParticipation = (ProgrammingExerciseParticipation) participation;

        if (participation instanceof ProgrammingExerciseStudentParticipation
                && ((ProgrammingExerciseStudentParticipation) peParticipation).getInitializationState() == InitializationState.INACTIVE) {
            // the build plan was deleted before, e.g. due to cleanup, therefore we need to reactivate the build plan by resuming the participation
            participationService.resumeExercise(peParticipation.getProgrammingExercise(), (ProgrammingExerciseStudentParticipation) peParticipation);
            // in addition we need to trigger a build so that we receive a result in a few seconds
            continuousIntegrationService.get().triggerBuild(peParticipation);
        }

        String lastCommitHash;
        try {
            lastCommitHash = versionControlService.get().getLastCommitHash(requestBody);
        }
        catch (Exception ex) {
            log.error("Commit hash could not be parsed for submission from participation " + participation, ex);
            throw new IllegalArgumentException(ex);
        }

        // There can't be two submissions for the same participation and commitHash!
        ProgrammingSubmission programmingSubmission = programmingSubmissionRepository.findFirstByParticipationIdAndCommitHash(participationId, lastCommitHash);
        if (programmingSubmission != null) {
            throw new IllegalStateException("Submission for participation id " + participationId + " and commitHash " + lastCommitHash + "already exists!");
        }

        programmingSubmission = new ProgrammingSubmission();
        programmingSubmission.setCommitHash(lastCommitHash);
        log.info("create new programmingSubmission with commitHash: " + lastCommitHash + " for participation " + participationId);

        programmingSubmission.setSubmitted(true);
        programmingSubmission.setSubmissionDate(ZonedDateTime.now());
        programmingSubmission.setType(SubmissionType.MANUAL);

        participation.addSubmissions(programmingSubmission);

        programmingSubmissionRepository.save(programmingSubmission);
        // TODO: do we need to save the participation because we added the submission?
        return programmingSubmission;
    }
}
