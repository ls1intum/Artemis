package de.tum.cit.aet.artemis.iris.service.pyris;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.core.domain.AiSelectionDecision;
import de.tum.cit.aet.artemis.core.service.feature.FeatureToggleService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.service.CourseLoadService;
import de.tum.cit.aet.artemis.exercise.repository.StudentParticipationRepository;
import de.tum.cit.aet.artemis.iris.domain.session.IrisChatMode;
import de.tum.cit.aet.artemis.iris.domain.session.IrisChatSession;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisAskUserModeSettings;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.PyrisPipelineExecutionSettingsDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.chat.PyrisChatPipelineExecutionDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.chat.askuser.PyrisAskUserPipelineExecutionDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisProgrammingExerciseDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisSubmissionDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisRunState;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisStatusErrorDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.job.ChatJob;
import de.tum.cit.aet.artemis.iris.service.websocket.IrisChatWebsocketService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;

class PyrisPipelineServiceTest {

    @Test
    void chatPipelineSettingsOmitStreamResponseWhenDisabled() {
        var settings = executeChatPipelineAndCaptureSettings(false);

        assertThat(settings.streamResponse()).isNull();
    }

    @Test
    void chatPipelineSettingsEnableStreamResponseWhenEnabled() {
        var settings = executeChatPipelineAndCaptureSettings(true);

        assertThat(settings.streamResponse()).isTrue();
    }

    @Test
    void executeChatPipelineSendsInitialRunningFrameWithRunId() {
        var pyrisConnectorService = mock(PyrisConnectorService.class);
        var pyrisJobService = mock(PyrisJobService.class);
        var userRepository = mock(UserRepository.class);
        var irisChatWebsocketService = mock(IrisChatWebsocketService.class);
        var user = new User();
        user.setId(7L);
        user.setLogin("student");
        user.setSelectedLLMUsage(AiSelectionDecision.CLOUD_AI);
        when(userRepository.findByIdElseThrow(7L)).thenReturn(user);
        when(pyrisJobService.addChatJob(1L, 2L, 3L, null)).thenReturn("run-1");

        var service = new PyrisPipelineService(pyrisConnectorService, pyrisJobService, mock(PyrisDTOService.class), irisChatWebsocketService,
                mock(StudentParticipationRepository.class), userRepository, mock(CourseLoadService.class), mock(FeatureToggleService.class));
        ReflectionTestUtils.setField(service, "artemisBaseUrl", "https://artemis.example");

        var session = new IrisChatSession();
        session.setCourseId(1L);
        session.setId(2L);
        session.setEntityId(3L);
        session.setUserId(7L);

        service.executeChatPipeline("default", "moderate", session, Optional.empty(), (executionDto, ignoredUser, ignoredPyrisUser) -> new PyrisChatPipelineExecutionDTO(null,
                List.of(), executionDto.settings(), null, ignoredPyrisUser, null, null, null, null, null, null, null, null, null));

        verify(irisChatWebsocketService).sendStatusUpdate(eq(session), eq("run-1"), eq(PyrisRunState.RUNNING), isNull());
    }

    @Test
    void executePipelineReportsLiveErrorKeyWhenConnectorFails() {
        var pyrisConnectorService = mock(PyrisConnectorService.class);
        var pyrisJobService = mock(PyrisJobService.class);
        var userRepository = mock(UserRepository.class);

        var service = new PyrisPipelineService(pyrisConnectorService, pyrisJobService, mock(PyrisDTOService.class), mock(IrisChatWebsocketService.class),
                mock(StudentParticipationRepository.class), userRepository, mock(CourseLoadService.class), mock(FeatureToggleService.class));
        ReflectionTestUtils.setField(service, "artemisBaseUrl", "https://artemis.example");

        doThrow(new PyrisConnectorException("boom")).when(pyrisConnectorService).executePipeline(eq("chat"), any(), any());

        var capturedError = new AtomicReference<PyrisStatusErrorDTO>();
        PyrisPipelineService.PipelineStatusUpdater updater = (runId, runState, error) -> {
            if (runState == PyrisRunState.FAILED) {
                capturedError.set(error);
            }
        };

        var success = service.executePipeline("chat", AiSelectionDecision.CLOUD_AI, "default", "moderate", Optional.empty(), "job-1", dto -> dto, updater);

        // The failed run-state callback must reference the translation key the client still ships, not the removed stage key.
        assertThat(success).isFalse();
        assertThat(capturedError.get()).isNotNull();
        assertThat(capturedError.get().message()).isEqualTo("artemisApp.iris.error.internal");
        verify(pyrisJobService).getJob("job-1");
    }

