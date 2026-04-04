package de.tum.cit.aet.artemis.hyperion.service.codegeneration;

import java.util.Map;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.hyperion.config.HyperionEnabled;
import de.tum.cit.aet.artemis.hyperion.dto.HyperionCodeGenerationEventDTO;
import de.tum.cit.aet.artemis.hyperion.service.websocket.HyperionWebsocketService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;

@Service
@Lazy
@Conditional(HyperionEnabled.class)
public class HyperionCodeGenerationTaskService {

    private final HyperionCodeGenerationExecutionService executionService;

    private final HyperionWebsocketService websocket;

    public HyperionCodeGenerationTaskService(HyperionCodeGenerationExecutionService executionService, HyperionWebsocketService websocket) {
        this.executionService = executionService;
        this.websocket = websocket;
    }

    /**
     * Runs the code generation job asynchronously and publishes websocket updates.
     *
     * @param jobId          job identifier
     * @param user           requesting user
     * @param exercise       target exercise
     * @param courseId       resolved course id for telemetry attribution
     * @param repositoryType target repository type
     * @param cleanup        optional cleanup action to run after job completion
     */
    @Async
    public void runJobAsync(String jobId, User user, ProgrammingExercise exercise, Long courseId, RepositoryType repositoryType, Runnable cleanup) {
        var topicSuffix = "code-generation/jobs/" + jobId;
        var publisher = new WebsocketEventPublisher(websocket, user.getLogin(), topicSuffix, exercise, repositoryType, jobId);

        publisher.started();
        try {
            executionService.generateAndCompileCode(exercise, user, courseId, repositoryType, publisher);
        }
        catch (Exception ex) {
            publisher.error("Unhandled error: " + ex.getMessage());
        }
        finally {
            if (cleanup != null) {
                cleanup.run();
            }
        }
    }

    private static final class WebsocketEventPublisher implements HyperionCodeGenerationEventPublisher {

        private final HyperionWebsocketService websocket;

        private final String login;

        private final String topicSuffix;

        private final ProgrammingExercise exercise;

        private final RepositoryType repositoryType;

        private final String jobId;

        private WebsocketEventPublisher(HyperionWebsocketService websocket, String login, String topicSuffix, ProgrammingExercise exercise, RepositoryType repositoryType,
                String jobId) {
            this.websocket = websocket;
            this.login = login;
            this.topicSuffix = topicSuffix;
            this.exercise = exercise;
            this.repositoryType = repositoryType;
            this.jobId = jobId;
        }

        @Override
        public void started() {
            websocket.send(login, topicSuffix, new HyperionCodeGenerationEventDTO(HyperionCodeGenerationEventDTO.Type.STARTED, jobId, exercise.getId(), null, repositoryType, null,
                    null, null, null, null, null, "Started"));
        }

        @Override
        public void progress(int iteration) {
            websocket.send(login, topicSuffix, new HyperionCodeGenerationEventDTO(HyperionCodeGenerationEventDTO.Type.PROGRESS, jobId, exercise.getId(), iteration, repositoryType,
                    null, null, null, null, null, null, "Progress"));
        }

        @Override
        public void fileUpdated(String path, RepositoryType repoType, int iteration) {
            websocket.send(login, topicSuffix, new HyperionCodeGenerationEventDTO(HyperionCodeGenerationEventDTO.Type.FILE_UPDATED, jobId, exercise.getId(), iteration, repoType,
                    path, null, null, null, null, null, null));
        }

        @Override
        public void newFile(String path, RepositoryType repoType, int iteration) {
            websocket.send(login, topicSuffix, new HyperionCodeGenerationEventDTO(HyperionCodeGenerationEventDTO.Type.NEW_FILE, jobId, exercise.getId(), iteration, repoType, path,
                    null, null, null, null, null, null));
        }

        @Override
        public void done(HyperionCodeGenerationEventDTO.CompletionStatus completionStatus, HyperionCodeGenerationEventDTO.CompletionReason completionReason,
                Map<String, String> completionReasonParams, int attemptsUsed, String message) {
            boolean success = completionStatus == HyperionCodeGenerationEventDTO.CompletionStatus.SUCCESS;
            websocket.send(login, topicSuffix, new HyperionCodeGenerationEventDTO(HyperionCodeGenerationEventDTO.Type.DONE, jobId, exercise.getId(), null, repositoryType, null,
                    success, completionStatus, completionReason, completionReasonParams, attemptsUsed, message));
        }

        @Override
        public void error(String message) {
            websocket.send(login, topicSuffix, new HyperionCodeGenerationEventDTO(HyperionCodeGenerationEventDTO.Type.ERROR, jobId, exercise.getId(), null, repositoryType, null,
                    null, null, null, null, null, message));
        }
    }
}
