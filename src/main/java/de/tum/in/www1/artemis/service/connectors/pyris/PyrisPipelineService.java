package de.tum.in.www1.artemis.service.connectors.pyris;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.ProgrammingSubmission;
import de.tum.in.www1.artemis.domain.iris.session.IrisChatSession;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.PyrisPipelineExecutionSettingsDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.data.PyrisCourseDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.data.PyrisUserDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.status.PyrisStageDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.status.PyrisStageStateDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.tutorChat.PyrisTutorChatPipelineExecutionDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.job.TutorChatJob;
import de.tum.in.www1.artemis.service.iris.websocket.IrisChatWebsocketService;

@Service
@Profile("iris")
public class PyrisPipelineService {

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

    public void executeTutorChatPipeline(String variant, Optional<ProgrammingSubmission> latestSubmission, ProgrammingExercise exercise, IrisChatSession session) {
        var jobToken = pyrisJobService.addJob(new TutorChatJob(exercise.getCourseViaExerciseGroupOrCourseMember().getId(), exercise.getId(), session.getId()));
        var settingsDTO = new PyrisPipelineExecutionSettingsDTO(jobToken, List.of(), artemisBaseUrl);
        var preparingRequestStageInProgress = new PyrisStageDTO("Preparing request", 10, PyrisStageStateDTO.IN_PROGRESS, "Checking out repositories and loading data");
        var preparingRequestStageDone = new PyrisStageDTO("Preparing request", 10, PyrisStageStateDTO.DONE, "Checking out repositories and loading data");
        var executingPipelineStageNotStarted = new PyrisStageDTO("Executing pipeline", 30, PyrisStageStateDTO.NOT_STARTED, null);
        var executingPipelineStageInProgress = new PyrisStageDTO("Executing pipeline", 30, PyrisStageStateDTO.IN_PROGRESS, null);
        irisChatWebsocketService.sendStatusUpdate(session, List.of(preparingRequestStageInProgress, executingPipelineStageNotStarted));

        var executionDTO = new PyrisTutorChatPipelineExecutionDTO(settingsDTO, List.of(preparingRequestStageDone), latestSubmission.map(pyrisDTOService::toPyrisDTO).orElse(null),
                pyrisDTOService.toPyrisDTO(exercise), new PyrisCourseDTO(exercise.getCourseViaExerciseGroupOrCourseMember()), pyrisDTOService.toPyrisDTO(session.getMessages()),
                new PyrisUserDTO(session.getUser()));

        irisChatWebsocketService.sendStatusUpdate(session, List.of(preparingRequestStageDone, executingPipelineStageInProgress));

        pyrisConnectorService.executePipeline("tutor-chat", variant, executionDTO);
    }
}
