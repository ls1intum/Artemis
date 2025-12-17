package de.tum.cit.aet.artemis.iris.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_IRIS;

import java.util.Optional;

import org.springframework.context.annotation.Lazy;
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
import de.tum.cit.aet.artemis.iris.service.pyris.dto.rewriting.PyrisRewritingPipelineExecutionDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.rewriting.PyrisRewritingStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.rewriting.RewritingVariant;
import de.tum.cit.aet.artemis.iris.service.pyris.job.RewritingJob;
import de.tum.cit.aet.artemis.iris.service.websocket.IrisWebsocketService;

/**
 * Service to handle the rewriting subsystem of Iris.
 */
@Lazy
@Service
@Profile(PROFILE_IRIS)
public class IrisRewritingService {

    private final PyrisPipelineService pyrisPipelineService;

    private final LLMTokenUsageService llmTokenUsageService;

    private final CourseRepository courseRepository;

    private final IrisWebsocketService websocketService;

    private final PyrisJobService pyrisJobService;

    private final UserRepository userRepository;

    public IrisRewritingService(PyrisPipelineService pyrisPipelineService, LLMTokenUsageService llmTokenUsageService, CourseRepository courseRepository,
            IrisWebsocketService websocketService, PyrisJobService pyrisJobService, UserRepository userRepository) {
        this.pyrisPipelineService = pyrisPipelineService;
        this.llmTokenUsageService = llmTokenUsageService;
        this.courseRepository = courseRepository;
        this.websocketService = websocketService;
        this.pyrisJobService = pyrisJobService;
        this.userRepository = userRepository;
    }

    /**
     * Executes the rewriting pipeline on Pyris
     *
     * @param user          the user for which the pipeline should be executed
     * @param course        the course for which the pipeline should be executed
     * @param variant       the rewriting variant to be used
     * @param toBeRewritten the text to be rewritten
     */
    public void executeRewritingPipeline(User user, Course course, RewritingVariant variant, String toBeRewritten) {
        // @formatter:off
        pyrisPipelineService.executePipeline(
            "rewriting",
            user.getSelectedLLMUsage(),
            variant.name().toLowerCase(),
            Optional.empty(),
            pyrisJobService.createTokenForJob(token -> new RewritingJob(token, course.getId(), user.getId())),
            executionDto -> new PyrisRewritingPipelineExecutionDTO(executionDto, toBeRewritten, course.getId()),
            stages -> websocketService.send(user.getLogin(), websocketTopic(course.getId()), new PyrisRewritingStatusUpdateDTO(stages, null, null, null, null, ""))
        );
        // @formatter:on
    }

    /**
     * Takes a status update from Pyris containing a new rewriting result and sends it to the client via websocket
     *
     * @param job          Job related to the status update
     * @param statusUpdate the status update containing text recommendations
     * @return the same job that was passed in
     */
    public RewritingJob handleStatusUpdate(RewritingJob job, PyrisRewritingStatusUpdateDTO statusUpdate) {
        Course course = courseRepository.findByIdForUpdateElseThrow(job.courseId());
        if (statusUpdate.tokens() != null && !statusUpdate.tokens().isEmpty()) {
            llmTokenUsageService.saveLLMTokenUsage(statusUpdate.tokens(), LLMServiceType.IRIS, builder -> builder.withCourse(course.getId()).withUser(job.userId()));
        }

        var user = userRepository.findById(job.userId()).orElseThrow();
        websocketService.send(user.getLogin(), websocketTopic(job.courseId()), statusUpdate);

        return job;
    }

    private static String websocketTopic(long courseId) {
        return "rewriting/" + courseId;
    }

}
