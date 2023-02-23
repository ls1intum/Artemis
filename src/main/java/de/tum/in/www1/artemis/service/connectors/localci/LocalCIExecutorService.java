package de.tum.in.www1.artemis.service.connectors.localci;

import java.nio.file.Path;
import java.util.concurrent.ExecutorService;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.service.WebsocketMessagingService;
import de.tum.in.www1.artemis.service.connectors.LtiNewResultService;
import de.tum.in.www1.artemis.service.programming.ProgrammingExerciseGradingService;

@Service
@Profile("localci")
public class LocalCIExecutorService {

    private final ExecutorService executorService;

    private final ProgrammingExerciseGradingService programmingExerciseGradingService;

    private final WebsocketMessagingService websocketMessagingService;

    private final LtiNewResultService ltiNewResultService;

    public LocalCIExecutorService(ExecutorService executorService, ProgrammingExerciseGradingService programmingExerciseGradingService,
            WebsocketMessagingService websocketMessagingService, LtiNewResultService ltiNewResultService) {
        this.executorService = executorService;
        this.programmingExerciseGradingService = programmingExerciseGradingService;
        this.websocketMessagingService = websocketMessagingService;
        this.ltiNewResultService = ltiNewResultService;
    }

    public void addBuildJobToQueue(ProgrammingExerciseParticipation participation, Path assignmentRepositoryPath, Path testRepositoryPath, Path scriptPath) {
        LocalCIBuildJob localCIBuildJob = new LocalCIBuildJob(participation, assignmentRepositoryPath, testRepositoryPath, scriptPath, programmingExerciseGradingService,
                websocketMessagingService, ltiNewResultService);
        executorService.submit(localCIBuildJob);
    }
}
