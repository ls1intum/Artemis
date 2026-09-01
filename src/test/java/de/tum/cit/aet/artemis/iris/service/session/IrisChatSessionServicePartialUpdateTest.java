package de.tum.cit.aet.artemis.iris.service.session;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;
import org.springframework.transaction.PlatformTransactionManager;

import de.tum.cit.aet.artemis.account.service.UserAiPreferenceService;
import de.tum.cit.aet.artemis.admin.service.LLMTokenUsageService;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;
import de.tum.cit.aet.artemis.course.repository.CourseRepository;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.exercise.repository.SubmissionRepository;
import de.tum.cit.aet.artemis.iris.domain.session.IrisChatSession;
import de.tum.cit.aet.artemis.iris.repository.IrisChatSessionRepository;
import de.tum.cit.aet.artemis.iris.repository.IrisMessageRepository;
import de.tum.cit.aet.artemis.iris.repository.IrisSessionRepository;
import de.tum.cit.aet.artemis.iris.service.IrisCitationService;
import de.tum.cit.aet.artemis.iris.service.IrisMessageService;
import de.tum.cit.aet.artemis.iris.service.IrisRateLimitService;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisJobService;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.chat.PyrisChatStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisRunState;
import de.tum.cit.aet.artemis.iris.service.pyris.job.ChatJob;
import de.tum.cit.aet.artemis.iris.service.settings.IrisSettingsService;
import de.tum.cit.aet.artemis.iris.service.websocket.IrisChatWebsocketService;
import de.tum.cit.aet.artemis.lecture.api.LectureRepositoryApi;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseStudentParticipationRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingSubmissionRepository;

class IrisChatSessionServicePartialUpdateTest {

    private IrisSessionRepository irisSessionRepository;

    private IrisMessageService irisMessageService;

    private IrisMessageRepository irisMessageRepository;

    private IrisChatWebsocketService irisChatWebsocketService;

    private IrisChatSessionService irisChatSessionService;

    @BeforeEach
    void setUp() {
        irisSessionRepository = mock(IrisSessionRepository.class);
        irisMessageService = mock(IrisMessageService.class);
        irisMessageRepository = mock(IrisMessageRepository.class);
        irisChatWebsocketService = mock(IrisChatWebsocketService.class);

        irisChatSessionService = new IrisChatSessionService(irisMessageService, irisMessageRepository, mock(LLMTokenUsageService.class), mock(IrisSettingsService.class),
                irisChatWebsocketService, mock(AuthorizationCheckService.class), irisSessionRepository, mock(IrisChatSessionRepository.class),
                mock(ProgrammingExerciseStudentParticipationRepository.class), mock(ProgrammingSubmissionRepository.class), mock(IrisRateLimitService.class),
                JsonObjectMapper.get(), mock(ExerciseRepository.class), mock(SubmissionRepository.class), mock(CourseRepository.class), Optional.<LectureRepositoryApi>empty(),
                mock(IrisCitationService.class), mock(MessageSource.class), mock(IrisChatPipelineExecutionService.class), mock(PyrisJobService.class),
                mock(UserAiPreferenceService.class), mock(PlatformTransactionManager.class), true);
    }

    @Test
    void partialStatusUpdateRelaysDraftWithoutPersistingMessage() {
        var job = new ChatJob("run-1", 1L, 2L, 3L, null, null, null);
        var statusUpdate = new PyrisChatStatusUpdateDTO(null, PyrisRunState.RUNNING, null, null, null, null, null, null, "partial", 4, null, null);
        var session = new IrisChatSession();
        session.setId(2L);
        session.setUserId(5L);
        when(irisSessionRepository.findByIdElseThrow(2L)).thenReturn(session);

        irisChatSessionService.handlePartialStatusUpdate(job, statusUpdate);

        verify(irisSessionRepository).findByIdElseThrow(2L);
        verify(irisChatWebsocketService).sendPartialUpdate(session, "partial", 4, "run-1");
        verifyNoInteractions(irisMessageService, irisMessageRepository);
    }
}
