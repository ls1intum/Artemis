package de.tum.cit.aet.artemis.iris.struggle;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Objects;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessage;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessageOrigin;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessageSender;
import de.tum.cit.aet.artemis.iris.domain.message.IrisProactiveOutcome;
import de.tum.cit.aet.artemis.iris.domain.session.IrisChatMode;
import de.tum.cit.aet.artemis.iris.domain.session.IrisChatSession;
import de.tum.cit.aet.artemis.iris.repository.IrisChatSessionRepository;
import de.tum.cit.aet.artemis.iris.repository.IrisMessageRepository;
import de.tum.cit.aet.artemis.iris.service.IrisMessageService;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisDTOService;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisJobService;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisPipelineService;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.struggle.PyrisStruggleInterventionStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.job.StruggleInterventionJob;
import de.tum.cit.aet.artemis.iris.service.session.IrisChatSessionService;
import de.tum.cit.aet.artemis.iris.service.session.IrisStruggleInterventionService;
import de.tum.cit.aet.artemis.iris.service.settings.IrisSettingsService;
import de.tum.cit.aet.artemis.iris.service.websocket.IrisChatWebsocketService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestRepository;

/**
 * Plain Mockito unit test for the decision side of {@link IrisStruggleInterventionService#handleDecision}.
 *
 * <p>
 * A9 contracts verified here:
 * <ul>
 * <li>active above threshold: persist with episodeId, sendMessage, emit kind="decide"/action="active" event.</li>
 * <li>active below threshold: downgrades to silent, no session created, emits kind="decide"/action="silent" noop.</li>
 * <li>ambient above threshold: NO persist (pull model), session resolved for sessionId, emits kind="decide"/action="ambient" event.</li>
 * <li>active with terminal episode: no persist, emits kind="decide"/action="silent" noop.</li>
 * <li>active resolved session not exercise-bound: defensive drop, no save, no event.</li>
 * <li>null result: emits kind="decide"/action="silent" noop regardless of action.</li>
 * <li>active with episodeId: episodeId stamped on the persisted message.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class IrisStruggleInterventionDecisionTest {

    @Mock
    private ProgrammingExerciseTestRepository programmingExerciseRepository;

    @Mock
    private AuthorizationCheckService authCheckService;

    @Mock
    private IrisSettingsService irisSettingsService;

    @Mock
    private IrisChatSessionRepository irisChatSessionRepository;

    @Mock
    private PyrisDTOService pyrisDTOService;

    @Mock
    private PyrisPipelineService pyrisPipelineService;

    @Mock
    private PyrisJobService pyrisJobService;

    @Mock
    private UserTestRepository userRepository;

    @Mock
    private IrisChatSessionService irisChatSessionService;

    @Mock
    private IrisMessageService irisMessageService;

    @Mock
    private IrisChatWebsocketService irisChatWebsocketService;

    @Mock
    private IrisMessageRepository irisMessageRepository;

    private IrisStruggleInterventionService service;

    private User user;

    // job with no episodeId (legacy / single-episode scenarios)
    private final StruggleInterventionJob job = new StruggleInterventionJob("t", 7L, 42L, 3L, null, null, null, null);

    // job with an explicit episodeId (A9 episodeId-threading tests)
    private final StruggleInterventionJob jobWithEpisode = new StruggleInterventionJob("t2", 7L, 42L, 3L, "decide", "ep-123", null, null);

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(3L);
        user.setLogin("student1");
        service = new IrisStruggleInterventionService(programmingExerciseRepository, authCheckService, irisSettingsService, irisChatSessionRepository, pyrisDTOService,
                pyrisPipelineService, pyrisJobService, userRepository, irisChatSessionService, irisMessageService, irisChatWebsocketService, irisMessageRepository);
        ReflectionTestUtils.setField(service, "confidenceThreshold", 0.6);
        when(userRepository.findByIdElseThrow(3L)).thenReturn(user);
    }

    @Test
    void active_aboveThreshold_materializesPersistsAndPushes() {
        var session = exerciseSession(42L);
        when(irisChatSessionService.getCurrentSessionOrCreateIfNotExists(eq(IrisChatMode.PROGRAMMING_EXERCISE_CHAT), eq(42L), any())).thenReturn(session);
        when(irisMessageService.saveMessage(any(), eq(session), eq(IrisMessageSender.LLM))).thenAnswer(inv -> {
            IrisMessage m = inv.getArgument(0);
            m.setId(555L);
            return m;
        });
        var update = new PyrisStruggleInterventionStatusUpdateDTO("Check empty list.", "active", 0.8, "FM", List.of(), List.of(), null, null, null, null, null, null, null, null);
        service.handleDecision(job, update);
        verify(irisMessageService).saveMessage(argThat(m -> m.getOrigin() == IrisMessageOrigin.PROACTIVE_STRUGGLE), eq(session), eq(IrisMessageSender.LLM));
        verify(irisChatWebsocketService).sendMessage(eq(session), any(), any());
        // Objects.equals: sessionId is a @Nullable Long, so a regression to null fails as a clean assertion mismatch
        // rather than throwing NPE inside argThat. confidence is forwarded for the eval log (§12).
        verify(irisChatWebsocketService).sendStruggleEvent(any(),
                argThat(e -> "active".equals(e.action()) && Objects.equals(e.sessionId(), 99L) && Objects.equals(e.messageId(), 555L) && Objects.equals(e.confidence(), 0.8)));
    }

    @Test
    void active_belowThreshold_downgradesToSilent_noSessionCreated_emitsSilentNoop() {
        var update = new PyrisStruggleInterventionStatusUpdateDTO("Check empty list.", "active", 0.4, "FM", List.of(), List.of(), null, null, null, null, null, null, null, null);
        service.handleDecision(job, update);
        verify(irisChatSessionService, never()).getCurrentSessionOrCreateIfNotExists(any(), eq(42L), any());
        verify(irisMessageService, never()).saveMessage(any(), any(), any());
        // A9: silent downgrade always emits a kind="decide"/action="silent" noop so the client's in-flight clears.
        verify(irisChatWebsocketService).sendStruggleEvent(any(), argThat(e -> "decide".equals(e.kind()) && "silent".equals(e.action())));
    }

    @Test
    void ambient_aboveThreshold_emitsEventWithSessionId_noPersistedMessage() {
        // A9 pull model: ambient does NOT persist. Session is resolved to supply sessionId on the event.
        var session = exerciseSession(42L);
        when(irisChatSessionService.getCurrentSessionOrCreateIfNotExists(eq(IrisChatMode.PROGRAMMING_EXERCISE_CHAT), eq(42L), any())).thenReturn(session);
        var update = new PyrisStruggleInterventionStatusUpdateDTO("Re-check the logic.", "ambient", 0.7, null, List.of(), List.of(), "Sort.java", 42, "off-by-one?", null, null,
                null, null, null);

        service.handleDecision(job, update);

        // ambient never saves a message row (pull model)
        verify(irisMessageService, never()).saveMessage(any(), any(), any());
        verify(irisChatWebsocketService, never()).sendMessage(any(), any(), any());
        // event carries kind="decide", action="ambient", the hint text, and the resolved sessionId (no messageId)
        verify(irisChatWebsocketService).sendStruggleEvent(any(),
                argThat(e -> "decide".equals(e.kind()) && "ambient".equals(e.action()) && Objects.equals(e.message(), "Re-check the logic.") && Objects.equals(e.sessionId(), 99L)
                        && e.messageId() == null && "Sort.java".equals(e.anchorFile()) && Objects.equals(e.anchorLine(), 42) && "off-by-one?".equals(e.inlineHint())
                        && Objects.equals(e.confidence(), 0.7)));
    }

    @Test
    void active_resolvedSessionNotExerciseBound_isDropped() {
        var session = exerciseSession(999L);   // defensive: resolved session not bound to job.exerciseId()
        when(irisChatSessionService.getCurrentSessionOrCreateIfNotExists(eq(IrisChatMode.PROGRAMMING_EXERCISE_CHAT), eq(42L), any())).thenReturn(session);
        var update = new PyrisStruggleInterventionStatusUpdateDTO("Check empty list.", "active", 0.9, "FM", List.of(), List.of(), null, null, null, null, null, null, null, null);
        service.handleDecision(job, update);
        verify(irisMessageService, never()).saveMessage(any(), any(), any());
    }

    @Test
    void nullResult_emitsSilentDecideEvent_noPersistedMessage() {
        // Empty/null result: a completion noop is always emitted so the client's in-flight decide clears (Critical fix).
        var update = new PyrisStruggleInterventionStatusUpdateDTO(null, "active", 0.9, "FM", List.of(), List.of(), null, null, null, null, null, null, null, null);
        service.handleDecision(job, update);
        verify(irisMessageService, never()).saveMessage(any(), any(), any());
        verify(irisChatWebsocketService).sendStruggleEvent(any(), argThat(e -> "decide".equals(e.kind()) && "silent".equals(e.action())));
    }

    @Test
    void emptyResult_emitsSilentDecideEvent_noPersistedMessage() {
        var update = new PyrisStruggleInterventionStatusUpdateDTO("", "active", 0.9, "FM", List.of(), List.of(), null, null, null, null, null, null, null, null);
        service.handleDecision(job, update);
        verify(irisMessageService, never()).saveMessage(any(), any(), any());
        verify(irisChatWebsocketService).sendStruggleEvent(any(), argThat(e -> "decide".equals(e.kind()) && "silent".equals(e.action())));
    }

    @Test
    void active_withEpisodeId_stampsEpisodeIdOnPersistedMessage() {
        // A9: the episodeId from the job must be set on the saved IrisMessage row.
        var session = exerciseSession(42L);
        when(irisChatSessionService.getCurrentSessionOrCreateIfNotExists(eq(IrisChatMode.PROGRAMMING_EXERCISE_CHAT), eq(42L), any())).thenReturn(session);
        when(irisMessageRepository.findEpisodeOutcomes("ep-123")).thenReturn(List.of());   // not yet terminal
        when(irisMessageService.saveMessage(any(), eq(session), eq(IrisMessageSender.LLM))).thenAnswer(inv -> {
            IrisMessage m = inv.getArgument(0);
            m.setId(777L);
            return m;
        });
        var update = new PyrisStruggleInterventionStatusUpdateDTO("Hint text.", "active", 0.9, "FM", List.of(), List.of(), null, null, null, null, null, null, null, null);
        service.handleDecision(jobWithEpisode, update);
        // The persisted message must have proactiveEpisodeId set.
        verify(irisMessageService).saveMessage(argThat(m -> "ep-123".equals(m.getProactiveEpisodeId())), eq(session), eq(IrisMessageSender.LLM));
        // The active control event must carry the episodeId.
        verify(irisChatWebsocketService).sendStruggleEvent(any(),
                argThat(e -> "decide".equals(e.kind()) && "active".equals(e.action()) && Objects.equals(e.episodeId(), "ep-123") && Objects.equals(e.messageId(), 777L)));
    }

    @Test
    void active_withTerminalEpisode_emitsSilentEvent_noPersistedMessage() {
        // A9: if the episode is already terminal (DISMISSED), a late escalation is skipped and a silent noop emitted.
        when(irisMessageRepository.findEpisodeOutcomes("ep-123")).thenReturn(List.of(IrisProactiveOutcome.DISMISSED));
        var update = new PyrisStruggleInterventionStatusUpdateDTO("Hint text.", "active", 0.9, "FM", List.of(), List.of(), null, null, null, null, null, null, null, null);
        service.handleDecision(jobWithEpisode, update);
        verify(irisMessageService, never()).saveMessage(any(), any(), any());
        verify(irisChatWebsocketService).sendStruggleEvent(any(),
                argThat(e -> "decide".equals(e.kind()) && "silent".equals(e.action()) && Objects.equals(e.episodeId(), "ep-123")));
    }

    private IrisChatSession exerciseSession(long entityId) {
        var course = new Course();
        course.setId(7L);
        var exercise = new ProgrammingExercise();
        exercise.setId(entityId);
        exercise.setCourse(course);
        var session = new IrisChatSession(exercise, user, IrisChatMode.PROGRAMMING_EXERCISE_CHAT);
        session.setId(99L);
        return session;
    }
}
