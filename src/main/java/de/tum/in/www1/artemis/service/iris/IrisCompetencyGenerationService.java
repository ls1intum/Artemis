package de.tum.in.www1.artemis.service.iris;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.competency.CompetencyTaxonomy;
import de.tum.in.www1.artemis.service.connectors.pyris.PyrisJobService;
import de.tum.in.www1.artemis.service.connectors.pyris.PyrisPipelineService;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.competency.PyrisCompetencyExtractionPipelineExecutionDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.competency.PyrisCompetencyStatusUpdateDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.job.CompetencyExtractionJob;
import de.tum.in.www1.artemis.service.iris.websocket.IrisCompetencyWebsocketService;

/**
 * Service to handle the Competency generation subsytem of Iris.
 */
@Service
@Profile("iris")
public class IrisCompetencyGenerationService {

    private final PyrisPipelineService pyrisPipelineService;

    private final IrisCompetencyWebsocketService websocketService;

    private final PyrisJobService pyrisJobService;

    public IrisCompetencyGenerationService(PyrisPipelineService pyrisPipelineService, IrisCompetencyWebsocketService websocketService, PyrisJobService pyrisJobService) {
        this.pyrisPipelineService = pyrisPipelineService;
        this.websocketService = websocketService;
        this.pyrisJobService = pyrisJobService;
    }

    // Executes the competency extraction pipeline on Pyris for a given course, user and course description
    public void executeCompetencyExtractionPipeline(User user, Course course, String courseDescription) {
        // @formatter:off
        pyrisPipelineService.executePipeline(
                "competency-extraction",
                "default",
                pyrisJobService.createTokenForJob(token -> new CompetencyExtractionJob(token, course.getId(), user.getLogin())),
                executionDto -> new PyrisCompetencyExtractionPipelineExecutionDTO(executionDto, courseDescription, CompetencyTaxonomy.values(), 10),
                stages -> websocketService.sendCompetencies(user.getLogin(), course.getId(), new PyrisCompetencyStatusUpdateDTO(stages, null))
        );
        // @formatter:on
    }

    // Takes a status update from Pyris containing a new competency extraction result
    // and sends it to the client via websocket
    public void handleStatusUpdate(String userLogin, long courseId, PyrisCompetencyStatusUpdateDTO statusUpdate) {
        websocketService.sendCompetencies(userLogin, courseId, statusUpdate);
    }

}
