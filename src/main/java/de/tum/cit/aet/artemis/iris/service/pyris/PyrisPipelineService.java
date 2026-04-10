package de.tum.cit.aet.artemis.iris.service.pyris;

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
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.communication.dto.PostDTO;
import de.tum.cit.aet.artemis.core.domain.AiSelectionDecision;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.service.course.CourseLoadService;
import de.tum.cit.aet.artemis.core.service.feature.Feature;
import de.tum.cit.aet.artemis.core.service.feature.FeatureToggleService;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.repository.StudentParticipationRepository;
import de.tum.cit.aet.artemis.iris.config.IrisEnabled;
import de.tum.cit.aet.artemis.iris.domain.session.IrisChatSession;
import de.tum.cit.aet.artemis.iris.domain.session.IrisTutorSuggestionSession;
import de.tum.cit.aet.artemis.iris.exception.IrisException;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.PyrisPipelineExecutionDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.PyrisPipelineExecutionSettingsDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.autonomoustutor.PyrisAutonomousTutorPipelineExecutionDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.chat.PyrisChatPipelineExecutionDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.chat.PyrisEventDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.chat.tutorsuggestion.PyrisTutorSuggestionPipelineExecutionDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisCourseDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisPostDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisProgrammingExerciseDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisSubmissionDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisTextExerciseDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisUserDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisStageDTO;
import de.tum.cit.aet.artemis.iris.service.websocket.IrisChatWebsocketService;

/**
 * Service responsible for executing the various Pyris pipelines in a type-safe manner.
 * Uses {@link PyrisConnectorService} to execute the pipelines and {@link PyrisJobService} to manage the jobs.
 */
@Lazy
@Service
@Conditional(IrisEnabled.class)
public class PyrisPipelineService {

    private static final Logger log = LoggerFactory.getLogger(PyrisPipelineService.class);

    private final PyrisConnectorService pyrisConnectorService;

    private final PyrisJobService pyrisJobService;

    private final PyrisDTOService pyrisDTOService;

    private final IrisChatWebsocketService irisChatWebsocketService;

    private final CourseLoadService courseLoadService;

    private final StudentParticipationRepository studentParticipationRepository;

    private final UserRepository userRepository;

    private final FeatureToggleService featureToggleService;

    @Value("${server.url}")
    private String artemisBaseUrl;

