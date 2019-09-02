package de.tum.in.www1.artemis.service;

import java.net.URL;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.http.HttpException;
import org.eclipse.jgit.lib.ObjectId;
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
import de.tum.in.www1.artemis.service.connectors.GitService;
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

    private final GitService gitService;

    private final SimpMessageSendingOperations messagingTemplate;

    public ProgrammingSubmissionService(ProgrammingSubmissionRepository programmingSubmissionRepository,
            ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository, Optional<VersionControlService> versionControlService,
            Optional<ContinuousIntegrationService> continuousIntegrationService, ParticipationService participationService, SimpMessageSendingOperations messagingTemplate,
            ProgrammingExerciseParticipationService programmingExerciseParticipationService, GitService gitService) {
        this.programmingSubmissionRepository = programmingSubmissionRepository;
        this.programmingExerciseStudentParticipationRepository = programmingExerciseStudentParticipationRepository;
        this.versionControlService = versionControlService;
        this.continuousIntegrationService = continuousIntegrationService;
        this.participationService = participationService;
        this.messagingTemplate = messagingTemplate;
        this.programmingExerciseParticipationService = programmingExerciseParticipationService;
        this.gitService = gitService;
    }

    /**
     * This method gets called if a new commit was pushed to the VCS
     *
     * @param participationId The ID to the Participation, where the push happend
     * @param requestBody the body of the post request by the VCS.
     * @return the ProgrammingSubmission for the last commitHash
     * @throws EntityNotFoundException if no ProgrammingExerciseParticipation could be found
     * @throws IllegalStateException if a ProgrammingSubmission already exists
     * @throws IllegalArgumentException it the Commit hash could not be parsed for submission from participation
     */
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

            try {
                continuousIntegrationService.get().triggerBuild(peParticipation);
            }
            catch (HttpException ex) {
                // TODO: This case is currently not handled. The correct handling would be creating the submission and informing the user that the build trigger failed.
            }
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
        participationService.save(participation);
        return programmingSubmission;
    }

    /**
     * A pending submission is one that does not have a result yet.
     *
     * @param participationId the id of the participation get the latest submission for
     * @return the latest pending submission if exists or null.
     * @throws EntityNotFoundException if the participation for the given id can't be found.
     * @throws IllegalArgumentException if the participation for the given id is not a programming exercise participation.
     * @throws IllegalAccessException if the user does not have access to the given participation.
     */
    @Transactional(readOnly = true)
    public Optional<ProgrammingSubmission> getLatestPendingSubmission(Long participationId) throws EntityNotFoundException, IllegalArgumentException, IllegalAccessException {
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

        return findLatestPendingSubmissionForParticipation(participationId);
    }

    /**
     * For every student participation of a programming exercise, try to find a pending submission.
     *
     * @param programmingExerciseId for which to search pending submissions
     * @return a Map of {[participationId]: ProgrammingSubmission | null}. Will contain an entry for every student participation of the exercise and a submission object if a pending submission exists or null if not.
     */
    @Transactional(readOnly = true)
    public Map<Long, Optional<ProgrammingSubmission>> getLatestPendingSubmissionsForProgrammingExercise(Long programmingExerciseId) {
        List<ProgrammingExerciseStudentParticipation> participations = programmingExerciseParticipationService.findByExerciseId(programmingExerciseId);
        return participations.stream().collect(Collectors.toMap(Participation::getId, p -> findLatestPendingSubmissionForParticipation(p.getId())));
    }

    private Optional<ProgrammingSubmission> findLatestPendingSubmissionForParticipation(final long participationId) {
        Optional<ProgrammingSubmission> submissionOpt = programmingSubmissionRepository.findFirstByParticipationIdOrderBySubmissionDateDesc(participationId);
        if (submissionOpt.isEmpty() || submissionOpt.get().getResult() != null) {
            // This is not an error case, it is very likely that there is no pending submission for a participation.
            return Optional.empty();
        }

        return submissionOpt;
    }

    /**
     * Create a submission with given submission type for the last commit hash of the given participation.
     * WARNING: The commitHash is used to map incoming results to submissions. Using this method could cause the result to have multiple fitting submissions.
     *
     * See discussion in: https://github.com/ls1intum/Artemis/pull/712#discussion_r314944129;
     *
     * Worst case scenario when using this method:
     * 1) Student executes a submission, the build is created on Bamboo
     * 2) The build takes longer than 2 minutes, this enables the student to trigger the submission again
     * 3) A new submission with the same commitHash is created on the server, there are now 2 submissions for the same commitHash and 2 running builds
     * 4) The first build returns a result to Artemis, this result is now attached to the second submission (that was just created)
     * 5) The second build finishes and returns a result to Artemis, this result is attached to the first submission
     *
     * @param participation to create submission for.
     * @param submissionType of the submission to create.
     * @return created submission.
     * @throws IllegalStateException if the last commit hash can't be retrieved.
     */
    @Transactional
    public ProgrammingSubmission createSubmissionWithLastCommitHashForParticipation(ProgrammingExerciseParticipation participation, SubmissionType submissionType)
            throws IllegalStateException {
        URL repoUrl = participation.getRepositoryUrlAsUrl();
        ObjectId lastCommitHash;
        try {
            lastCommitHash = gitService.getLastCommitHash(repoUrl);
        }
        catch (EntityNotFoundException ex) {
            throw new IllegalStateException("Last commit hash for participation " + participation.getId() + " could not be retrieved");
        }

        ProgrammingSubmission newSubmission = (ProgrammingSubmission) new ProgrammingSubmission().commitHash(lastCommitHash.getName()).submitted(true)
                .submissionDate(ZonedDateTime.now()).type(submissionType);
        newSubmission.setParticipation((Participation) participation);
        return programmingSubmissionRepository.save(newSubmission);
    }

    /**
     * Uses {@link #createSubmissionWithLastCommitHashForParticipation(ProgrammingExerciseParticipation, SubmissionType)} but for multiple participations.
     * Will ignore exceptions that are raised by this method and just not create a submission for the concerned participations.
     *
     * @param participations for which to create new submissions.
     * @param submissionType the type for the submissions to be created.
     * @return list of created submissions (might be smaller as the list of provided participations!).
     */
    @Transactional
    public List<ProgrammingSubmission> createSubmissionWithLastCommitHashForParticipationsOfExercise(List<ProgrammingExerciseStudentParticipation> participations,
            SubmissionType submissionType) {
        return participations.stream().map(participation -> {
            ProgrammingSubmission submission;
            try {
                submission = createSubmissionWithLastCommitHashForParticipation(participation, submissionType);
                return submission;
            }
            catch (IllegalStateException ex) {
                // The exception is already logged, we just return null here.
                return null;
            }
        }).filter(Objects::nonNull).collect(Collectors.toList());
    }
}
