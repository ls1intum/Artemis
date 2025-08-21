package de.tum.cit.aet.artemis.exercise.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.repository.ParticipantScoreRepository;
import de.tum.cit.aet.artemis.assessment.repository.StudentScoreRepository;
import de.tum.cit.aet.artemis.assessment.repository.TeamScoreRepository;
import de.tum.cit.aet.artemis.assessment.service.ResultService;
import de.tum.cit.aet.artemis.atlas.api.CompetencyProgressApi;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.InitializationState;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.repository.ParticipationRepository;
import de.tum.cit.aet.artemis.exercise.repository.StudentParticipationRepository;
import de.tum.cit.aet.artemis.exercise.repository.SubmissionRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseStudentParticipationRepository;
import de.tum.cit.aet.artemis.programming.service.BuildLogEntryService;
import de.tum.cit.aet.artemis.programming.service.GitService;
import de.tum.cit.aet.artemis.programming.service.ParticipationVcsAccessTokenService;
import de.tum.cit.aet.artemis.programming.service.localci.SharedQueueManagementService;
import de.tum.cit.aet.artemis.programming.service.vcs.VersionControlService;

@Service
@Profile(PROFILE_CORE)
@Lazy
public class ParticipationDeletionService {

    private static final Logger log = LoggerFactory.getLogger(ParticipationDeletionService.class);

    private final StudentParticipationRepository studentParticipationRepository;

    private final ParticipantScoreRepository participantScoreRepository;

    private final ParticipationRepository participationRepository;

    private final TeamScoreRepository teamScoreRepository;

    private final SubmissionRepository submissionRepository;

    private final StudentScoreRepository studentScoreRepository;

    private final ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository;

    private final ResultService resultService;

    private final GitService gitService;

    private final BuildLogEntryService buildLogEntryService;

    private final ParticipationVcsAccessTokenService participationVcsAccessTokenService;

    // private final Optional<ContinuousIntegrationService> continuousIntegrationService;

    private final Optional<CompetencyProgressApi> competencyProgressApi;

    private final Optional<VersionControlService> versionControlService;

    private final Optional<SharedQueueManagementService> localCISharedBuildJobQueueService;

    public ParticipationDeletionService(StudentParticipationRepository studentParticipationRepository, ParticipantScoreRepository participantScoreRepository,
            SubmissionRepository submissionRepository, Optional<CompetencyProgressApi> competencyProgressApi, ParticipationRepository participationRepository,
            TeamScoreRepository teamScoreRepository, ResultService resultService, StudentScoreRepository studentScoreRepository,
            ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository, Optional<VersionControlService> versionControlService,
            GitService gitService, BuildLogEntryService buildLogEntryService, ParticipationVcsAccessTokenService participationVcsAccessTokenService,
            Optional<SharedQueueManagementService> localCISharedBuildJobQueueService) {
        this.studentParticipationRepository = studentParticipationRepository;
        this.participantScoreRepository = participantScoreRepository;
        this.submissionRepository = submissionRepository;
        this.competencyProgressApi = competencyProgressApi;
        this.participationRepository = participationRepository;
        this.teamScoreRepository = teamScoreRepository;
        this.resultService = resultService;
        this.studentScoreRepository = studentScoreRepository;
        this.programmingExerciseStudentParticipationRepository = programmingExerciseStudentParticipationRepository;
        // this.continuousIntegrationService = continuousIntegrationService;
        this.versionControlService = versionControlService;
        this.gitService = gitService;
        this.buildLogEntryService = buildLogEntryService;
        this.participationVcsAccessTokenService = participationVcsAccessTokenService;
        this.localCISharedBuildJobQueueService = localCISharedBuildJobQueueService;
    }

    /**
     * Delete all participations belonging to the given exercise
     *
     * @param exercise                      the exercise
     * @param recalculateCompetencyProgress specify if the competency progress should be recalculated
     */
    public void deleteAllByExercise(Exercise exercise, boolean recalculateCompetencyProgress) {
        var participationsToDelete = studentParticipationRepository.findByExerciseId(exercise.getId());
        log.info("Request to delete all {} participations of exercise with id : {}", participationsToDelete.size(), exercise.getId());

        // First remove all participant scores, as we are deleting all participations for the exercise
        participantScoreRepository.deleteAllByExerciseId(exercise.getId());

        for (StudentParticipation participation : participationsToDelete) {
            delete(participation.getId(), false);
        }

        if (recalculateCompetencyProgress) {
            competencyProgressApi.ifPresent(api -> api.updateProgressByLearningObjectAsync(exercise));
        }
    }

    /**
     * Delete all participations belonging to the given team
     *
     * @param teamId the id of the team
     */
    public void deleteAllByTeamId(Long teamId) {
        log.info("Request to delete all participations of Team with id : {}", teamId);

        // First remove all participant scores, as we are deleting all participations for the team
        teamScoreRepository.deleteAllByTeamId(teamId);

        List<StudentParticipation> participationsToDelete = studentParticipationRepository.findByTeamId(teamId);
        for (StudentParticipation participation : participationsToDelete) {
            delete(participation.getId(), false);
        }
    }

