package de.tum.in.www1.artemis.service.connectors.localci;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
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

    private final Logger log = LoggerFactory.getLogger(LocalCITriggerService.class);

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
     */
    @Override
    public void triggerBuild(ProgrammingExerciseParticipation participation) {
        CompletableFuture<LocalCIBuildResult> futureResult = localCIExecutorService.addBuildJobToQueue(participation);
        futureResult.whenComplete((buildResult, exception) -> {
            if (exception != null) {
                log.error("Error while building and testing repository " + participation.getRepositoryUrl(), exception);
            }

            // The 'user' is not properly logged into Artemis, this leads to an issue when accessing custom repository methods.
            // Therefore, a mock auth object has to be created.
            SecurityUtils.setAuthorizationObject();
            Optional<Result> optResult = programmingExerciseGradingService.processNewProgrammingExerciseResult(participation, buildResult);

            // Only notify the user about the new result if the result was created successfully.
            if (optResult.isPresent()) {
                Result result = optResult.get();
                programmingMessagingService.notifyUserAboutNewResult(result, participation);
            }
        }).join(); // Wait for the completion and rethrow any exceptions.
    }
}
