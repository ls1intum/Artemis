package de.tum.cit.aet.artemis.iris.service.pyris;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessageSender;
import de.tum.cit.aet.artemis.iris.domain.session.IrisSession;
import de.tum.cit.aet.artemis.iris.dto.IrisCommandAckDTO;
import de.tum.cit.aet.artemis.iris.repository.IrisSessionRepository;
import de.tum.cit.aet.artemis.iris.service.IrisMessageService;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.chat.PyrisPointOutCommandDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.job.ChatJob;
import de.tum.cit.aet.artemis.iris.service.websocket.IrisChatWebsocketService;
import de.tum.cit.aet.artemis.iris.service.websocket.IrisWebsocketService;
import de.tum.cit.aet.artemis.lecture.api.LectureUnitRepositoryApi;

/**
 * Unit tests for {@link IrisCommandService#executeCommand}. Covers the point-out dispatch: the
 * short-circuit guards, the applied path (client navigated -> success + persisted COMMAND marker),
 * the not-applied path (client did nothing -> no marker), and the timeout/transport path.
 */
@ExtendWith(MockitoExtension.class)
class IrisCommandServiceTest {

    private static final long COURSE_ID = 3L;

    private static final Long SESSION_ID = 5L;

    private static final long USER_ID = 7L;

    private static final long LECTURE_UNIT_ID = 42L;

    @Mock
    private IrisCommandCoordinationService coordinationService;

    @Mock
    private IrisWebsocketService irisWebsocketService;

    @Mock
    private IrisChatWebsocketService irisChatWebsocketService;

    @Mock
    private IrisMessageService irisMessageService;

    @Mock
    private IrisSessionRepository irisSessionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private LectureUnitRepositoryApi lectureUnitRepositoryApi;

    @Mock
    private IrisSession session;

    @Mock
    private PlatformTransactionManager transactionManager;

    private IrisCommandService commandService;

    private ChatJob job;

    @BeforeEach
    void setUp() {
        commandService = new IrisCommandService(coordinationService, irisWebsocketService, irisChatWebsocketService, irisMessageService, irisSessionRepository, userRepository,
                new ObjectMapper(), Optional.of(lectureUnitRepositoryApi), transactionManager);
        job = new ChatJob("job-1", COURSE_ID, SESSION_ID, null, null, null, null);
    }

    private void stubSessionAndUser() {
        when(irisSessionRepository.findByIdElseThrow(SESSION_ID)).thenReturn(session);
        when(session.getId()).thenReturn(SESSION_ID);
        when(session.getUserId()).thenReturn(USER_ID);
        var user = new User();
        user.setLogin("student1");
        when(userRepository.findByIdElseThrow(USER_ID)).thenReturn(user);
    }

    @Test
    void executeCommand_appliedNavigatesPersistsMarkerAndReturnsSuccess() {
        stubSessionAndUser();
        when(coordinationService.register(anyString(), eq("student1"))).thenReturn(CompletableFuture.completedFuture(new IrisCommandAckDTO("corr", true)));
        when(irisMessageService.saveMessage(any(), eq(session), eq(IrisMessageSender.COMMAND))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = commandService.executeCommand(job, new PyrisPointOutCommandDTO(LECTURE_UNIT_ID, 3, null)).join();

        assertThat(result.applied()).isTrue();
        verify(irisWebsocketService).send(eq("student1"), anyString(), any());
        verify(irisMessageService).saveMessage(any(), eq(session), eq(IrisMessageSender.COMMAND));
        verify(irisChatWebsocketService).sendMessage(eq(session), any(), isNull(), isNull());
    }

    @Test
    void executeCommand_notAppliedDoesNotPersistMarker() {
        stubSessionAndUser();
        when(coordinationService.register(anyString(), eq("student1"))).thenReturn(CompletableFuture.completedFuture(new IrisCommandAckDTO("corr", false)));

        var result = commandService.executeCommand(job, new PyrisPointOutCommandDTO(LECTURE_UNIT_ID, 3, null)).join();

        assertThat(result.applied()).isFalse();
        verify(irisWebsocketService).send(eq("student1"), anyString(), any());
        verify(irisMessageService, never()).saveMessage(any(), any(), any());
        verify(irisChatWebsocketService, never()).sendMessage(any(), any(), any(), any());
    }

    @Test
    void executeCommand_timeoutIsReportedAsNotApplied() {
        stubSessionAndUser();
        when(coordinationService.register(anyString(), eq("student1"))).thenReturn(CompletableFuture.failedFuture(new TimeoutException("no ack")));

        var result = commandService.executeCommand(job, new PyrisPointOutCommandDTO(LECTURE_UNIT_ID, 3, null)).join();

        assertThat(result.applied()).isFalse();
        verify(irisMessageService, never()).saveMessage(any(), any(), any());
    }

    @Test
    void executeCommand_missingLectureUnitIdShortCircuitsWithoutContactingClient() {
        var result = commandService.executeCommand(job, new PyrisPointOutCommandDTO(null, 3, null)).join();

        assertThat(result.applied()).isFalse();
        verify(coordinationService, never()).register(anyString(), anyString());
        verify(irisWebsocketService, never()).send(any(), any(), any());
    }

    @Test
    void executeCommand_missingPageAndTimestampShortCircuitsWithoutContactingClient() {
        var result = commandService.executeCommand(job, new PyrisPointOutCommandDTO(LECTURE_UNIT_ID, null, null)).join();

        assertThat(result.applied()).isFalse();
        verify(coordinationService, never()).register(anyString(), anyString());
        verify(irisWebsocketService, never()).send(any(), any(), any());
    }
}
