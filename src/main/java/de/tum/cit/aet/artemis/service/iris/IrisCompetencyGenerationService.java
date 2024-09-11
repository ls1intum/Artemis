package de.tum.cit.aet.artemis.service.iris;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.domain.Course;
import de.tum.cit.aet.artemis.domain.User;
import de.tum.cit.aet.artemis.domain.competency.CompetencyTaxonomy;
import de.tum.cit.aet.artemis.service.connectors.pyris.PyrisJobService;
import de.tum.cit.aet.artemis.service.connectors.pyris.PyrisPipelineService;
import de.tum.cit.aet.artemis.service.connectors.pyris.dto.competency.PyrisCompetencyExtractionPipelineExecutionDTO;
import de.tum.cit.aet.artemis.service.connectors.pyris.dto.competency.PyrisCompetencyRecommendationDTO;
import de.tum.cit.aet.artemis.service.connectors.pyris.dto.competency.PyrisCompetencyStatusUpdateDTO;
import de.tum.cit.aet.artemis.service.connectors.pyris.job.CompetencyExtractionJob;
import de.tum.cit.aet.artemis.service.iris.websocket.IrisWebsocketService;

/**
 * Service to handle the Competency generation subsytem of Iris.
 */
@Service
@Profile("iris")
public class IrisCompetencyGenerationService {

    private final PyrisPipelineService pyrisPipelineService;

    private final IrisWebsocketService websocketService;

    private final PyrisJobService pyrisJobService;

    public IrisCompetencyGenerationService(PyrisPipelineService pyrisPipelineService, IrisWebsocketService websocketService, PyrisJobService pyrisJobService) {
        this.pyrisPipelineService = pyrisPipelineService;
        this.websocketService = websocketService;
        this.pyrisJobService = pyrisJobService;
    }

    /**
     * Executes the competency extraction pipeline on Pyris for a given course, user and course description
     *
     * @param user                the user for which the pipeline should be executed
     * @param course              the course for which the pipeline should be executed
     * @param courseDescription   the description of the course
     * @param currentCompetencies the current competencies of the course (to avoid re-extraction)
     */
    public void executeCompetencyExtractionPipeline(User user, Course course, String courseDescription, PyrisCompetencyRecommendationDTO[] currentCompetencies) {
        // @formatter:off
        pyrisPipelineService.executePipeline(
                "competency-extraction",
                "default",
                pyrisJobService.createTokenForJob(token -> new CompetencyExtractionJob(token, course.getId(), user.getLogin())),
                executionDto -> new PyrisCompetencyExtractionPipelineExecutionDTO(executionDto, courseDescription, currentCompetencies, CompetencyTaxonomy.values(), 5),
                stages -> websocketService.send(user.getLogin(), websocketTopic(course.getId()), new PyrisCompetencyStatusUpdateDTO(stages, null))
        );
        // @formatter:on
    }

    /**
     * Takes a status update from Pyris containing a new competency extraction result and sends it to the client via websocket
     *
     * @param userLogin    the login of the user
     * @param courseId     the id of the course
     * @param statusUpdate the status update containing the new competency recommendations
     */
    public void handleStatusUpdate(String userLogin, long courseId, PyrisCompetencyStatusUpdateDTO statusUpdate) {
        websocketService.send(userLogin, websocketTopic(courseId), statusUpdate);
    }

    private static String websocketTopic(long courseId) {
        return "competencies/" + courseId;
    }

}
