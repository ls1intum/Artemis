package de.tum.in.www1.artemis.service.connectors.pyris;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
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
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.ProgrammingSubmission;
import de.tum.in.www1.artemis.domain.competency.CompetencyJol;
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
import de.tum.in.www1.artemis.service.connectors.pyris.dto.data.PyrisEventDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.data.PyrisExerciseWithStudentSubmissionsDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.data.PyrisExtendedCourseDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.data.PyrisUserDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.status.PyrisStageDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.status.PyrisStageState;
import de.tum.in.www1.artemis.service.iris.exception.IrisException;
import de.tum.in.www1.artemis.service.iris.websocket.IrisChatWebsocketService;
import de.tum.in.www1.artemis.service.metrics.LearningMetricsService;
import de.tum.in.www1.artemis.web.rest.dto.competency.CompetencyJolDTO;

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
        var preparingRequestStageInProgress = new PyrisStageDTO("Preparing", 10, PyrisStageState.IN_PROGRESS, null);
        var preparingRequestStageDone = new PyrisStageDTO("Preparing", 10, PyrisStageState.DONE, null);
        var executingPipelineStageNotStarted = new PyrisStageDTO("Executing pipeline", 30, PyrisStageState.NOT_STARTED, null);

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
            var executingPipelineStageInProgress = new PyrisStageDTO("Executing pipeline", 30, PyrisStageState.IN_PROGRESS, null);
            irisChatWebsocketService.sendStatusUpdate(session, List.of(preparingRequestStageDone, executingPipelineStageInProgress));

            try {
                // Execute the pipeline using the connector service
                pyrisConnectorService.executePipeline(pipelineName, variant, executionDTO);
            }
            catch (PyrisConnectorException | IrisException e) {
                log.error("Failed to execute " + pipelineName + " pipeline", e);
                var executingPipelineStageFailed = new PyrisStageDTO("Executing pipeline", 30, PyrisStageState.ERROR, "An internal error occurred");
                irisChatWebsocketService.sendStatusUpdate(session, List.of(preparingRequestStageDone, executingPipelineStageFailed));
            }
        }
        catch (Exception e) {
            log.error("Failed to prepare " + pipelineName + " pipeline execution", e);
            var preparingRequestStageFailed = new PyrisStageDTO("Preparing request", 10, PyrisStageState.ERROR, "An internal error occurred");
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
     * @param variant          the variant of the pipeline
     * @param latestSubmission the latest submission of the student
     * @param exercise         the programming exercise
     * @param session          the chat session
     * @see PyrisPipelineService#executeChatPipeline for more details on the pipeline execution process.
     */
    public void executeExerciseChatPipeline(String variant, Optional<ProgrammingSubmission> latestSubmission, ProgrammingExercise exercise, IrisExerciseChatSession session) {
        executeChatPipeline(variant, session, "tutor-chat", // TODO: Rename this to 'exercise-chat' with next breaking Pyris version
                base -> new PyrisExerciseChatPipelineExecutionDTO(latestSubmission.map(pyrisDTOService::toPyrisSubmissionDTO).orElse(null),
                        pyrisDTOService.toPyrisProgrammingExerciseDTO(exercise), new PyrisCourseDTO(exercise.getCourseViaExerciseGroupOrCourseMember()), base.chatHistory(),
                        base.user(), base.settings(), base.initialStages()),
                () -> pyrisJobService.addExerciseChatJob(exercise.getCourseViaExerciseGroupOrCourseMember().getId(), exercise.getId(), session.getId()));
    }

    /**
     * Execute the course chat pipeline for the given session.
     * It provides specific data for the course chat pipeline, including:
     * - The full course with the participation of the student
     * - The metrics of the student in the course
     * - Event-specific data if this is due to a specific event
     *
     * @param variant       the variant of the pipeline
     * @param session       the chat session
     * @param eventObject   if this function triggers a pipeline execution due to a specific event, this object is the event payload
     * @param eventDtoClass the class of the DTO that should be generated from the object
     * @param <T>           the type of the object
     * @param <U>           the type of the DTO
     */
    private <T, U extends PyrisEventDTO> void executeCourseChatPipeline(String variant, IrisCourseChatSession session, T eventObject, Class<U> eventDtoClass) {
        var courseId = session.getCourse().getId();
        var studentId = session.getUser().getId();
        this.log.debug("Executing course chat pipeline vairant {} for course {} and student {}", variant, courseId, studentId);
        executeChatPipeline(variant, session, "course-chat", base -> {
            var fullCourse = loadCourseWithParticipationOfStudent(courseId, studentId);
            return new PyrisCourseChatPipelineExecutionDTO(PyrisExtendedCourseDTO.of(fullCourse),
                    learningMetricsService.getStudentCourseMetrics(session.getUser().getId(), courseId),
                    eventObject == null ? null : generateDTOFromObjectType(eventDtoClass, eventObject), base.chatHistory(), base.user(), base.settings(), base.initialStages());
        }, () -> pyrisJobService.addCourseChatJob(courseId, session.getId()));
    }

    /**
     * Execute the course chat pipeline for the given session.
     * It provides specific data for the course chat pipeline, including:
     * - The full course with the participation of the student
     * - The metrics of the student in the course
     * - The competency JoL if this is due to a JoL set event
     * <p>
     *
     * @param variant the variant of the pipeline
     * @param session the chat session
     * @param object  if this function triggers a pipeline execution due to a specific event, this object is the event payload
     * @see PyrisPipelineService#executeChatPipeline for more details on the pipeline execution process.
     */
    public void executeCourseChatPipeline(String variant, IrisCourseChatSession session, Object object) {
        this.log.debug("Executing course chat pipeline variant {} with object {}", variant, object.getClass());
        switch (object) {
            case null -> executeCourseChatPipeline(variant, session, null, null);
            case CompetencyJol competencyJol -> executeCourseChatPipeline(variant, session, competencyJol, CompetencyJolDTO.class);
            case Exercise exercise -> executeCourseChatPipeline(variant, session, exercise, PyrisExerciseWithStudentSubmissionsDTO.class);
            default -> throw new UnsupportedOperationException("Unsupported Pyris event payload type: " + object);
        }
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

    /**
     * Generate a DTO from an object type by invoking the 'of' method of the DTO class.
     * The 'of' method must be a static method that accepts the object type as argument and returns a subclass of PyrisEventDTO.
     * <p>
     * This method is used to generate DTOs from object types that are not known at compile time.
     * It is used to generate DTOs from Pyris event objects that are passed to the chat pipeline.
     * The DTO classes must have a static 'of' method that accepts the object type as argument.
     * The return type of the 'of' method must be a subclass of PyrisEventDTO.
     * </p>
     *
     * @param dtoClass
     * @param object
     * @param <T>
     * @param <U>
     * @return
     */
    private <T, U extends PyrisEventDTO> U generateDTOFromObjectType(Class<U> dtoClass, T object) {
        // Get the 'of' method from the DTO class
        Method ofMethod = null;
        Class<?> currentClass = object.getClass();

        // Traverse up the class hierarchy
        while (currentClass != null && ofMethod == null) {
            for (Method method : dtoClass.getMethods()) {
                if (method.getName().equals("of") && method.getParameterCount() == 1) {
                    if (method.getParameters()[0].getType().isAssignableFrom(currentClass)) {
                        ofMethod = method;
                        break;
                    }
                }
            }
            currentClass = currentClass.getSuperclass();
        }

        if (ofMethod == null) {
            throw new UnsupportedOperationException("Failed to find suitable 'of' method in " + dtoClass.getSimpleName() + " for " + object.getClass().getSimpleName());
        }

        // Check if the return type of 'of' method is a subclass of the PyrisEventDTO class
        if (!PyrisEventDTO.class.isAssignableFrom(ofMethod.getReturnType())) {
            throw new UnsupportedOperationException("The return type of the 'of' method must be a subclass of PyrisEventDTO");
        }

        // Invoke the 'of' method with the object as argument
        try {
            Object result = ofMethod.invoke(null, object);
            return dtoClass.cast(result);
        }
        catch (IllegalArgumentException e) {
            throw new UnsupportedOperationException("The 'of' method's parameter type doesn't match the provided object", e);
        }
        catch (IllegalAccessException e) {
            throw new UnsupportedOperationException("The 'of' method is not accessible", e);
        }
        catch (InvocationTargetException e) {
            throw new UnsupportedOperationException("The 'of' method threw an exception", e.getCause());
        }
        catch (ClassCastException e) {
            throw new UnsupportedOperationException("The 'of' method's return type is not compatible with " + dtoClass.getSimpleName(), e);
        }
    }
}
