package de.tum.in.www1.artemis.service.connectors.pyris;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.ProgrammingSubmission;
import de.tum.in.www1.artemis.domain.iris.session.IrisChatSession;
import de.tum.in.www1.artemis.domain.iris.session.IrisCourseChatSession;
import de.tum.in.www1.artemis.domain.iris.session.IrisExerciseChatSession;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.StudentParticipationRepository;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.PyrisPipelineExecutionSettingsDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.chat.PyrisChatPipelineExecutionBaseDataDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.chat.course.PyrisCourseChatPipelineExecutionDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.chat.exercise.PyrisExerciseChatPipelineExecutionDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.data.PyrisCourseDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.data.PyrisExtendedCourseDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.data.PyrisUserDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.status.PyrisStageDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.status.PyrisStageStateDTO;
import de.tum.in.www1.artemis.service.iris.exception.IrisException;
import de.tum.in.www1.artemis.service.iris.websocket.IrisChatWebsocketService;
import de.tum.in.www1.artemis.service.metrics.LearningMetricsService;

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

    private final CourseRepository courseRepository;

    private final StudentParticipationRepository studentParticipationRepository;

    private final LearningMetricsService learningMetricsService;

    @Value("${server.url}")
    private String artemisBaseUrl;

    public PyrisPipelineService(PyrisConnectorService pyrisConnectorService, PyrisJobService pyrisJobService, PyrisDTOService pyrisDTOService,
            IrisChatWebsocketService irisChatWebsocketService, CourseRepository courseRepository, LearningMetricsService learningMetricsService,
            StudentParticipationRepository studentParticipationRepository) {
        this.pyrisConnectorService = pyrisConnectorService;
        this.pyrisJobService = pyrisJobService;
        this.pyrisDTOService = pyrisDTOService;
        this.irisChatWebsocketService = irisChatWebsocketService;
        this.courseRepository = courseRepository;
        this.learningMetricsService = learningMetricsService;
        this.studentParticipationRepository = studentParticipationRepository;
    }

    /**
     * Executes a chat pipeline for a given chat session subtype.
     * This method prepares the execution data, executes the specified pipeline, and handles the state updates.
     * <p>
     * The general idea of this being generic is that the pipeline execution is the same for all chat sessions,
     * but the specific data required for the pipeline execution is different for each session / pipeline type.
     * Therefore, the specific data is provided by a function that accepts the basic chat data and returns the more specific data.
     *
     * @param variant              the variant of the pipeline
     * @param session              the active chat session, must inherit from {@link IrisChatSession}
     * @param pipelineName         the name of the pipeline to be executed
     * @param executionDtoSupplier a function that accepts basic chat data and returns an execution DTO specific to the pipeline being executed
     * @param jobTokenSupplier     a supplier that provides a unique job token for tracking the pipeline execution
     * @param <T>                  the type of the chat session
     * @param <U>                  the type of the execution DTO
     */
    private <T extends IrisChatSession, U> void executeChatPipeline(String variant, T session, String pipelineName,
            Function<PyrisChatPipelineExecutionBaseDataDTO, U> executionDtoSupplier, Supplier<String> jobTokenSupplier) {

        // Retrieve the unique job token for this pipeline execution
        var jobToken = jobTokenSupplier.get();

        // Set up initial pipeline execution settings with the server base URL
        var settingsDTO = new PyrisPipelineExecutionSettingsDTO(jobToken, List.of(), artemisBaseUrl);

        // Define the preparation stages of pipeline execution with their initial states
        // There will be more stages added in Pyris later
        var preparingRequestStageInProgress = new PyrisStageDTO("Preparing", 10, PyrisStageStateDTO.IN_PROGRESS, null);
        var preparingRequestStageDone = new PyrisStageDTO("Preparing", 10, PyrisStageStateDTO.DONE, null);
        var executingPipelineStageNotStarted = new PyrisStageDTO("Executing pipeline", 30, PyrisStageStateDTO.NOT_STARTED, null);

        // Send initial status update indicating that the preparation stage is in progress
        irisChatWebsocketService.sendStatusUpdate(session, List.of(preparingRequestStageInProgress, executingPipelineStageNotStarted));

        try {
            // Prepare the base execution data for the pipeline.
            // It is shared among chat pipelines and included as field "base" in the specific execution DTOs.
            var base = new PyrisChatPipelineExecutionBaseDataDTO(pyrisDTOService.toPyrisMessageDTOList(session.getMessages()), new PyrisUserDTO(session.getUser()), settingsDTO,
                    List.of(preparingRequestStageDone) // The initial stage is done when the request arrives at Pyris
            );

            // Prepare the specific execution data for the pipeline
            // This is implementation-specific and includes additional data required for the pipeline
            // Implementations must deliver the base data, too
            U executionDTO = executionDtoSupplier.apply(base);

            // Send a status update that preparation is done and pipeline execution is starting
            var executingPipelineStageInProgress = new PyrisStageDTO("Executing pipeline", 30, PyrisStageStateDTO.IN_PROGRESS, null);
            irisChatWebsocketService.sendStatusUpdate(session, List.of(preparingRequestStageDone, executingPipelineStageInProgress));

            try {
                // Execute the pipeline using the connector service
                pyrisConnectorService.executePipeline(pipelineName, variant, executionDTO);
            }
            catch (PyrisConnectorException | IrisException e) {
                log.error("Failed to execute " + pipelineName + " pipeline", e);
                var executingPipelineStageFailed = new PyrisStageDTO("Executing pipeline", 30, PyrisStageStateDTO.ERROR, "An internal error occurred");
                irisChatWebsocketService.sendStatusUpdate(session, List.of(preparingRequestStageDone, executingPipelineStageFailed));
            }
        }
        catch (Exception e) {
            log.error("Failed to prepare " + pipelineName + " pipeline execution", e);
            var preparingRequestStageFailed = new PyrisStageDTO("Preparing request", 10, PyrisStageStateDTO.ERROR, "An internal error occurred");
            irisChatWebsocketService.sendStatusUpdate(session, List.of(preparingRequestStageFailed, executingPipelineStageNotStarted));
        }
    }

    /**
     * Execute the exercise chat pipeline for the given session.
     * It provides specific data for the exercise chat pipeline, including:
     * - The latest submission of the student
     * - The programming exercise
     * - The course the exercise is part of
     * <p>
     *
     * @see PyrisPipelineService#executeChatPipeline for more details on the pipeline execution process.
     *
     * @param variant          the variant of the pipeline
     * @param latestSubmission the latest submission of the student
     * @param exercise         the programming exercise
     * @param session          the chat session
     */
    public void executeExerciseChatPipeline(String variant, Optional<ProgrammingSubmission> latestSubmission, ProgrammingExercise exercise, IrisExerciseChatSession session) {
        executeChatPipeline(variant, session, "tutor-chat", // TODO: Rename this to 'exercise-chat' with next breaking Pyris version
                base -> new PyrisExerciseChatPipelineExecutionDTO(base.chatHistory(), base.user(), base.settings(), base.initialStages(),
                        latestSubmission.map(pyrisDTOService::toPyrisSubmissionDTO).orElse(null), pyrisDTOService.toPyrisProgrammingExerciseDTO(exercise),
                        new PyrisCourseDTO(exercise.getCourseViaExerciseGroupOrCourseMember())),
                () -> pyrisJobService.addExerciseChatJob(exercise.getCourseViaExerciseGroupOrCourseMember().getId(), exercise.getId(), session.getId()));
    }

    /**
     * Execute the course chat pipeline for the given session.
     * It provides specific data for the course chat pipeline, including:
     * - The full course with the participation of the student
     * - The metrics of the student in the course
     * <p>
     *
     * @see PyrisPipelineService#executeChatPipeline for more details on the pipeline execution process.
     *
     * @param variant the variant of the pipeline
     * @param session the chat session
     */
    public void executeCourseChatPipeline(String variant, IrisCourseChatSession session) {
        var courseId = session.getCourse().getId();
        var studentId = session.getUser().getId();
        executeChatPipeline(variant, session, "course-chat", base -> {
            var fullCourse = loadCourseWithParticipationOfStudent(courseId, studentId);
            var metrics = learningMetricsService.getStudentCourseMetrics(session.getUser().getId(), courseId);
            return new PyrisCourseChatPipelineExecutionDTO(base.chatHistory(), base.user(), base.settings(), base.initialStages(), PyrisExtendedCourseDTO.of(fullCourse), metrics);
        }, () -> pyrisJobService.addCourseChatJob(courseId, session.getId()));
    }

    /**
     * Load the course with the participation of the student and set the participations on the exercises.
     * <p>
     * Spring Boot 3 does not support conditional left joins, so we have to load the participations separately.
     *
     * @param courseId  the id of the course
     * @param studentId the id of the student
     */
    private Course loadCourseWithParticipationOfStudent(long courseId, long studentId) {
        Course course = courseRepository.findWithEagerExercisesAndLecturesAndAttachmentsAndLectureUnitsAndCompetenciesAndExamsById(courseId).orElseThrow();
        List<StudentParticipation> participations = studentParticipationRepository.findByStudentIdAndIndividualExercisesWithEagerSubmissionsResultIgnoreTestRuns(studentId,
                course.getExercises());

        Map<Long, Set<StudentParticipation>> participationMap = new HashMap<>();
        for (StudentParticipation participation : participations) {
            Long exerciseId = participation.getExercise().getId();
            participationMap.computeIfAbsent(exerciseId, k -> new HashSet<>()).add(participation);
        }

        course.getExercises().forEach(exercise -> {
            Set<StudentParticipation> exerciseParticipations = participationMap.getOrDefault(exercise.getId(), Set.of());
            exercise.setStudentParticipations(exerciseParticipations);
        });

        return course;
    }
}
