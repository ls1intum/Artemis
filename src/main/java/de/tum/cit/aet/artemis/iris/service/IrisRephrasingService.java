package de.tum.cit.aet.artemis.iris.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_IRIS;

import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.LLMServiceType;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.service.LLMTokenUsageService;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisJobService;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisPipelineService;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.rephrasing.PyrisRephrasingPipelineExecutionDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.rephrasing.PyrisRephrasingStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.rephrasing.RephrasingVariant;
import de.tum.cit.aet.artemis.iris.service.pyris.job.RephrasingJob;
import de.tum.cit.aet.artemis.iris.service.websocket.IrisWebsocketService;

/**
 * Service to handle the Competency generation subsystem of Iris.
 */
@Service
@Profile(PROFILE_IRIS)
public class IrisRephrasingService {

    private final PyrisPipelineService pyrisPipelineService;

    private final LLMTokenUsageService llmTokenUsageService;

    private final CourseRepository courseRepository;

    private final IrisWebsocketService websocketService;

    private final PyrisJobService pyrisJobService;

    private final UserRepository userRepository;

    public IrisRephrasingService(PyrisPipelineService pyrisPipelineService, LLMTokenUsageService llmTokenUsageService, CourseRepository courseRepository,
            IrisWebsocketService websocketService, PyrisJobService pyrisJobService, UserRepository userRepository) {
        this.pyrisPipelineService = pyrisPipelineService;
        this.llmTokenUsageService = llmTokenUsageService;
        this.courseRepository = courseRepository;
        this.websocketService = websocketService;
        this.pyrisJobService = pyrisJobService;
        this.userRepository = userRepository;
    }

    /**
     * Executes the competency extraction pipeline on Pyris for a given course, user and course description
     *
     * @param user          the user for which the pipeline should be executed
     * @param course        the course for which the pipeline should be executed
     * @param toBeRephrased the description of the course
     */
    public void executeRephrasingPipeline(User user, Course course, RephrasingVariant variant, String toBeRephrased) {
        // @formatter:off
        pyrisPipelineService.executePipeline(
                "rephrasing",
                variant.toString(),
                Optional.empty(),
                pyrisJobService.createTokenForJob(token -> new RephrasingJob(token, course.getId(), user.getId())),
                executionDto -> new PyrisRephrasingPipelineExecutionDTO(executionDto, toBeRephrased),
                stages -> websocketService.send(user.getLogin(), websocketTopic(course.getId()), new PyrisRephrasingStatusUpdateDTO(stages, null, null))
        );
        // @formatter:on
    }

    /**
     * Takes a status update from Pyris containing a new competency extraction result and sends it to the client via websocket
     *
     * @param job          Job related to the status update
     * @param statusUpdate the status update containing the new competency recommendations
     * @return the same job that was passed in
     */
    public RephrasingJob handleStatusUpdate(RephrasingJob job, PyrisRephrasingStatusUpdateDTO statusUpdate) {
        Course course = courseRepository.findByIdForUpdateElseThrow(job.courseId());
        if (statusUpdate.tokens() != null && !statusUpdate.tokens().isEmpty()) {
            llmTokenUsageService.saveLLMTokenUsage(statusUpdate.tokens(), LLMServiceType.IRIS, builder -> builder.withCourse(course.getId()).withUser(job.userId()));
        }

        var user = userRepository.findById(job.userId()).orElseThrow();
        websocketService.send(user.getLogin(), websocketTopic(job.courseId()), statusUpdate);

        return job;
    }

    private static String websocketTopic(long courseId) {
        return "rephrasing/" + courseId;
    }

}