    /**
     * Delete the participation by participationId.
     *
     * @param participationId         the participationId of the entity
     * @param deleteParticipantScores false if the participant scores have already been bulk deleted, true by default otherwise
     */
    public void delete(long participationId, boolean deleteParticipantScores) {
        StudentParticipation participation = studentParticipationRepository.findByIdElseThrow(participationId);
        log.info("Request to delete Participation : {}", participation);

        if (participation instanceof ProgrammingExerciseStudentParticipation programmingExerciseParticipation) {
            var repositoryUri = programmingExerciseParticipation.getVcsRepositoryUri();
            String buildPlanId = programmingExerciseParticipation.getBuildPlanId();

            // if (buildPlanId != null) {
            // final var projectKey = programmingExerciseParticipation.getProgrammingExercise().getProjectKey();
            // continuousIntegrationService.orElseThrow().deleteBuildPlan(projectKey, buildPlanId);
            // }
            if (programmingExerciseParticipation.getRepositoryUri() != null) {
                try {
                    versionControlService.orElseThrow().deleteRepository(repositoryUri);
                }
                catch (Exception ex) {
                    log.error("Could not delete repository: {}", ex.getMessage());
                }
            }
            // delete local repository cache
            gitService.deleteLocalRepository(repositoryUri);

            participationVcsAccessTokenService.deleteByParticipationId(participationId);
        }

        // If local CI is active, remove all queued jobs for participation
        localCISharedBuildJobQueueService.ifPresent(service -> service.cancelAllJobsForParticipation(participationId));

        deleteResultsAndSubmissionsOfParticipation(participationId, deleteParticipantScores);
        studentParticipationRepository.delete(participation);
    }

    /**
     * Remove all results and submissions of the given participation. Will do nothing if invoked with a participation without results/submissions.
     *
     * @param participationId         the id of the participation to delete results/submissions from.
     * @param deleteParticipantScores false if the participant scores have already been bulk deleted, true by default otherwise
     */
    public void deleteResultsAndSubmissionsOfParticipation(Long participationId, boolean deleteParticipantScores) {
        log.debug("Request to delete all results and submissions of participation with id : {}", participationId);
        var participation = participationRepository.findByIdWithSubmissionsResults(participationId)
                .orElseThrow(() -> new EntityNotFoundException("Participation", participationId));

        // delete the participant score with the combination (exerciseId, studentId) or (exerciseId, teamId)
        if (deleteParticipantScores && participation instanceof StudentParticipation studentParticipation) {
            studentParticipation.getStudent().ifPresent(student -> studentScoreRepository.deleteByExerciseAndUser(participation.getExercise(), student));
            studentParticipation.getTeam().ifPresent(team -> teamScoreRepository.deleteByExerciseAndTeam(participation.getExercise(), team));
        }

        Set<Submission> submissions = participation.getSubmissions();
        // Delete all results for this participation
        Set<Result> resultsToBeDeleted = submissions.stream().flatMap(submission -> submission.getResults().stream()).collect(Collectors.toSet());
        // By removing the participation, the ResultListener will ignore this result instead of scheduling a participant score update
        // This is okay here, because we delete the whole participation (no older results will exist for the score)
        resultsToBeDeleted.forEach(result -> resultService.deleteResult(result, false));
        // Delete all submissions for this participation
        submissions.forEach(submission -> {
            // We have to set the results to an empty list because otherwise clearing the build log entries does not work correctly
            submission.setResults(Collections.emptyList());
            if (submission instanceof ProgrammingSubmission programmingSubmission) {
                buildLogEntryService.deleteBuildLogEntriesForProgrammingSubmission(programmingSubmission);
            }
            submissionRepository.deleteById(submission.getId());
        });
    }

    // /**
    // * Deletes the build plan on the continuous integration server and sets the initialization state of the participation to inactive.
    // * This means the participation can be resumed in the future
    // *
    // * @param participation that will be set to inactive
    // */
    // public void cleanupBuildPlan(ProgrammingExerciseStudentParticipation participation) {
    // // ignore participations without build plan id
    // if (participation.getBuildPlanId() != null) {
    // final var projectKey = ((ProgrammingExercise) participation.getExercise()).getProjectKey();
    // continuousIntegrationService.orElseThrow().deleteBuildPlan(projectKey, participation.getBuildPlanId());
    //
    // // If a graded participation gets cleaned up after the due date set the state back to finished. Otherwise, the participation is initialized
    // var dueDate = ExerciseDateService.getDueDate(participation);
    // if (!participation.isPracticeMode() && dueDate.isPresent() && ZonedDateTime.now().isAfter(dueDate.get())) {
    // participation.setInitializationState(InitializationState.FINISHED);
    // }
    // else {
    // participation.setInitializationState(InitializationState.INACTIVE);
    // }
    // participation.setBuildPlanId(null);
    // programmingExerciseStudentParticipationRepository.saveAndFlush(participation);
    // }
    // }

    /**
     * NOTICE: be careful with this method because it deletes the students code on the version control server Deletes the repository on the version control server and sets the
     * initialization state of the participation to finished. This means the participation cannot be resumed in the future and would need to be restarted
     *
     * @param participation to be stopped
     */
    public void cleanupRepository(ProgrammingExerciseStudentParticipation participation) {
        // ignore participations without repository URI
        if (participation.getRepositoryUri() != null) {
            versionControlService.orElseThrow().deleteRepository(participation.getVcsRepositoryUri());
            gitService.deleteLocalRepository(participation.getVcsRepositoryUri());
            participation.setRepositoryUri((String) null);
            participation.setInitializationState(InitializationState.FINISHED);
            programmingExerciseStudentParticipationRepository.saveAndFlush(participation);
        }
    }

}
