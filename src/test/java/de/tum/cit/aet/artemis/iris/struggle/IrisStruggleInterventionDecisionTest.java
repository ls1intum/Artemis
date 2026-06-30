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
import de.tum.cit.aet.artemis.iris.domain.session.IrisChatMode;
import de.tum.cit.aet.artemis.iris.domain.session.IrisChatSession;
import de.tum.cit.aet.artemis.iris.repository.IrisChatSessionRepository;
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
 * Plain Mockito unit test for the decision side of {@link IrisStruggleInterventionService#handleDecision}. The four
 * behaviors are the contract: active >= threshold materializes the session, persists an origin-tagged LLM message and
 * pushes both the chat message and an {@code active} event; active < threshold downgrades to silent (no session, no
 * save, no event); ambient >= threshold persists an origin-tagged LLM message into the shared session and emits an
 * {@code ambient} event carrying its sessionId + messageId (no live push this slice); an active outcome whose
 * resolved session is not exercise-bound is defensively dropped (no save).
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

    private IrisStruggleInterventionService service;

    private User user;

    private final StruggleInterventionJob job = new StruggleInterventionJob("t", 7L, 42L, 3L, null, null, null, null);

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(3L);
        user.setLogin("student1");
        service = new IrisStruggleInterventionService(programmingExerciseRepository, authCheckService, irisSettingsService, irisChatSessionRepository, pyrisDTOService,
                pyrisPipelineService, pyrisJobService, userRepository, irisChatSessionService, irisMessageService, irisChatWebsocketService);
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
        var update = new PyrisStruggleInterventionStatusUpdateDTO("Check empty list.", "active", 0.8, "FM", List.of(), List.of(), null, null, null);
        service.handleDecision(job, update);
        verify(irisMessageService).saveMessage(argThat(m -> m.getOrigin() == IrisMessageOrigin.PROACTIVE_STRUGGLE), eq(session), eq(IrisMessageSender.LLM));
        verify(irisChatWebsocketService).sendMessage(eq(session), any(), any());
        // Objects.equals: sessionId is a @Nullable Long, so a regression to null fails as a clean assertion mismatch
        // rather than throwing NPE inside argThat. confidence is forwarded for the eval log (§12).
        verify(irisChatWebsocketService).sendStruggleEvent(any(),
                argThat(e -> "active".equals(e.action()) && Objects.equals(e.sessionId(), 99L) && Objects.equals(e.messageId(), 555L) && Objects.equals(e.confidence(), 0.8)));
    }

    @Test
    void active_belowThreshold_downgradesToSilent_noSessionCreated() {
        var update = new PyrisStruggleInterventionStatusUpdateDTO("Check empty list.", "active", 0.4, "FM", List.of(), List.of(), null, null, null);
        service.handleDecision(job, update);
        verify(irisChatSessionService, never()).getCurrentSessionOrCreateIfNotExists(any(), eq(42L), any());
        verify(irisMessageService, never()).saveMessage(any(), any(), any());
        verify(irisChatWebsocketService, never()).sendStruggleEvent(any(), any());
    }

    @Test
    void ambient_aboveThreshold_persistsAndEmitsSessionAndMessageId() {
        var session = exerciseSession(42L);
        when(irisChatSessionService.getCurrentSessionOrCreateIfNotExists(eq(IrisChatMode.PROGRAMMING_EXERCISE_CHAT), eq(42L), any())).thenReturn(session);
        when(irisMessageService.saveMessage(any(), eq(session), eq(IrisMessageSender.LLM))).thenAnswer(inv -> {
            IrisMessage m = inv.getArgument(0);
            m.setId(556L);
            return m;
        });
        var update = new PyrisStruggleInterventionStatusUpdateDTO("Re-check the logic.", "ambient", 0.7, null, List.of(), List.of(), "Sort.java", 42, "off-by-one?");

        service.handleDecision(job, update);

        // unify-persistence: ambient now saves an origin-tagged LLM message into the shared exercise-chat session
        verify(irisMessageService).saveMessage(argThat(m -> m.getOrigin() == IrisMessageOrigin.PROACTIVE_STRUGGLE), eq(session), eq(IrisMessageSender.LLM));
        // but it does NOT push the bubble live in this slice (surfacing is deferred)
        verify(irisChatWebsocketService, never()).sendMessage(any(), any(), any());
        // the event also forwards the gate's anchor + inlineHint (spec §4/§8) for the inline surface
        verify(irisChatWebsocketService).sendStruggleEvent(any(),
                argThat(e -> "ambient".equals(e.action()) && Objects.equals(e.message(), "Re-check the logic.") && Objects.equals(e.sessionId(), 99L)
                        && Objects.equals(e.messageId(), 556L) && "Sort.java".equals(e.anchorFile()) && Objects.equals(e.anchorLine(), 42) && "off-by-one?".equals(e.inlineHint())
                        && Objects.equals(e.confidence(), 0.7)));
    }

    @Test
    void active_resolvedSessionNotExerciseBound_isDropped() {
        var session = exerciseSession(999L);   // defensive: resolved session not bound to job.exerciseId()
        when(irisChatSessionService.getCurrentSessionOrCreateIfNotExists(eq(IrisChatMode.PROGRAMMING_EXERCISE_CHAT), eq(42L), any())).thenReturn(session);
        var update = new PyrisStruggleInterventionStatusUpdateDTO("Check empty list.", "active", 0.9, "FM", List.of(), List.of(), null, null, null);
        service.handleDecision(job, update);
        verify(irisMessageService, never()).saveMessage(any(), any(), any());
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
