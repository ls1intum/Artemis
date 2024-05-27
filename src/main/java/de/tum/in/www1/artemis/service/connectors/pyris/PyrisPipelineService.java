package de.tum.in.www1.artemis.service.connectors.pyris;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.ProgrammingSubmission;
import de.tum.in.www1.artemis.domain.iris.session.IrisTutorChatSession;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.PyrisPipelineExecutionSettingsDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.data.PyrisCourseDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.data.PyrisUserDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.status.PyrisStageDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.status.PyrisStageStateDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.tutorChat.PyrisTutorChatPipelineExecutionDTO;
import de.tum.in.www1.artemis.service.iris.exception.IrisException;
import de.tum.in.www1.artemis.service.iris.websocket.IrisChatWebsocketService;

/**
 * Service responsible for executing the various Pyris pipelines in a type-safe manner.
 * Uses {@link PyrisConnectorService} to execute the pipelines and {@link PyrisJobService} to manage the jobs.
 */
@Service
@Profile("iris")
public class PyrisPipelineService {

    private static final Logger log = LoggerFactory.getLogger(PyrisPipelineService.class);

    private final PyrisConnectorService pyrisConnectorService;

    private final PyrisJobService pyrisJobService;

    private final PyrisDTOService pyrisDTOService;

    private final IrisChatWebsocketService irisChatWebsocketService;

    @Value("${server.url}")
    private String artemisBaseUrl;

    public PyrisPipelineService(PyrisConnectorService pyrisConnectorService, PyrisJobService pyrisJobService, PyrisDTOService pyrisDTOService,
            IrisChatWebsocketService irisChatWebsocketService) {
        this.pyrisConnectorService = pyrisConnectorService;
        this.pyrisJobService = pyrisJobService;
        this.pyrisDTOService = pyrisDTOService;
        this.irisChatWebsocketService = irisChatWebsocketService;
    }

    /**
     * Execute the tutor chat pipeline for the given exercise and session.
     * This method will create a new job, setup the DTOs and execution settings, and then execute the pipeline.
     *
     * @param variant          the variant of the pipeline to execute
     * @param latestSubmission the latest submission for the exercise
     * @param exercise         the programming exercise
     * @param session          the chat session
     */
    public void executeTutorChatPipeline(String variant, Optional<ProgrammingSubmission> latestSubmission, ProgrammingExercise exercise, IrisTutorChatSession session) {
        var jobToken = pyrisJobService.addJob(exercise.getCourseViaExerciseGroupOrCourseMember().getId(), exercise.getId(), session.getId());
        var settingsDTO = new PyrisPipelineExecutionSettingsDTO(jobToken, List.of(), artemisBaseUrl);

        // TODO: i18n
        var preparingRequestStageInProgress = new PyrisStageDTO("Loading repositories", 10, PyrisStageStateDTO.IN_PROGRESS, null);
        var preparingRequestStageDone = new PyrisStageDTO("Loading repositories", 10, PyrisStageStateDTO.DONE, null);
        var executingPipelineStageNotStarted = new PyrisStageDTO("Executing pipeline", 30, PyrisStageStateDTO.NOT_STARTED, null);
        var executingPipelineStageInProgress = new PyrisStageDTO("Executing pipeline", 30, PyrisStageStateDTO.IN_PROGRESS, null);
        irisChatWebsocketService.sendStatusUpdate(session, List.of(preparingRequestStageInProgress, executingPipelineStageNotStarted));

        try {
            // @formatter:off
            var executionDTO = new PyrisTutorChatPipelineExecutionDTO(
                    latestSubmission.map(pyrisDTOService::toPyrisDTO).orElse(null),
                    pyrisDTOService.toPyrisDTO(exercise),
                    new PyrisCourseDTO(exercise.getCourseViaExerciseGroupOrCourseMember()),
                    pyrisDTOService.toPyrisDTO(session.getMessages()),
                    new PyrisUserDTO(session.getUser()),
                    settingsDTO, List.of(preparingRequestStageDone)
            );
            // @formatter:on

            irisChatWebsocketService.sendStatusUpdate(session, List.of(preparingRequestStageDone, executingPipelineStageInProgress));

            try {
                pyrisConnectorService.executePipeline("tutor-chat", variant, executionDTO);
            }
            catch (PyrisConnectorException | IrisException e) {
                log.error("Failed to execute tutor chat pipeline", e);
                var executingPipelineStageFailed = new PyrisStageDTO("Executing pipeline", 30, PyrisStageStateDTO.ERROR, "An internal error occurred");
                irisChatWebsocketService.sendStatusUpdate(session, List.of(preparingRequestStageDone, executingPipelineStageFailed));
            }
        }
        catch (Exception e) {
            log.error("Failed to prepare tutor chat pipeline execution", e);
            var preparingRequestStageFailed = new PyrisStageDTO("Preparing request", 10, PyrisStageStateDTO.ERROR, "An internal error occurred");
            irisChatWebsocketService.sendStatusUpdate(session, List.of(preparingRequestStageFailed, executingPipelineStageNotStarted));
        }

    }
}
