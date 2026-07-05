package de.tum.cit.aet.artemis.iris.service.pyris;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.core.domain.AiSelectionDecision;
import de.tum.cit.aet.artemis.core.service.feature.FeatureToggleService;
import de.tum.cit.aet.artemis.course.service.CourseLoadService;
import de.tum.cit.aet.artemis.exercise.repository.StudentParticipationRepository;
import de.tum.cit.aet.artemis.iris.domain.session.IrisChatSession;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.PyrisPipelineExecutionSettingsDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.chat.PyrisChatPipelineExecutionDTO;
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
            return new PyrisChatPipelineExecutionDTO(null, List.of(), executionDto.settings(), null, ignoredPyrisUser, executionDto.initialStages(), null, null, null, null, null,
                    null, null, null, null, null);
        });

        return capturedSettings.get();
    }
}