    public PyrisPipelineService(PyrisConnectorService pyrisConnectorService, PyrisJobService pyrisJobService, PyrisDTOService pyrisDTOService,
            IrisChatWebsocketService irisChatWebsocketService, StudentParticipationRepository studentParticipationRepository, UserRepository userRepository,
            CourseLoadService courseLoadService, FeatureToggleService featureToggleService) {
        this.pyrisConnectorService = pyrisConnectorService;
        this.pyrisJobService = pyrisJobService;
        this.pyrisDTOService = pyrisDTOService;
        this.irisChatWebsocketService = irisChatWebsocketService;
        this.studentParticipationRepository = studentParticipationRepository;
        this.userRepository = userRepository;
        this.courseLoadService = courseLoadService;
        this.featureToggleService = featureToggleService;
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
     * @param aiSelection   the current AI selection of the user
     * @param variant       the variant of the pipeline
     * @param event         an optional event variant that can be used to trigger specific event of the given pipeline
     * @param jobToken      a unique job token for tracking the pipeline execution
     * @param dtoMapper     a function to create the concrete DTO type for this pipeline from the base DTO
     * @param statusUpdater a consumer to update the status of the pipeline execution
     */
    public void executePipeline(String name, AiSelectionDecision aiSelection, String variant, Optional<String> event, String jobToken,
            Function<PyrisPipelineExecutionDTO, Object> dtoMapper, Consumer<List<PyrisStageDTO>> statusUpdater) {
        // Define the preparation stages of pipeline execution with their initial states
        // There will be more stages added in Pyris later
        var preparing = new PyrisStageDTO("artemisApp.iris.stages.thinking", 10, null, null, false, null);
        var executing = new PyrisStageDTO("artemisApp.iris.stages.analyzing", 30, null, null, false, null);

        // Send initial status update indicating that the preparation stage is in progress
        statusUpdater.accept(List.of(preparing.inProgress(), executing.notStarted()));

        var baseDto = new PyrisPipelineExecutionDTO(new PyrisPipelineExecutionSettingsDTO(jobToken, aiSelection, artemisBaseUrl, variant), List.of(preparing.done()));
        var pipelineDto = dtoMapper.apply(baseDto);

        try {
            // Send a status update that preparation is done and pipeline execution is starting
            statusUpdater.accept(List.of(preparing.done(), executing.inProgress()));

            try {
                // Execute the pipeline using the connector service
                pyrisConnectorService.executePipeline(name, pipelineDto, event);
            }
            catch (PyrisConnectorException | IrisException e) {
                log.error("Failed to execute {} pipeline", name, e);
                statusUpdater.accept(List.of(preparing.done(), executing.error("artemisApp.iris.stages.error.internal")));
            }
        }
        catch (Exception e) {
            log.error("Failed to prepare {} pipeline execution", name, e);
            statusUpdater.accept(List.of(preparing.error("artemisApp.iris.stages.error.internal"), executing.notStarted()));
        }
    }

    /**
     * Execute the chat pipeline for any chat session context.
     * The caller provides a DTO builder lambda that constructs the context-specific {@link PyrisChatPipelineExecutionDTO}.
     *
     * @param variant      the variant of the pipeline
     * @param session      the chat session
     * @param eventVariant the event variant to trigger, if any
     * @param dtoBuilder   a function that receives the base execution DTO and returns the fully constructed chat pipeline DTO
     */
    public void executeChatPipeline(String variant, IrisChatSession session, Optional<String> eventVariant,
            Function<PyrisPipelineExecutionDTO, PyrisChatPipelineExecutionDTO> dtoBuilder) {
        var user = userRepository.findByIdElseThrow(session.getUserId());
        if (!featureToggleService.isFeatureEnabled(Feature.Memiris)) {
            user.setMemirisEnabled(false);
        }
        var lastMessageId = session.getMessages().isEmpty() ? null : session.getMessages().getLast().getId();
        // @formatter:off
        executePipeline("chat", user.getSelectedLLMUsage(), variant, eventVariant,
            pyrisJobService.addChatJob(session.getCourseId(), session.getId(), session.getEntityId(), lastMessageId),
            dtoBuilder::apply,
            stages -> irisChatWebsocketService.sendStatusUpdate(session, stages));
        // @formatter:on
    }

    /**
     * Execute the tutor suggestion pipeline for the given session.
     * It provides specific data for the tutor suggestion pipeline, including:
     * - The post the session is about
     * - The messages of the session
     * - The user that created the session
     *
     * @param variant                the variant of the pipeline
     * @param session                the chat session
     * @param eventVariant           the event variant if this function triggers a pipeline execution due to a specific event
     * @param lectureId              the optional lecture ID if this is due to a specific event
     * @param textExerciseDTO        the optional text exercise DTO if this is due to a specific event
     * @param submissionDTO          the optional submission DTO if this is due to a specific event
     * @param programmingExerciseDTO the optional programming exercise DTO if this is due to a specific event
     * @param postDTO                the post DTO containing the post
     */
    public void executeTutorSuggestionPipeline(String variant, IrisTutorSuggestionSession session, Optional<String> eventVariant, Optional<Long> lectureId,
            Optional<PyrisTextExerciseDTO> textExerciseDTO, Optional<PyrisSubmissionDTO> submissionDTO, Optional<PyrisProgrammingExerciseDTO> programmingExerciseDTO,
            PostDTO postDTO) {
        var post = postDTO.post();
        var course = post.getCoursePostingBelongsTo();
        if (course == null) {
            throw new IllegalStateException("Course not found for post " + post.getId());
        }
        var user = userRepository.findByIdElseThrow(session.getUserId());
        // @formatter:off
        executePipeline(
            "tutor-suggestion",
            user.getSelectedLLMUsage(),
            variant,
            eventVariant,
            pyrisJobService.addTutorSuggestionJob(post.getId(), course.getId(), session.getId()),
            executionDto -> new PyrisTutorSuggestionPipelineExecutionDTO(
                new PyrisCourseDTO(course),
                new PyrisPostDTO(post),
                pyrisDTOService.toPyrisMessageDTOList(session.getMessages()),
                new PyrisUserDTO(user),
                executionDto.settings(),
                executionDto.initialStages(),
                textExerciseDTO,
                submissionDTO,
                programmingExerciseDTO,
                lectureId
            ),
            stages -> irisChatWebsocketService.sendStatusUpdate(session, stages)
        );
        // @formatter:on
    }

    /**
     * Execute the autonomous tutor pipeline to respond to a student's post.
     * Unlike session-based pipelines, this is a one-shot operation that generates a response
     * and either posts it directly or discards it based on confidence.
     *
     * @param variant                the variant of the pipeline
     * @param post                   the student's post to respond to
     * @param course                 the course the post belongs to
     * @param student                the student who created the post
     * @param programmingExerciseDTO optional programming exercise if the channel is linked to one
     * @param textExerciseDTO        optional text exercise if the channel is linked to one
     * @param lectureDTO             optional lecture if the channel is linked to one
     * @param statusUpdateConsumer   consumer to handle status updates (e.g., for logging or future websocket support)
     */
    public void executeAutonomousTutorPipeline(String variant, PyrisPostDTO post, Course course, PyrisUserDTO student, PyrisProgrammingExerciseDTO programmingExerciseDTO,
            PyrisTextExerciseDTO textExerciseDTO, PyrisLectureDTO lectureDTO, Consumer<List<PyrisStageDTO>> statusUpdateConsumer) {
        // @formatter:off
        executePipeline(
            "autonomous-tutor",
            null,
            variant,
            Optional.empty(),
            pyrisJobService.addAutonomousTutorJob(post.id(), course.getId()),
            executionDto -> new PyrisAutonomousTutorPipelineExecutionDTO(
                new PyrisCourseDTO(course),
                post,
                student,
                executionDto.settings(),
                executionDto.initialStages(),
                programmingExerciseDTO,
                textExerciseDTO,
                lectureDTO
            ),
            statusUpdateConsumer
        );
        // @formatter:on
    }

    /**
     * Load the course with the participation of the student and set the participations on the exercises.
     * <p>
     * Spring Boot 3 does not support conditional left joins, so we have to load the participations separately.
     *
     * @param courseId  the id of the course
     * @param studentId the id of the student
     */
    public Course loadCourseWithParticipationOfStudent(long courseId, long studentId) {
        Course course = courseLoadService.loadCourseWithExercisesLecturesLectureUnitsCompetenciesPrerequisitesAndExams(courseId);
        List<StudentParticipation> participations = studentParticipationRepository.findByStudentIdAndIndividualExercisesWithEagerLatestSubmissionResultIgnoreTestRuns(studentId,
                course.getExercises());

        Map<Long, Set<StudentParticipation>> participationMap = new HashMap<>();
        for (StudentParticipation participation : participations) {
            Long exerciseId = participation.getExercise().getId();
            participationMap.computeIfAbsent(exerciseId, _ -> new HashSet<>()).add(participation);
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
     * @return PyrisEventDTO<U> the generated DTO
     */
    public <T, U> PyrisEventDTO<U> generateEventPayloadFromObjectType(Class<U> dtoClass, T object) {

        if (object == null) {
            return null;
        }
        // Get the 'of' method from the DTO class
        Method ofMethod = getOfMethod(dtoClass, object);

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

    /**
     * Get the 'of' method from the DTO class that accepts the object type as argument.
     *
     * @param dtoClass the class of the DTO
     * @param object   the object to generate the DTO from
     * @param <T>      the type of the object
     * @param <U>      the type of the DTO
     * @return Method the 'of' method
     */
    public static <T, U> Method getOfMethod(Class<U> dtoClass, T object) {
        Method ofMethod = null;
        Class<?> currentClass = object.getClass();

        // Traverse up the class hierarchy
        while (currentClass != null && ofMethod == null) {
            for (Method method : dtoClass.getMethods()) {
                if (method.getName().equals("of") && method.getParameterCount() == 1 && method.getParameters()[0].getType().isAssignableFrom(currentClass)) {
                    ofMethod = method;
                    break;
                }
            }
            currentClass = currentClass.getSuperclass();
        }

        if (ofMethod == null) {
            throw new UnsupportedOperationException("Failed to find suitable 'of' method in " + dtoClass.getSimpleName() + " for " + object.getClass().getSimpleName());
        }
        return ofMethod;
    }
}
