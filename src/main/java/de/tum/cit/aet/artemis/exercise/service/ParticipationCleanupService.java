package de.tum.cit.aet.artemis.exercise.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Optional;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.atlas.api.CompetencyProgressApi;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.localci.service.SharedQueueManagementService;
import de.tum.cit.aet.artemis.localci.service.ci.ContinuousIntegrationService;
import de.tum.cit.aet.artemis.localvc.service.GitService;
import de.tum.cit.aet.artemis.localvc.service.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.localvc.service.vcs.VersionControlService;
import de.tum.cit.aet.artemis.programming.service.BuildLogEntryService;

/** Executes identifier-only resource cleanup without depending on participation or result deletion. */
@Service
@Profile(PROFILE_CORE)
@Lazy
public class ParticipationCleanupService {

    private final Optional<ContinuousIntegrationService> continuousIntegrationService;

    private final Optional<VersionControlService> versionControlService;

    private final GitService gitService;

    private final Optional<SharedQueueManagementService> localCISharedBuildJobQueueService;

    private final BuildLogEntryService buildLogEntryService;

    private final Optional<CompetencyProgressApi> competencyProgressApi;

    private final ExerciseRepository exerciseRepository;

    public ParticipationCleanupService(Optional<ContinuousIntegrationService> continuousIntegrationService, Optional<VersionControlService> versionControlService,
            GitService gitService, Optional<SharedQueueManagementService> localCISharedBuildJobQueueService, BuildLogEntryService buildLogEntryService,
            Optional<CompetencyProgressApi> competencyProgressApi, ExerciseRepository exerciseRepository) {
        this.continuousIntegrationService = continuousIntegrationService;
        this.versionControlService = versionControlService;
        this.gitService = gitService;
        this.localCISharedBuildJobQueueService = localCISharedBuildJobQueueService;
        this.buildLogEntryService = buildLogEntryService;
        this.competencyProgressApi = competencyProgressApi;
        this.exerciseRepository = exerciseRepository;
    }

    /**
     * Replays cleanup strictly so preparation cannot reuse an old repository while its deletion is incomplete.
     *
     * @param command resource identifiers captured before deleting participation data
     */
    public void execute(ParticipationCleanupCommand command) {
        switch (command.operation()) {
            case BUILD_PLAN -> continuousIntegrationService.orElseThrow().deleteBuildPlan(command.projectKey(), command.buildPlanId());
            case VC_REPOSITORY -> versionControlService.orElseThrow().deleteRepository(new LocalVCRepositoryUri(command.repositoryUri()));
            case LOCAL_REPOSITORY -> gitService.deleteLocalRepository(command.repositoryUri() != null ? new LocalVCRepositoryUri(command.repositoryUri()) : null);
            case QUEUED_JOBS -> localCISharedBuildJobQueueService.ifPresent(service -> service.cancelAllJobsForParticipation(command.participationId()));
            case BUILD_LOG_FILES -> buildLogEntryService.deleteBuildLogFiles(command.exerciseId(), command.submissionId());
            case COMPETENCY_PROGRESS -> competencyProgressApi.ifPresent(api -> api.updateProgressByLearningObjectAsync(exerciseRepository
                    .findByIdWithExerciseGroupExamAndCourse(command.exerciseId()).orElseThrow(() -> new EntityNotFoundException("Exercise", command.exerciseId()))));
        }
    }
}
