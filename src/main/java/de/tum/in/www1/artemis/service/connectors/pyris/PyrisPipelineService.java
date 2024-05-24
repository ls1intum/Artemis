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
import de.tum.in.www1.artemis.domain.iris.session.IrisTutorChatSession;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.StudentParticipationRepository;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.PyrisPipelineExecutionSettingsDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.chat.PyrisChatPipelineExecutionBaseDataDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.chat.course.PyrisCourseChatPipelineExecutionDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.chat.tutor.PyrisTutorChatPipelineExecutionDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.data.PyrisCourseDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.data.PyrisUserDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.status.PyrisStageDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.status.PyrisStageStateDTO;
import de.tum.in.www1.artemis.service.iris.exception.IrisException;
import de.tum.in.www1.artemis.service.iris.websocket.IrisChatWebsocketService;
import de.tum.in.www1.artemis.service.metrics.MetricsService;

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

    private final MetricsService metricsService;

    @Value("${server.url}")
    private String artemisBaseUrl;

    public PyrisPipelineService(PyrisConnectorService pyrisConnectorService, PyrisJobService pyrisJobService, PyrisDTOService pyrisDTOService,
            IrisChatWebsocketService irisChatWebsocketService, CourseRepository courseRepository, MetricsService metricsService,
            StudentParticipationRepository studentParticipationRepository) {
        this.pyrisConnectorService = pyrisConnectorService;
        this.pyrisJobService = pyrisJobService;
        this.pyrisDTOService = pyrisDTOService;
        this.irisChatWebsocketService = irisChatWebsocketService;
        this.courseRepository = courseRepository;
        this.metricsService = metricsService;
        this.studentParticipationRepository = studentParticipationRepository;
    }

    private <T extends IrisChatSession, U> void executeChatPipeline(String variant, T session, String pipelineName,
            Function<PyrisChatPipelineExecutionBaseDataDTO, U> executionDtoSupplier, Supplier<String> jobTokenSupplier) {
        var jobToken = jobTokenSupplier.get();
        var settingsDTO = new PyrisPipelineExecutionSettingsDTO(jobToken, List.of(), artemisBaseUrl);

        var preparingRequestStageInProgress = new PyrisStageDTO("Preparing", 10, PyrisStageStateDTO.IN_PROGRESS, null);
        var preparingRequestStageDone = new PyrisStageDTO("Preparing", 10, PyrisStageStateDTO.DONE, null);
        var executingPipelineStageNotStarted = new PyrisStageDTO("Executing pipeline", 30, PyrisStageStateDTO.NOT_STARTED, null);
        var executingPipelineStageInProgress = new PyrisStageDTO("Executing pipeline", 30, PyrisStageStateDTO.IN_PROGRESS, null);
        irisChatWebsocketService.sendStatusUpdate(session, List.of(preparingRequestStageInProgress, executingPipelineStageNotStarted));

        try {
            var base = new PyrisChatPipelineExecutionBaseDataDTO(pyrisDTOService.toPyrisMessageDTOList(session.getMessages()), new PyrisUserDTO(session.getUser()), settingsDTO,
                    List.of(preparingRequestStageDone));

            U executionDTO = executionDtoSupplier.apply(base);

            irisChatWebsocketService.sendStatusUpdate(session, List.of(preparingRequestStageDone, executingPipelineStageInProgress));

            try {
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

    public void executeTutorChatPipeline(String variant, Optional<ProgrammingSubmission> latestSubmission, ProgrammingExercise exercise, IrisTutorChatSession session) {
        executeChatPipeline(variant, session, "tutor-chat",
                base -> new PyrisTutorChatPipelineExecutionDTO(base, latestSubmission.map(pyrisDTOService::toPyrisSubmissionDTO).orElse(null),
                        pyrisDTOService.toPyrisProgrammingExerciseDTO(exercise), new PyrisCourseDTO(exercise.getCourseViaExerciseGroupOrCourseMember())),
                () -> pyrisJobService.addTutorChatJob(exercise.getCourseViaExerciseGroupOrCourseMember().getId(), exercise.getId(), session.getId()));
    }

    public void executeCourseChatPipeline(String variant, IrisCourseChatSession session) {
        var courseId = session.getCourse().getId();
        var studentId = session.getUser().getId();
        executeChatPipeline(variant, session, "course-chat", base -> {
            var fullCourse = loadCourseWithParticipationOfStudent(courseId, studentId);
            var metrics = metricsService.getStudentCourseMetrics(session.getUser().getId(), courseId);
            return new PyrisCourseChatPipelineExecutionDTO(base, pyrisDTOService.toPyrisExtendedCourseDTO(fullCourse), metrics);
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
    private Course loadCourseWithParticipationOfStudent(Long courseId, Long studentId) {
        Course course = courseRepository.findWithEagerExercisesAndLecturesAndLectureUnitsAndCompetenciesAndExamsById(courseId).orElseThrow();
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
