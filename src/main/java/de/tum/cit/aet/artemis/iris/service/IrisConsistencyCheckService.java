package de.tum.cit.aet.artemis.iris.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_IRIS;

import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.LLMServiceType;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.service.LLMTokenUsageService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisDTOService;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisJobService;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisPipelineService;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.consistencyCheck.PyrisConsistencyCheckPipelineExecutionDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.consistencyCheck.PyrisConsistencyCheckStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.job.ConsistencyCheckJob;
import de.tum.cit.aet.artemis.iris.service.websocket.IrisWebsocketService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

/**
 * Service to handle the rewriting subsystem of Iris.
 */
@Service
@Profile(PROFILE_IRIS)
public class IrisConsistencyCheckService {

    private final PyrisPipelineService pyrisPipelineService;

    private final LLMTokenUsageService llmTokenUsageService;

    private final ExerciseRepository exerciseRepository;

    private final IrisWebsocketService websocketService;

    private final PyrisJobService pyrisJobService;

    private final UserRepository userRepository;

    private final PyrisDTOService pyrisDTOService;

    public IrisConsistencyCheckService(PyrisPipelineService pyrisPipelineService, LLMTokenUsageService llmTokenUsageService, ExerciseRepository exerciseRepository,
            IrisWebsocketService websocketService, PyrisJobService pyrisJobService, UserRepository userRepository, PyrisDTOService pyrisDTOService) {
        this.pyrisPipelineService = pyrisPipelineService;
        this.llmTokenUsageService = llmTokenUsageService;
        this.exerciseRepository = exerciseRepository;
        this.websocketService = websocketService;
        this.pyrisJobService = pyrisJobService;
        this.userRepository = userRepository;
        this.pyrisDTOService = pyrisDTOService;
    }

    /**
     * Executes the consistency check pipeline on Pyris
     *
     * @param user     the user for which the pipeline should be executed
     * @param exercise the exercise for which the pipeline should be executed
     */
    public void executeConsistencyCheckPipeline(User user, ProgrammingExercise exercise) {
        Course course = exercise.getCourseViaExerciseGroupOrCourseMember();
        // @formatter:off
        pyrisPipelineService.executePipeline(
                "inconsistency-check",
                "default",
                Optional.empty(),
                pyrisJobService.createTokenForJob(token -> new ConsistencyCheckJob(token, course.getId(), exercise.getId(), user.getId())),
                executionDto -> new PyrisConsistencyCheckPipelineExecutionDTO(executionDto, pyrisDTOService.toPyrisProgrammingExerciseDTO(exercise)),
                stages -> websocketService.send(user.getLogin(), websocketTopic(exercise.getId()), new PyrisConsistencyCheckStatusUpdateDTO(stages, null, null))
        );
        // @formatter:on
    }

    /**
     * Takes a status update from Pyris containing a new consistency check result and sends it to the client via websocket
     *
     * @param job          Job related to the status update
     * @param statusUpdate the status update containing the consistency check result
     * @return the same job that was passed in
     */
    public ConsistencyCheckJob handleStatusUpdate(ConsistencyCheckJob job, PyrisConsistencyCheckStatusUpdateDTO statusUpdate) {
        Exercise exercise = exerciseRepository.findByIdElseThrow(job.exerciseId());
        if (statusUpdate.tokens() != null && !statusUpdate.tokens().isEmpty()) {
            llmTokenUsageService.saveLLMTokenUsage(statusUpdate.tokens(), LLMServiceType.IRIS, builder -> builder.withExercise(exercise.getId()).withUser(job.userId()));
        }

        var user = userRepository.findById(job.userId()).orElseThrow();
        websocketService.send(user.getLogin(), websocketTopic(job.exerciseId()), statusUpdate);

        return job;
    }

    private static String websocketTopic(long exerciseId) {
        return "consistency-check/exercises/" + exerciseId;
    }

}
