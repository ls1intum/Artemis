package de.tum.in.www1.artemis.service.connectors.localci;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.service.WebsocketMessagingService;
import de.tum.in.www1.artemis.service.connectors.LtiNewResultService;
import de.tum.in.www1.artemis.service.connectors.localci.dto.LocalCIBuildResultNotificationDTO;
import de.tum.in.www1.artemis.service.programming.ProgrammingExerciseGradingService;

@Service
@Profile("localci")
public class LocalCIExecutorService {

    private final Logger log = LoggerFactory.getLogger(LocalCIExecutorService.class);

    private final ExecutorService executorService;

    private final LocalCIBuildJobService localCIBuildJobService;

    private final ProgrammingExerciseGradingService programmingExerciseGradingService;

    private final WebsocketMessagingService websocketMessagingService;

    private final LtiNewResultService ltiNewResultService;

    public LocalCIExecutorService(ExecutorService executorService, LocalCIBuildJobService localCIBuildJobService,
            ProgrammingExerciseGradingService programmingExerciseGradingService, WebsocketMessagingService websocketMessagingService, LtiNewResultService ltiNewResultService) {
        this.executorService = executorService;
        this.localCIBuildJobService = localCIBuildJobService;
        this.programmingExerciseGradingService = programmingExerciseGradingService;
        this.websocketMessagingService = websocketMessagingService;
        this.ltiNewResultService = ltiNewResultService;
    }

    public void addBuildJobToQueue(ProgrammingExerciseParticipation participation, Path assignmentRepositoryPath, Path testRepositoryPath, Path scriptPath) {
        CompletableFuture<LocalCIBuildResultNotificationDTO> futureResult = new CompletableFuture<>();
        executorService.submit(() -> {
            LocalCIBuildResultNotificationDTO buildResult = localCIBuildJobService.runBuildJob(participation, assignmentRepositoryPath, testRepositoryPath, scriptPath);
            futureResult.complete(buildResult);
        });

        futureResult.thenAccept(buildResult -> processResult(participation, buildResult));
    }

    private void processResult(ProgrammingExerciseParticipation participation, LocalCIBuildResultNotificationDTO buildResult) {

        // The 'user' is not properly logged into Artemis, this leads to an issue when accessing custom repository methods.
        // Therefore, a mock auth object has to be created.
        SecurityUtils.setAuthorizationObject();
        Optional<Result> optResult = programmingExerciseGradingService.processNewProgrammingExerciseResult(participation, buildResult);

        // Only notify the user about the new result if the result was created successfully.
        if (optResult.isPresent()) {
            Result result = optResult.get();
            log.debug("Send result to client over websocket. Result: {}, Submission: {}, Participation: {}", result, result.getSubmission(), result.getParticipation());
            // notify user via websocket
            websocketMessagingService.broadcastNewResult((Participation) participation, result);
            if (participation instanceof StudentParticipation) {
                // do not try to report results for template or solution participations
                ltiNewResultService.onNewResult((ProgrammingExerciseStudentParticipation) participation);
            }
        }
    }
}
