package de.tum.in.www1.artemis.service.connectors.localci;

import java.util.NoSuchElementException;
import java.util.concurrent.CompletableFuture;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.exception.LocalCIException;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.service.connectors.ci.ContinuousIntegrationTriggerService;
import de.tum.in.www1.artemis.service.connectors.localci.dto.LocalCIBuildResult;
import de.tum.in.www1.artemis.service.programming.ProgrammingExerciseGradingService;
import de.tum.in.www1.artemis.service.programming.ProgrammingMessagingService;

/**
 * Service for triggering builds on the local CI server.
 * Note: This service exists only to prevent a circular dependency LocalCIService -> ProgrammingExercisesGradingService -> LocalCIService, which would be present if the
 * triggerBuild method from the ContinuousIntegrationService interface would be used.
 */
@Service
@Profile("localci")
public class LocalCITriggerService implements ContinuousIntegrationTriggerService {

    private final LocalCIExecutorService localCIExecutorService;

    private final ProgrammingExerciseGradingService programmingExerciseGradingService;

    private final ProgrammingMessagingService programmingMessagingService;

    public LocalCITriggerService(LocalCIExecutorService localCIExecutorService, ProgrammingExerciseGradingService programmingExerciseGradingService,
            ProgrammingMessagingService programmingMessagingService) {
        this.localCIExecutorService = localCIExecutorService;
        this.programmingExerciseGradingService = programmingExerciseGradingService;
        this.programmingMessagingService = programmingMessagingService;
    }

    /**
     * Add a new build job to the queue managed by the ExecutorService.
     *
     * @param participation the participation of the repository which should be built and tested.
     * @throws LocalCIException if the build job could not be added to the queue.
     */
    @Override
    public void triggerBuild(ProgrammingExerciseParticipation participation) {
        CompletableFuture<LocalCIBuildResult> futureResult = localCIExecutorService.addBuildJobToQueue(participation);
        futureResult.thenAccept(buildResult -> {
            try {
                // The 'user' is not properly logged into Artemis, this leads to an issue when accessing custom repository methods.
                // Therefore, a mock auth object has to be created.
                SecurityUtils.setAuthorizationObject();
                Result result = programmingExerciseGradingService.processNewProgrammingExerciseResult(participation, buildResult).orElseThrow();
                programmingMessagingService.notifyUserAboutNewResult(result, participation);
            }
            catch (NoSuchElementException e) {
                programmingMessagingService.notifyUserAboutBuildTriggerError(participation, e.getMessage());
            }
        });
    }
}
