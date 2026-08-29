package de.tum.cit.aet.artemis.iris.service.pyris;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.account.service.UserAiPreferenceService;
import de.tum.cit.aet.artemis.communication.domain.Post;
import de.tum.cit.aet.artemis.core.domain.AiSelectionDecision;
import de.tum.cit.aet.artemis.core.service.feature.Feature;
import de.tum.cit.aet.artemis.core.service.feature.FeatureToggleService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.service.CourseLoadService;
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
import de.tum.cit.aet.artemis.iris.service.pyris.dto.chat.tutorsuggestion.PyrisTutorSuggestionPipelineExecutionDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisCourseDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisLectureDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisPostDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisProgrammingExerciseDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisSubmissionDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisTextExerciseDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisUserDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisRunState;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisStatusErrorDTO;
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

    private final UserAiPreferenceService userAiPreferenceService;

    private final CourseLoadService courseLoadService;

    private final StudentParticipationRepository studentParticipationRepository;

    private final UserRepository userRepository;

    private final FeatureToggleService featureToggleService;

    @Value("${server.url}")
    private String artemisBaseUrl;

    @Value("${artemis.iris.response-streaming-enabled:true}")
    private boolean responseStreamingEnabled;

    public PyrisPipelineService(PyrisConnectorService pyrisConnectorService, PyrisJobService pyrisJobService, PyrisDTOService pyrisDTOService,
            IrisChatWebsocketService irisChatWebsocketService, StudentParticipationRepository studentParticipationRepository, UserRepository userRepository,
            CourseLoadService courseLoadService, FeatureToggleService featureToggleService, UserAiPreferenceService userAiPreferenceService) {
        this.pyrisConnectorService = pyrisConnectorService;
        this.userAiPreferenceService = userAiPreferenceService;
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
     * The status of the pipeline execution is updated via a consumer that accepts run-state frames. This method will
     * call the consumer with the initial running frame of the pipeline execution. Later states will be sent back from Pyris,
     * and need to be handled in the endpoint that receives the status updates.
     * <p>
     *
     * @param name          the name of the pipeline to be executed
     * @param aiSelection   the current AI selection of the user
     * @param variant       the variant of the pipeline
     * @param supportLevel  the instructional support level ("low" / "moderate" / "high")
     * @param event         an optional event variant that can be used to trigger specific event of the given pipeline
     * @param jobToken      a unique job token for tracking the pipeline execution
     * @param dtoMapper     a function to create the concrete DTO type for this pipeline from the base DTO
     * @param statusUpdater a consumer to update the status of the pipeline execution
     */
    public void executePipeline(String name, AiSelectionDecision aiSelection, String variant, String supportLevel, Optional<String> event, String jobToken,
            Function<PyrisPipelineExecutionDTO, Object> dtoMapper, PipelineStatusUpdater statusUpdater) {
        statusUpdater.accept(jobToken, PyrisRunState.RUNNING, null);

        try {
            Boolean streamResponse = responseStreamingEnabled && "chat".equals(name) ? Boolean.TRUE : null;
            var baseDto = new PyrisPipelineExecutionDTO(new PyrisPipelineExecutionSettingsDTO(jobToken, aiSelection, artemisBaseUrl, variant, supportLevel, streamResponse));
            long dtoBuildStart = System.nanoTime();
            var pipelineDto = dtoMapper.apply(baseDto);
            log.info("Pyris {} pipeline DTO built in {} ms", name, (System.nanoTime() - dtoBuildStart) / 1_000_000);

            try {
                // Execute the pipeline using the connector service
                long requestStart = System.nanoTime();
                pyrisConnectorService.executePipeline(name, pipelineDto, event);
                log.debug("Pyris {} pipeline run request accepted in {} ms", name, (System.nanoTime() - requestStart) / 1_000_000);
            }
            catch (PyrisConnectorException | IrisException e) {
                log.error("Failed to execute {} pipeline", name, e);
                statusUpdater.accept(jobToken, PyrisRunState.FAILED, new PyrisStatusErrorDTO("artemisApp.iris.error.internal", null));
            }
        }
        catch (Exception e) {
            log.error("Failed to prepare {} pipeline execution", name, e);
            statusUpdater.accept(jobToken, PyrisRunState.FAILED, new PyrisStatusErrorDTO("artemisApp.iris.error.internal", null));
        }
    }

    /**
     * Execute the chat pipeline for any chat session context.
     * The caller provides a DTO builder lambda that constructs the context-specific {@link PyrisChatPipelineExecutionDTO}.
     *
     * @param variant      the variant of the pipeline
     * @param supportLevel the instructional support level ("low" / "moderate" / "high")
     * @param session      the chat session
     * @param eventVariant the event variant to trigger, if any
     * @param dtoBuilder   a function that receives the base execution DTO, the persisted user and the feature-gated Pyris user DTO
     */
    public void executeChatPipeline(String variant, String supportLevel, IrisChatSession session, Optional<String> eventVariant, ChatPipelineDTOBuilder dtoBuilder) {
        var user = userRepository.findByIdElseThrow(session.getUserId());
        var pyrisUser = toPyrisUserDTO(user);
        var lastMessageId = session.getMessages().isEmpty() ? null : session.getMessages().getLast().getId();
        // @formatter:off
        executePipeline("chat", userAiPreferenceService.findDecision(user.getId()), variant, supportLevel, eventVariant,
            pyrisJobService.addChatJob(session.getCourseId(), session.getId(), session.getEntityId(), lastMessageId),
            executionDto -> dtoBuilder.apply(executionDto, user, pyrisUser),
            (runId, runState, error) -> irisChatWebsocketService.sendStatusUpdate(session, runId, runState, error));
        // @formatter:on
    }

    @FunctionalInterface
    public interface ChatPipelineDTOBuilder {

        PyrisChatPipelineExecutionDTO apply(PyrisPipelineExecutionDTO executionDto, User user, PyrisUserDTO pyrisUser);
    }

    /**
     * Execute the tutor suggestion pipeline for the given session.
     * It provides specific data for the tutor suggestion pipeline, including:
     * - The post the session is about
     * - The messages of the session
     * - The user that created the session
     *
     * @param variant                the variant of the pipeline
     * @param supportLevel           the instructional support level ("low" / "moderate" / "high"), sent for consistency with the other pipelines; whether the
     *                                   tutor-suggestion pipeline acts on it is determined by Pyris
     * @param session                the chat session
     * @param eventVariant           the event variant if this function triggers a pipeline execution due to a specific event
     * @param lectureId              the optional lecture ID if this is due to a specific event
     * @param textExerciseDTO        the optional text exercise DTO if this is due to a specific event
     * @param submissionDTO          the optional submission DTO if this is due to a specific event
     * @param programmingExerciseDTO the optional programming exercise DTO if this is due to a specific event
     * @param post                   the post the session is about
     */
    public void executeTutorSuggestionPipeline(String variant, String supportLevel, IrisTutorSuggestionSession session, Optional<String> eventVariant, Optional<Long> lectureId,
            Optional<PyrisTextExerciseDTO> textExerciseDTO, Optional<PyrisSubmissionDTO> submissionDTO, Optional<PyrisProgrammingExerciseDTO> programmingExerciseDTO, Post post) {
        var course = post.getCoursePostingBelongsTo();
        if (course == null) {
            throw new IllegalStateException("Course not found for post " + post.getId());
        }
        var user = userRepository.findByIdElseThrow(session.getUserId());
        // @formatter:off
        executePipeline(
            "tutor-suggestion",
            userAiPreferenceService.findDecision(user.getId()),
            variant,
            supportLevel,
            eventVariant,
            pyrisJobService.addTutorSuggestionJob(post.getId(), course.getId(), session.getId()),
            executionDto -> new PyrisTutorSuggestionPipelineExecutionDTO(
                new PyrisCourseDTO(course),
                // No roles: the tutor suggestion prompt addresses a tutor about a thread, not the thread's participants.
                new PyrisPostDTO(post, Map.of(), userAiPreferenceService.findDecisions(PyrisPostDTO.answerAuthorIds(post))),
                pyrisDTOService.toPyrisMessageDTOList(session.getMessages()),
                toPyrisUserDTO(user),
                executionDto.settings(),
                textExerciseDTO,
                submissionDTO,
                programmingExerciseDTO,
                lectureId
            ),
            (runId, runState, error) -> irisChatWebsocketService.sendStatusUpdate(session, runId, runState, error)
        );
        // @formatter:on
    }

    private PyrisUserDTO toPyrisUserDTO(User user) {
        return new PyrisUserDTO(user, featureToggleService.isFeatureEnabled(Feature.Memiris) && userAiPreferenceService.isMemirisEnabled(user.getId()));
    }

    /**
     * Execute the autonomous tutor pipeline to respond to a student's post.
     * Unlike session-based pipelines, this is a one-shot operation that generates a response
     * and either posts it directly or discards it based on confidence.
     *
     * @param variant                the variant of the pipeline
     * @param supportLevel           the instructional support level ("low" / "moderate" / "high")
     * @param aiSelection            the current AI selection of the user
     * @param post                   the student's post to respond to
     * @param course                 the course the post belongs to
     * @param student                the student who created the post
     * @param programmingExerciseDTO optional programming exercise if the channel is linked to one
     * @param textExerciseDTO        optional text exercise if the channel is linked to one
     * @param lectureDTO             optional lecture if the channel is linked to one
     * @param statusUpdateConsumer   consumer to handle status updates (e.g., for logging or future websocket support)
     */
    public void executeAutonomousTutorPipeline(String variant, String supportLevel, AiSelectionDecision aiSelection, PyrisPostDTO post, Course course, PyrisUserDTO student,
            PyrisProgrammingExerciseDTO programmingExerciseDTO, PyrisTextExerciseDTO textExerciseDTO, PyrisLectureDTO lectureDTO, PipelineStatusUpdater statusUpdateConsumer) {
        // @formatter:off
        executePipeline(
            "autonomous-tutor",
            aiSelection,
            variant,
            supportLevel,
            Optional.empty(),
            pyrisJobService.addAutonomousTutorJob(post.id(), course.getId()),
            executionDto -> new PyrisAutonomousTutorPipelineExecutionDTO(
                new PyrisCourseDTO(course),
                post,
                student,
                executionDto.settings(),
                programmingExerciseDTO,
                textExerciseDTO,
                lectureDTO
            ),
            statusUpdateConsumer
        );
        // @formatter:on
    }

    @FunctionalInterface
    public interface PipelineStatusUpdater {

        void accept(String runId, PyrisRunState runState, PyrisStatusErrorDTO error);
    }

    /**
     * Load the course with the participation of the student and set the participations on the exercises.
     * <p>
     * Spring Boot 3 does not support conditional left joins, so we have to load the participations separately.
     *
     * @param courseId  the id of the course
     * @param studentId the id of the student
     * @return the course with exercises, lectures, and student participations loaded
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
}
