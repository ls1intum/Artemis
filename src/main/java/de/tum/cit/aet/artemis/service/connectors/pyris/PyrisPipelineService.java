package de.tum.cit.aet.artemis.service.connectors.pyris;

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
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.repository.StudentParticipationRepository;
import de.tum.cit.aet.artemis.iris.domain.session.IrisCourseChatSession;
import de.tum.cit.aet.artemis.iris.domain.session.IrisExerciseChatSession;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.service.connectors.pyris.dto.PyrisPipelineExecutionDTO;
import de.tum.cit.aet.artemis.service.connectors.pyris.dto.PyrisPipelineExecutionSettingsDTO;
import de.tum.cit.aet.artemis.service.connectors.pyris.dto.chat.course.PyrisCourseChatPipelineExecutionDTO;
import de.tum.cit.aet.artemis.service.connectors.pyris.dto.chat.exercise.PyrisExerciseChatPipelineExecutionDTO;
import de.tum.cit.aet.artemis.service.connectors.pyris.dto.data.PyrisCourseDTO;
import de.tum.cit.aet.artemis.service.connectors.pyris.dto.data.PyrisExtendedCourseDTO;
import de.tum.cit.aet.artemis.service.connectors.pyris.dto.data.PyrisUserDTO;
import de.tum.cit.aet.artemis.service.connectors.pyris.dto.status.PyrisStageDTO;
import de.tum.cit.aet.artemis.service.iris.exception.IrisException;
import de.tum.cit.aet.artemis.service.iris.websocket.IrisChatWebsocketService;
import de.tum.cit.aet.artemis.service.metrics.LearningMetricsService;
import de.tum.cit.aet.artemis.web.rest.dto.competency.CompetencyJolDTO;

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
     * - The competency JoL if this is due to a JoL set event
     * <p>
     *
     * @param variant       the variant of the pipeline
     * @param session       the chat session
     * @param competencyJol if this is due to a JoL set event, this must be the newly created competencyJoL
     * @see PyrisPipelineService#executePipeline for more details on the pipeline execution process.
     */
    public void executeCourseChatPipeline(String variant, IrisCourseChatSession session, CompetencyJol competencyJol) {
        // @formatter:off
        var courseId = session.getCourse().getId();
        var studentId = session.getUser().getId();
        executePipeline(
                "course-chat",
                variant,
                pyrisJobService.addCourseChatJob(courseId, session.getId()),
                executionDto -> {
                    var fullCourse = loadCourseWithParticipationOfStudent(courseId, studentId);
                    return new PyrisCourseChatPipelineExecutionDTO(
                            PyrisExtendedCourseDTO.of(fullCourse),
                            learningMetricsService.getStudentCourseMetrics(session.getUser().getId(), courseId),
                            competencyJol == null ? null : CompetencyJolDTO.of(competencyJol),
                            pyrisDTOService.toPyrisMessageDTOList(session.getMessages()),
                            new PyrisUserDTO(session.getUser()),
                            executionDto.settings(), // flatten the execution dto here
                            executionDto.initialStages()
                    );
                },
                stages -> irisChatWebsocketService.sendStatusUpdate(session, stages)
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
