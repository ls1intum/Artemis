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
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.account.service.UserAiPreferenceService;
import de.tum.cit.aet.artemis.core.domain.AiSelectionDecision;
import de.tum.cit.aet.artemis.core.service.feature.FeatureToggleService;
import de.tum.cit.aet.artemis.course.service.CourseLoadService;
import de.tum.cit.aet.artemis.exercise.repository.StudentParticipationRepository;
import de.tum.cit.aet.artemis.iris.domain.session.IrisChatSession;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.PyrisPipelineExecutionSettingsDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.chat.PyrisChatPipelineExecutionDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisRunState;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisStatusErrorDTO;
import de.tum.cit.aet.artemis.iris.service.websocket.IrisChatWebsocketService;

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
        when(userRepository.findByIdElseThrow(7L)).thenReturn(user);
        when(pyrisJobService.addChatJob(1L, 2L, 3L, null, null)).thenReturn("run-1");

        var service = new PyrisPipelineService(pyrisConnectorService, pyrisJobService, mock(PyrisDTOService.class), irisChatWebsocketService,
                mock(StudentParticipationRepository.class), userRepository, mock(CourseLoadService.class), mock(FeatureToggleService.class), mock(UserAiPreferenceService.class));
        ReflectionTestUtils.setField(service, "artemisBaseUrl", "https://artemis.example");

        var session = new IrisChatSession();
        session.setCourseId(1L);
        session.setId(2L);
        session.setEntityId(3L);
        session.setUserId(7L);

        service.executeChatPipeline("default", "moderate", session, Optional.empty(), null, (executionDto, ignoredUser, ignoredPyrisUser) -> new PyrisChatPipelineExecutionDTO(null,
                List.of(), executionDto.settings(), null, ignoredPyrisUser, null, null, null, null, null, null, null, null, null));

        verify(irisChatWebsocketService).sendStatusUpdate(eq(session), eq("run-1"), eq(PyrisRunState.RUNNING), isNull());
    }

    @Test
    void executePipelineReportsLiveErrorKeyWhenConnectorFails() {
        var pyrisConnectorService = mock(PyrisConnectorService.class);
        var pyrisJobService = mock(PyrisJobService.class);
        var userRepository = mock(UserRepository.class);

        var service = new PyrisPipelineService(pyrisConnectorService, pyrisJobService, mock(PyrisDTOService.class), mock(IrisChatWebsocketService.class),
                mock(StudentParticipationRepository.class), userRepository, mock(CourseLoadService.class), mock(FeatureToggleService.class), mock(UserAiPreferenceService.class));
        ReflectionTestUtils.setField(service, "artemisBaseUrl", "https://artemis.example");

        doThrow(new PyrisConnectorException("boom")).when(pyrisConnectorService).executePipeline(eq("chat"), any(), any());

        var capturedError = new AtomicReference<PyrisStatusErrorDTO>();
        PyrisPipelineService.PipelineStatusUpdater updater = (runId, runState, error) -> {
            if (runState == PyrisRunState.FAILED) {
                capturedError.set(error);
            }
        };

        service.executePipeline("chat", AiSelectionDecision.CLOUD_AI, "default", "moderate", Optional.empty(), "job-1", dto -> dto, updater);

        // The failed run-state callback must reference the translation key the client still ships, not the removed stage key.
        assertThat(capturedError.get()).isNotNull();
        assertThat(capturedError.get().message()).isEqualTo("artemisApp.iris.error.internal");
    }

    /**
     * The decision reaches Pyris through the settings DTO and decides which model may answer, so the settings have to carry
     * the decision the account actually recorded.
     */
    @Test
    void chatPipelineSettingsCarryTheRecordedDecision() {
        var settings = executeChatPipelineAndCaptureSettings(false, AiSelectionDecision.LOCAL_AI);

        assertThat(settings.selection()).isEqualTo(AiSelectionDecision.LOCAL_AI);
    }

    @Test
    void chatPipelineSettingsCarryNoDecisionWhenTheAccountHasNotDecided() {
        var settings = executeChatPipelineAndCaptureSettings(false, null);

        assertThat(settings.selection()).isNull();
    }

    private PyrisPipelineExecutionSettingsDTO executeChatPipelineAndCaptureSettings(boolean responseStreamingEnabled) {
        return executeChatPipelineAndCaptureSettings(responseStreamingEnabled, AiSelectionDecision.CLOUD_AI);
    }

    private PyrisPipelineExecutionSettingsDTO executeChatPipelineAndCaptureSettings(boolean responseStreamingEnabled, @Nullable AiSelectionDecision recordedDecision) {
        var pyrisConnectorService = mock(PyrisConnectorService.class);
        var pyrisJobService = mock(PyrisJobService.class);
        var userRepository = mock(UserRepository.class);
        var user = new User();
        user.setId(7L);
        user.setLogin("student");
        when(userRepository.findByIdElseThrow(7L)).thenReturn(user);
        when(pyrisJobService.addChatJob(1L, 2L, 3L, null, null)).thenReturn("run-1");
        var userAiPreferenceService = mock(UserAiPreferenceService.class);
        when(userAiPreferenceService.findDecision(7L)).thenReturn(recordedDecision);

        var service = new PyrisPipelineService(pyrisConnectorService, pyrisJobService, mock(PyrisDTOService.class), mock(IrisChatWebsocketService.class),
                mock(StudentParticipationRepository.class), userRepository, mock(CourseLoadService.class), mock(FeatureToggleService.class), userAiPreferenceService);
        ReflectionTestUtils.setField(service, "artemisBaseUrl", "https://artemis.example");
        ReflectionTestUtils.setField(service, "responseStreamingEnabled", responseStreamingEnabled);

        var session = new IrisChatSession();
        session.setCourseId(1L);
        session.setId(2L);
        session.setEntityId(3L);
        session.setUserId(7L);

        var capturedSettings = new AtomicReference<PyrisPipelineExecutionSettingsDTO>();
        service.executeChatPipeline("default", "moderate", session, Optional.empty(), null, (executionDto, ignoredUser, ignoredPyrisUser) -> {
            capturedSettings.set(executionDto.settings());
            return new PyrisChatPipelineExecutionDTO(null, List.of(), executionDto.settings(), null, ignoredPyrisUser, null, null, null, null, null, null, null, null, null);
        });

        return capturedSettings.get();
    }
}