    @Test
    void executeAskUserPipelineSendsAskUserContractExpectedByPyris() throws Exception {
        var pyrisConnectorService = mock(PyrisConnectorService.class);
        var pyrisJobService = mock(PyrisJobService.class);
        var pyrisDTOService = mock(PyrisDTOService.class);
        var userRepository = mock(UserRepository.class);

        var user = new User();
        user.setId(7L);
        user.setLogin("student");
        user.setSelectedLLMUsage(AiSelectionDecision.CLOUD_AI);
        when(userRepository.findByIdElseThrow(7L)).thenReturn(user);
        when(pyrisJobService.addAskUserChatJob(1L, 2L, 3L, null)).thenReturn("run-1");

        var course = new Course();
        course.setId(1L);
        var exercise = new ProgrammingExercise();
        exercise.setId(3L);
        exercise.setCourse(course);
        var submission = new ProgrammingSubmission();
        submission.setId(4L);

        var exerciseDTO = new PyrisProgrammingExerciseDTO(3L, "Exercise", null, Map.of(), Map.of(), Map.of(), "Task", null, null);
        var submissionDTO = new PyrisSubmissionDTO(4L, null, Map.of(), false, false, List.of(), null);
        when(pyrisDTOService.toPyrisProgrammingExerciseDTOWithoutSolutionAndTests(exercise)).thenReturn(exerciseDTO);
        when(pyrisDTOService.toPyrisSubmissionDTO(submission)).thenReturn(submissionDTO);

        var service = new PyrisPipelineService(pyrisConnectorService, pyrisJobService, pyrisDTOService, mock(IrisChatWebsocketService.class),
                mock(StudentParticipationRepository.class), userRepository, mock(CourseLoadService.class), mock(FeatureToggleService.class));
        ReflectionTestUtils.setField(service, "artemisBaseUrl", "https://artemis.example");

        var session = new IrisChatSession();
        session.setCourseId(1L);
        session.setId(2L);
        session.setEntityId(3L);
        session.setUserId(7L);

        var success = service.executeAskUserPipeline("default", submission, exercise, session, Optional.of("BUILD_WITH_POINTS"), IrisAskUserModeSettings.defaultSettings());

        var dtoCaptor = ArgumentCaptor.forClass(Object.class);
        verify(pyrisConnectorService).executePipeline(eq("ask-user"), dtoCaptor.capture(), eq(Optional.of("BUILD_WITH_POINTS")));

        assertThat(success).isTrue();
        assertThat(dtoCaptor.getValue()).isInstanceOf(PyrisAskUserPipelineExecutionDTO.class);
        var dto = (PyrisAskUserPipelineExecutionDTO) dtoCaptor.getValue();
        assertThat(dto.chatMode()).isEqualTo(IrisChatMode.PROGRAMMING_EXERCISE_CHAT);
        assertThat(dto.programmingExercise()).isSameAs(exerciseDTO);
        assertThat(dto.programmingExerciseSubmission()).isSameAs(submissionDTO);

        var json = new ObjectMapper().writeValueAsString(dto);
        assertThat(json).contains("\"chatMode\":\"PROGRAMMING_EXERCISE_CHAT\"", "\"programmingExercise\":", "\"programmingExerciseSubmission\":");
    }

