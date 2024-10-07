package de.tum.cit.aet.artemis.iris.service.pyris;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_IRIS;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyJol;
import de.tum.cit.aet.artemis.atlas.dto.CompetencyJolDTO;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.repository.StudentParticipationRepository;
import de.tum.cit.aet.artemis.exercise.service.LearningMetricsService;
import de.tum.cit.aet.artemis.iris.domain.session.IrisCourseChatSession;
import de.tum.cit.aet.artemis.iris.domain.session.IrisExerciseChatSession;
import de.tum.cit.aet.artemis.iris.exception.IrisException;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.PyrisPipelineExecutionDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.PyrisPipelineExecutionSettingsDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.chat.PyrisEventDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.chat.course.PyrisCourseChatPipelineExecutionDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.chat.exercise.PyrisExerciseChatPipelineExecutionDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisCourseDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisExerciseWithStudentSubmissionsDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisExtendedCourseDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisUserDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisStageDTO;
import de.tum.cit.aet.artemis.iris.service.websocket.IrisChatWebsocketService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;

/**
 * Service responsible for executing the various Pyris pipelines in a type-safe manner.
 * Uses {@link PyrisConnectorService} to execute the pipelines and {@link PyrisJobService} to manage the jobs.
 */
@Service
@Profile(PROFILE_IRIS)
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
     * Executes a pipeline on Pyris, identified by the given name and variant.
     * The pipeline execution is tracked by a unique job token, which must be provided by the caller.
     * The caller must additionally provide a mapper function to create the concrete DTO type for this pipeline from the base DTO.
     * The status of the pipeline execution is updated via a consumer that accepts a list of stages. This method will
     * call the consumer with the initial stages of the pipeline execution. Later stages will be sent back from Pyris,
     * and need to be handled in the endpoint that receives the status updates.
     * <p>
     *
     * @param name          the name of the pipeline to be executed
     * @param variant       the variant of the pipeline
     * @param jobToken      a unique job token for tracking the pipeline execution
     * @param dtoMapper     a function to create the concrete DTO type for this pipeline from the base DTO
     * @param statusUpdater a consumer to update the status of the pipeline execution
     */
    public void executePipeline(String name, String variant, String jobToken, Function<PyrisPipelineExecutionDTO, Object> dtoMapper, Consumer<List<PyrisStageDTO>> statusUpdater) {
        // Define the preparation stages of pipeline execution with their initial states
        // There will be more stages added in Pyris later
        var preparing = new PyrisStageDTO("Preparing", 10, null, null);
        var executing = new PyrisStageDTO("Executing pipeline", 30, null, null);

        // Send initial status update indicating that the preparation stage is in progress
        statusUpdater.accept(List.of(preparing.inProgress(), executing.notStarted()));

        var baseDto = new PyrisPipelineExecutionDTO(new PyrisPipelineExecutionSettingsDTO(jobToken, List.of(), artemisBaseUrl), List.of(preparing.done()));
        var pipelineDto = dtoMapper.apply(baseDto);

        try {
            // Send a status update that preparation is done and pipeline execution is starting
            statusUpdater.accept(List.of(preparing.done(), executing.inProgress()));

            try {
                // Execute the pipeline using the connector service
                pyrisConnectorService.executePipeline(name, variant, pipelineDto);
            }
            catch (PyrisConnectorException | IrisException e) {
                log.error("Failed to execute {} pipeline", name, e);
                statusUpdater.accept(List.of(preparing.done(), executing.error("An internal error occurred")));
            }
        }
        catch (Exception e) {
            log.error("Failed to prepare {} pipeline execution", name, e);
            statusUpdater.accept(List.of(preparing.error("An internal error occurred"), executing.notStarted()));
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
     * @see PyrisPipelineService#executePipeline for more details on the pipeline execution process.
     */
    public void executeExerciseChatPipeline(String variant, Optional<ProgrammingSubmission> latestSubmission, ProgrammingExercise exercise, IrisExerciseChatSession session) {
        // @formatter:off
        executePipeline(
                "tutor-chat", // TODO: Rename this to 'exercise-chat' with next breaking Pyris version
                variant,
                pyrisJobService.addExerciseChatJob(exercise.getCourseViaExerciseGroupOrCourseMember().getId(), exercise.getId(), session.getId()),
                executionDto -> {
                    var course = exercise.getCourseViaExerciseGroupOrCourseMember();
                    return new PyrisExerciseChatPipelineExecutionDTO(
                            latestSubmission.map(pyrisDTOService::toPyrisSubmissionDTO).orElse(null),
                            pyrisDTOService.toPyrisProgrammingExerciseDTO(exercise),
                            new PyrisCourseDTO(course),
                            pyrisDTOService.toPyrisMessageDTOList(session.getMessages()),
                            new PyrisUserDTO(session.getUser()),
                            executionDto.settings(),
                            executionDto.initialStages()
                    );
                },
                stages -> irisChatWebsocketService.sendStatusUpdate(session, stages)
        );
        // @formatter:on
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
    private <T, U> void executeCourseChatPipeline(String variant, IrisCourseChatSession session, T eventObject, Class<U> eventDtoClass) {
        var courseId = session.getCourse().getId();
        var studentId = session.getUser().getId();
        executePipeline("course-chat", variant, pyrisJobService.addCourseChatJob(courseId, session.getId()), executionDto -> {
            var fullCourse = loadCourseWithParticipationOfStudent(courseId, studentId);
            return new PyrisCourseChatPipelineExecutionDTO(PyrisExtendedCourseDTO.of(fullCourse),
                    learningMetricsService.getStudentCourseMetrics(session.getUser().getId(), courseId), generateEventPayloadFromObjectType(eventDtoClass, eventObject),
                    pyrisDTOService.toPyrisMessageDTOList(session.getMessages()), new PyrisUserDTO(session.getUser()), executionDto.settings(), // flatten the execution dto here
                    executionDto.initialStages());
        }, stages -> irisChatWebsocketService.sendStatusUpdate(session, stages));
        // @formatter:on
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
     * @see PyrisPipelineService#executePipeline for more details on the pipeline execution process.
     */
    public void executeCourseChatPipeline(String variant, IrisCourseChatSession session, Object object) {
        log.debug("Executing course chat pipeline variant {} with object {}", variant, object);
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
     * Generate an PyrisEventDTO from an object type by invoking the 'of' method of the DTO class.
     * The 'of' method must be a static method that accepts the object type as argument and returns a subclass of PyrisEventDTO.
     * <p>
     * This method is used to generate DTOs from object types that are not known at compile time.
     * It is used to generate DTOs from Pyris event objects that are passed to the chat pipeline.
     * The DTO classes must have a static 'of' method that accepts the object type as argument.
     * The return type of the 'of' method must be a subclass of PyrisEventDTO.
     * </p>
     *
     * @param dtoClass the class of the DTO that should be generated
     * @param object   the object to generate the DTO from
     * @param <T>      the type of the object
     * @param <U>      the type of the DTO
     * @return PyrisEventDTO<U>
     */
    private <T, U> PyrisEventDTO<U> generateEventPayloadFromObjectType(Class<U> dtoClass, T object) {

        if (object == null) {
            return null;
        }
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

        // Invoke the 'of' method with the object as argument
        try {
            Object result = ofMethod.invoke(null, object);
            return new PyrisEventDTO<>(dtoClass.cast(result), object.getClass().getSimpleName());
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
