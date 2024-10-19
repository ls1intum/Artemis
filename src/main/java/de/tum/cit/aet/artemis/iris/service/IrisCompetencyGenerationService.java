package de.tum.cit.aet.artemis.iris.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_IRIS;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyTaxonomy;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.LLMServiceType;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.service.LLMTokenUsageService;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisJobService;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisPipelineService;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.competency.PyrisCompetencyExtractionPipelineExecutionDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.competency.PyrisCompetencyRecommendationDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.competency.PyrisCompetencyStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.job.CompetencyExtractionJob;
import de.tum.cit.aet.artemis.iris.service.websocket.IrisWebsocketService;

/**
 * Service to handle the Competency generation subsystem of Iris.
 */
@Service
@Profile(PROFILE_IRIS)
public class IrisCompetencyGenerationService {

    private final PyrisPipelineService pyrisPipelineService;

    private final LLMTokenUsageService llmTokenUsageService;

    private final CourseRepository courseRepository;

    private final IrisWebsocketService websocketService;

    private final PyrisJobService pyrisJobService;

    public IrisCompetencyGenerationService(PyrisPipelineService pyrisPipelineService, LLMTokenUsageService llmTokenUsageService, CourseRepository courseRepository,
            IrisWebsocketService websocketService, PyrisJobService pyrisJobService) {
        this.pyrisPipelineService = pyrisPipelineService;
        this.llmTokenUsageService = llmTokenUsageService;
        this.courseRepository = courseRepository;
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
                pyrisJobService.createTokenForJob(token -> new CompetencyExtractionJob(token, course.getId(), user)),
                executionDto -> new PyrisCompetencyExtractionPipelineExecutionDTO(executionDto, courseDescription, currentCompetencies, CompetencyTaxonomy.values(), 5),
                stages -> websocketService.send(user.getLogin(), websocketTopic(course.getId()), new PyrisCompetencyStatusUpdateDTO(stages, null, null))
        );
        // @formatter:on
    }

    /**
     * Takes a status update from Pyris containing a new competency extraction result and sends it to the client via websocket
     *
     * @param job          Job related to the status update
     * @param statusUpdate the status update containing the new competency recommendations
     */
    public void handleStatusUpdate(CompetencyExtractionJob job, PyrisCompetencyStatusUpdateDTO statusUpdate) {
        Course course = courseRepository.findByIdForUpdateElseThrow(job.courseId());
        if (statusUpdate.tokens() != null && !statusUpdate.tokens().isEmpty()) {
            llmTokenUsageService.saveLLMTokenUsage(statusUpdate.tokens(), LLMServiceType.IRIS, builder -> builder.withCourse(course).withUser(job.user()));
        }
        websocketService.send(job.user().getLogin(), websocketTopic(job.courseId()), statusUpdate);
    }

    private static String websocketTopic(long courseId) {
        return "competencies/" + courseId;
    }

}