    @Test
    void executeAskUserPipelineReportsQuizFailureKeyWhenConnectorFailsDuringActiveQuiz() {
        var pyrisConnectorService = mock(PyrisConnectorService.class);
        var pyrisJobService = mock(PyrisJobService.class);
        var userRepository = mock(UserRepository.class);
        var irisChatWebsocketService = mock(IrisChatWebsocketService.class);

        var user = new User();
        user.setId(7L);
        user.setLogin("student");
        user.setSelectedLLMUsage(AiSelectionDecision.CLOUD_AI);
        when(userRepository.findByIdElseThrow(7L)).thenReturn(user);
        when(pyrisJobService.addAskUserChatJob(1L, 2L, 3L, null)).thenReturn("run-1");
        var job = new ChatJob("run-1", 1L, 2L, 3L, null, null, null, ChatJob.ASK_USER_PIPELINE_NAME);
        when(pyrisJobService.getJob("run-1")).thenReturn(job);

        var course = new Course();
        course.setId(1L);
        var exercise = new ProgrammingExercise();
        exercise.setId(3L);
        exercise.setCourse(course);
        var submission = new ProgrammingSubmission();
        submission.setId(4L);

        var service = new PyrisPipelineService(pyrisConnectorService, pyrisJobService, mock(PyrisDTOService.class), irisChatWebsocketService,
                mock(StudentParticipationRepository.class), userRepository, mock(CourseLoadService.class), mock(FeatureToggleService.class));
        ReflectionTestUtils.setField(service, "artemisBaseUrl", "https://artemis.example");

        doThrow(new PyrisConnectorException("boom")).when(pyrisConnectorService).executePipeline(eq("ask-user"), any(), any());

        var session = session(1L, 2L, 3L, 7L);
        session.setInAskUserModePipeline(true);

        var success = service.executeAskUserPipeline("default", submission, exercise, session, Optional.empty(), IrisAskUserModeSettings.defaultSettings());

        var errorCaptor = ArgumentCaptor.forClass(PyrisStatusErrorDTO.class);
        assertThat(success).isFalse();
        verify(irisChatWebsocketService).sendStatusUpdate(any(), eq("run-1"), eq(PyrisRunState.FAILED), errorCaptor.capture(), isNull());
        assertThat(errorCaptor.getValue().message()).isEqualTo("artemisApp.exerciseChatbot.errors.askUserQuizFailed");
        verify(pyrisJobService).removeJob(job);
    }

    private PyrisPipelineExecutionSettingsDTO executeChatPipelineAndCaptureSettings(boolean responseStreamingEnabled) {
        var pyrisConnectorService = mock(PyrisConnectorService.class);
        var pyrisJobService = mock(PyrisJobService.class);
        var userRepository = mock(UserRepository.class);
        var user = new User();
        user.setId(7L);
        user.setLogin("student");
        user.setSelectedLLMUsage(AiSelectionDecision.CLOUD_AI);
        when(userRepository.findByIdElseThrow(7L)).thenReturn(user);
        when(pyrisJobService.addChatJob(1L, 2L, 3L, null)).thenReturn("run-1");

        var service = new PyrisPipelineService(pyrisConnectorService, pyrisJobService, mock(PyrisDTOService.class), mock(IrisChatWebsocketService.class),
                mock(StudentParticipationRepository.class), userRepository, mock(CourseLoadService.class), mock(FeatureToggleService.class));
        ReflectionTestUtils.setField(service, "artemisBaseUrl", "https://artemis.example");
        ReflectionTestUtils.setField(service, "responseStreamingEnabled", responseStreamingEnabled);

        var session = new IrisChatSession();
        session.setCourseId(1L);
        session.setId(2L);
        session.setEntityId(3L);
        session.setUserId(7L);

        var capturedSettings = new AtomicReference<PyrisPipelineExecutionSettingsDTO>();
        service.executeChatPipeline("default", "moderate", session, Optional.empty(), (executionDto, ignoredUser, ignoredPyrisUser) -> {
            capturedSettings.set(executionDto.settings());
            return new PyrisChatPipelineExecutionDTO(null, List.of(), executionDto.settings(), null, ignoredPyrisUser, null, null, null, null, null, null, null, null, null);
        });

        return capturedSettings.get();
    }

    private IrisChatSession session(long courseId, long sessionId, long entityId, long userId) {
        var session = new IrisChatSession();
        session.setCourseId(courseId);
        session.setId(sessionId);
        session.setEntityId(entityId);
        session.setUserId(userId);
        return session;
    }
}
