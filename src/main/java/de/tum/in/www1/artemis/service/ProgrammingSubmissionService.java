package de.tum.in.www1.artemis.service;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
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
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.service.connectors.ContinuousIntegrationService;
import de.tum.in.www1.artemis.service.connectors.VersionControlService;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

@Service
@Transactional
public class ProgrammingSubmissionService {

    private static final long RESULT_WAIT_LIMIT_SECONDS = 60;

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

    public ProgrammingSubmission notifyPush(Long participationId, Object requestBody) throws IllegalArgumentException {
        Participation participation = participationService.findOne(participationId);
        if (!(participation instanceof ProgrammingExerciseParticipation))
            throw new IllegalArgumentException();

        ProgrammingExerciseParticipation programmingExerciseParticipation = (ProgrammingExerciseParticipation) participation;

        if (participation instanceof ProgrammingExerciseStudentParticipation
                && ((ProgrammingExerciseStudentParticipation) programmingExerciseParticipation).getInitializationState() == InitializationState.INACTIVE) {
            // the build plan was deleted before, e.g. due to cleanup, therefore we need to
            // reactivate the
            // build plan by resuming the participation
            participationService.resumeExercise(programmingExerciseParticipation.getProgrammingExercise(),
                    (ProgrammingExerciseStudentParticipation) programmingExerciseParticipation);
            // in addition we need to trigger a build so that we receive a result in a few
            // seconds
            continuousIntegrationService.get().triggerBuild(programmingExerciseParticipation);
        }

        ProgrammingSubmission programmingSubmission = new ProgrammingSubmission();

        try {
            String lastCommitHash = versionControlService.get().getLastCommitHash(requestBody);
            programmingSubmission.setCommitHash(lastCommitHash);
            log.info("create new programmingSubmission with commitHash: " + lastCommitHash);
        }
        catch (Exception ex) {
            log.error("Commit hash could not be parsed for submission from participation " + participation, ex);
        }

        programmingSubmission.setSubmitted(true);
        programmingSubmission.setSubmissionDate(ZonedDateTime.now());
        programmingSubmission.setType(SubmissionType.MANUAL);

        participation.addSubmissions(programmingSubmission);

        return programmingSubmissionRepository.save(programmingSubmission);
    }

    /**
     * A pending submission is one that does not have a result yet and is not older than RESULT_WAIT_LIMIT_SECONDS.
     *
     * @param participationId the id of the participation get the latest submission for
     * return the latest pending submission if exists or null.
     */
    public ProgrammingSubmission getLatestPendingSubmission(Long participationId) throws EntityNotFoundException, IllegalArgumentException, IllegalAccessException {
        Participation participation = participationService.findOne(participationId);
        if (participation == null) {
            throw new EntityNotFoundException("Participation with id " + participationId + " could not be retrieved!");
        }
        if (!(participation instanceof ProgrammingExerciseParticipation)) {
            throw new IllegalArgumentException("Participation with id " + participationId + " is not a programming exercise participation!");
        }
        if (!programmingExerciseParticipationService.canAccessParticipation((ProgrammingExerciseParticipation) participation)) {
            throw new IllegalAccessException("Participation with id " + participationId + " can't be accessed by user " + SecurityUtils.getCurrentUserLogin());
        }

        Optional<ProgrammingSubmission> submissionOpt = programmingSubmissionRepository.findFirstByParticipationIdOrderBySubmissionDateDesc(participationId);
        if (!submissionOpt.isPresent() || submissionOpt.get().getResult() != null) {
            // This is not an error case, it is very likely that there is no pending submission for a participation.
            return null;
        }
        ProgrammingSubmission submission = submissionOpt.get();
        boolean submissionDateIsWithinWaitLimit = ChronoUnit.SECONDS.between(submission.getSubmissionDate(), ZonedDateTime.now()) <= RESULT_WAIT_LIMIT_SECONDS;
        if (submissionDateIsWithinWaitLimit) {
            return submission;
        }
        else {
            return null;
        }
    }

}
