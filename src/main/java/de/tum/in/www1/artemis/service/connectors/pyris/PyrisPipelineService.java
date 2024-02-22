package de.tum.in.www1.artemis.service.connectors.pyris;

import java.util.List;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.Submission;
import de.tum.in.www1.artemis.domain.iris.session.IrisChatSession;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.PyrisCodeEditorPipelineExecutionDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.PyrisCompetencyGenerationPipelineExecutionDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.PyrisHestiaDescriptionGenerationPipelineExecutionDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.PyrisPipelineExecutionSettingsDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.PyrisTutorChatPipelineExecutionDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.job.TutorChatJob;

@Service
public class PyrisPipelineService {

    private final PyrisConnectorService pyrisConnectorService;

    private final PyrisJobService pyrisJobService;

    public PyrisPipelineService(PyrisConnectorService pyrisConnectorService, PyrisJobService pyrisJobService) {
        this.pyrisConnectorService = pyrisConnectorService;
        this.pyrisJobService = pyrisJobService;
    }

    void executeTutorChatPipeline(String variant, Submission latestSubmission, ProgrammingExercise exercise, IrisChatSession session) {
        var jobToken = pyrisJobService.addJob(new TutorChatJob(exercise.getCourseViaExerciseGroupOrCourseMember().getId(), exercise.getId(), session.getId()));

        var settingsDTO = new PyrisPipelineExecutionSettingsDTO(jobToken, List.of());
        var executionDTO = new PyrisTutorChatPipelineExecutionDTO(null, latestSubmission, exercise, session.getMessages());
        pyrisConnectorService.executePipeline("tutor-chat", variant, executionDTO);
    }

    void executeHestiaDescriptionGenerationPipeline(String variant, PyrisHestiaDescriptionGenerationPipelineExecutionDTO executionDTO) {
        pyrisConnectorService.executePipeline("hestia-description-generation", variant, executionDTO);
    }

    void executeCodeEditorPipeline(String variant, PyrisCodeEditorPipelineExecutionDTO executionDTO) {
        pyrisConnectorService.executePipeline("code-editor", variant, executionDTO);
    }

    void executeCompetencyGenerationPipeline(String variant, PyrisCompetencyGenerationPipelineExecutionDTO executionDTO) {
        pyrisConnectorService.executePipeline("competency-generation", variant, executionDTO);
    }
}
