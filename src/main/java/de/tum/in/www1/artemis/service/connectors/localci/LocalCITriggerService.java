package de.tum.in.www1.artemis.service.connectors.localci;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.exception.LocalCIException;
import de.tum.in.www1.artemis.service.connectors.ci.ContinuousIntegrationTriggerService;

/**
 * Service for triggering builds on the local CI system.
 */
@Service
@Profile("localci")
public class LocalCITriggerService implements ContinuousIntegrationTriggerService {

    private final LocalCISharedBuildJobQueue localCISharedBuildJobQueue;

    public LocalCITriggerService(LocalCISharedBuildJobQueue localCISharedBuildJobQueue) {
        this.localCISharedBuildJobQueue = localCISharedBuildJobQueue;
    }

    /**
     * Add a new build job to the queue managed by the ExecutorService and process the returned result.
     *
     * @param participation the participation of the repository which should be built and tested.
     * @throws LocalCIException if the build job could not be added to the queue.
     */
    @Override
    public void triggerBuild(ProgrammingExerciseParticipation participation) {
        triggerBuild(participation, null);
    }

    /**
     * Add a new build job for a specific commit to the queue managed by the ExecutorService and process the returned result.
     *
     * @param participation the participation of the repository which should be built and tested
     * @param commitHash    the commit hash of the commit that triggers the build. If it is null, the latest commit of the default branch will be built.
     * @throws LocalCIException if the build job could not be added to the queue.
     */
    public void triggerBuild(ProgrammingExerciseParticipation participation, String commitHash) {

        localCISharedBuildJobQueue.addBuildJobInformation(participation.getId(), commitHash);

        /*
         * CompletableFuture<LocalCIBuildResult> futureResult = localCIBuildJobManagementService.addBuildJobToQueue(participation, commitHash);
         * futureResult.thenAccept(buildResult -> {
         * // The 'user' is not properly logged into Artemis, this leads to an issue when accessing custom repository methods.
         * // Therefore, a mock auth object has to be created.
         * SecurityUtils.setAuthorizationObject();
         * Result result = programmingExerciseGradingService.processNewProgrammingExerciseResult(participation, buildResult);
         * if (result != null) {
         * programmingMessagingService.notifyUserAboutNewResult(result, participation);
         * }
         * else {
         * programmingMessagingService.notifyUserAboutSubmissionError((Participation) participation,
         * new BuildTriggerWebsocketError("Result could not be processed", participation.getId()));
         * }
         * });
         */
    }
}
