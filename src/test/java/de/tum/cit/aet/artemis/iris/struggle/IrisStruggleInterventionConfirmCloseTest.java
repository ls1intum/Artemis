package de.tum.cit.aet.artemis.iris.struggle;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Objects;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;

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
import de.tum.cit.aet.artemis.iris.repository.IrisAmbientDecisionRepository;
import de.tum.cit.aet.artemis.iris.repository.IrisChatSessionRepository;
import de.tum.cit.aet.artemis.iris.repository.IrisMessageRepository;
import de.tum.cit.aet.artemis.iris.service.IrisMessageService;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisDTOService;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisJobService;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisPipelineService;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisRunState;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.struggle.PyrisStruggleInterventionStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.job.StruggleInterventionJob;
import de.tum.cit.aet.artemis.iris.service.session.IrisChatSessionService;
import de.tum.cit.aet.artemis.iris.service.session.IrisStruggleInterventionService;
import de.tum.cit.aet.artemis.iris.service.settings.IrisSettingsService;
import de.tum.cit.aet.artemis.iris.service.websocket.IrisChatWebsocketService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestRepository;

/**
 * Plain Mockito unit tests for {@link IrisStruggleInterventionService#handleConfirmClose} (A11).
 *
 * <p>
 * Matrix covered:
 * <ul>
 * <li>confirm_close progress resolved=true: RECOVERED written, closing persisted, event carries closingSentence/episodeLabel/messageId.</li>
 * <li>confirm_close progress resolved=true missing fields: default closing sentence and label applied.</li>
 * <li>confirm_close progress resolved=false: quiet (no persist, no outcome, no messageId in event).</li>
 * <li>confirm_close parked_progress (either result): persist nothing, no outcome, bare completion event (no messageId).</li>
 * <li>confirm_close null confirmReason: fail-closed to parked_progress semantics.</li>
 * <li>confirm_close already-terminal episode: persist skipped, noop event (delivered reasons only).</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class IrisStruggleInterventionConfirmCloseTest {

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

    @Mock
    private IrisAmbientDecisionRepository irisAmbientDecisionRepository;

    @Mock
    private PlatformTransactionManager transactionManager;

    private IrisStruggleInterventionService service;

    private User user;

    private final StruggleInterventionJob progressJob = new StruggleInterventionJob("t1", 7L, 42L, 3L, "confirm_close", "ep-cc", "progress", null, null);

    private final StruggleInterventionJob parkedJob = new StruggleInterventionJob("t3", 7L, 42L, 3L, "confirm_close", "ep-cc", "parked_progress", null, null);

    private final StruggleInterventionJob nullReasonJob = new StruggleInterventionJob("t4", 7L, 42L, 3L, "confirm_close", "ep-cc", null, null, null);

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(3L);
        user.setLogin("student1");
        service = new IrisStruggleInterventionService(programmingExerciseRepository, authCheckService, irisSettingsService, irisChatSessionRepository, pyrisDTOService,
                pyrisPipelineService, pyrisJobService, userRepository, irisChatSessionService, irisMessageService, irisChatWebsocketService, irisMessageRepository,
                irisAmbientDecisionRepository, transactionManager);
        when(userRepository.findByIdElseThrow(3L)).thenReturn(user);
    }

    // --- confirm_close progress resolved=true ---

    @Test
    void confirmClose_progress_resolved_true_persistsClosingAndWritesRecovered() {
        var session = exerciseSession(42L);
        when(irisChatSessionService.getCurrentSessionOrCreateIfNotExists(eq(IrisChatMode.PROGRAMMING_EXERCISE_CHAT), eq(42L), any())).thenReturn(session);
        when(irisMessageRepository.findEpisodeOutcomes("ep-cc", 3L)).thenReturn(List.of());
        when(irisMessageRepository.findEpisodeRowsForUserOrderByIdAsc("ep-cc", 3L)).thenReturn(List.of(savedMsg(201L)));
        when(irisMessageRepository.setProactiveOutcomeIfNull(201L, IrisProactiveOutcome.RECOVERED)).thenReturn(1);
        when(irisMessageService.saveMessage(any(), eq(session), eq(IrisMessageSender.LLM))).thenAnswer(inv -> {
            IrisMessage m = inv.getArgument(0);
            m.setId(201L);
            return m;
        });
        var update = closeUpdate(true, "You nailed it!", "Challenge cleared", null);

        service.handleConfirmClose(progressJob, update);

        verify(irisMessageService).saveMessage(argThat(m -> m.getOrigin() == IrisMessageOrigin.PROACTIVE_STRUGGLE && "ep-cc".equals(m.getProactiveEpisodeId())), eq(session),
                eq(IrisMessageSender.LLM));
        verify(irisChatWebsocketService).sendMessage(eq(session), any(), any(), any());
        verify(irisMessageRepository).setProactiveOutcomeIfNull(201L, IrisProactiveOutcome.RECOVERED);
        verify(irisChatWebsocketService).sendStruggleEvent(any(),
                argThat(e -> "confirm_close".equals(e.kind()) && Objects.equals(e.resolved(), true) && "You nailed it!".equals(e.closingSentence())
                        && "Challenge cleared".equals(e.episodeLabel()) && Objects.equals(e.messageId(), 201L) && Objects.equals(e.episodeId(), "ep-cc")));
        // Outcome-last invariant: persist -> broadcast -> outcome write (a resolved=true close must never gate away its own row).
        InOrder order = inOrder(irisMessageService, irisChatWebsocketService, irisMessageRepository);
        order.verify(irisMessageService).saveMessage(any(), eq(session), eq(IrisMessageSender.LLM));
        order.verify(irisChatWebsocketService).sendMessage(eq(session), any(), any(), any());
        order.verify(irisMessageRepository).setProactiveOutcomeIfNull(anyLong(), eq(IrisProactiveOutcome.RECOVERED));
    }

    @Test
    void confirmClose_progress_resolved_true_missingFields_usesDefaults() {
        var session = exerciseSession(42L);
        when(irisChatSessionService.getCurrentSessionOrCreateIfNotExists(eq(IrisChatMode.PROGRAMMING_EXERCISE_CHAT), eq(42L), any())).thenReturn(session);
        when(irisMessageRepository.findEpisodeOutcomes("ep-cc", 3L)).thenReturn(List.of());
        when(irisMessageRepository.findEpisodeRowsForUserOrderByIdAsc("ep-cc", 3L)).thenReturn(List.of(savedMsg(202L)));
        when(irisMessageRepository.setProactiveOutcomeIfNull(202L, IrisProactiveOutcome.RECOVERED)).thenReturn(1);
        when(irisMessageService.saveMessage(any(), eq(session), eq(IrisMessageSender.LLM))).thenAnswer(inv -> {
            IrisMessage m = inv.getArgument(0);
            m.setId(202L);
            return m;
        });
        // closingSentence and episodeLabel are null/blank: defaults must apply
        var update = closeUpdate(true, null, null, null);

        service.handleConfirmClose(progressJob, update);

        verify(irisChatWebsocketService).sendStruggleEvent(any(),
                argThat(e -> "confirm_close".equals(e.kind()) && "Nice work, that is resolved.".equals(e.closingSentence()) && "Resolved".equals(e.episodeLabel())));
    }

    @Test
    void confirmClose_progress_resolved_false_isQuiet() {
        when(irisMessageRepository.findEpisodeOutcomes("ep-cc", 3L)).thenReturn(List.of());
        var update = closeUpdate(false, null, null, null);

        service.handleConfirmClose(progressJob, update);

        // No persist, no outcome write
        verify(irisMessageService, never()).saveMessage(any(), any(), any());
        verify(irisMessageRepository, never()).setProactiveOutcomeIfNull(anyLong(), any());
        // Quiet event: resolved=false, no messageId
        verify(irisChatWebsocketService).sendStruggleEvent(any(),
                argThat(e -> "confirm_close".equals(e.kind()) && Objects.equals(e.resolved(), false) && e.messageId() == null && Objects.equals(e.episodeId(), "ep-cc")));
    }

    // --- confirm_close parked_progress ---

    @Test
    void confirmClose_parkedProgress_resolved_true_isFullySilent() {
        var update = closeUpdate(true, "Closing", "Label", null);

        service.handleConfirmClose(parkedJob, update);

        // Nothing persisted, no outcome
        verify(irisMessageService, never()).saveMessage(any(), any(), any());
        verify(irisMessageRepository, never()).setProactiveOutcomeIfNull(anyLong(), any());
        // Terminal gate NOT consulted (no findEpisodeOutcomes call for parked_progress)
        verify(irisMessageRepository, never()).findEpisodeOutcomes(any(), anyLong());
        // Bare completion event only (no messageId, no closingSentence, no episodeLabel)
        verify(irisChatWebsocketService).sendStruggleEvent(any(), argThat(e -> "confirm_close".equals(e.kind()) && Objects.equals(e.resolved(), true) && e.messageId() == null
                && e.closingSentence() == null && Objects.equals(e.episodeId(), "ep-cc")));
    }

    @Test
    void confirmClose_parkedProgress_resolved_false_isFullySilent() {
        var update = closeUpdate(false, null, null, null);

        service.handleConfirmClose(parkedJob, update);

        verify(irisMessageService, never()).saveMessage(any(), any(), any());
        verify(irisMessageRepository, never()).setProactiveOutcomeIfNull(anyLong(), any());
        verify(irisMessageRepository, never()).findEpisodeOutcomes(any(), anyLong());
        verify(irisChatWebsocketService).sendStruggleEvent(any(), argThat(e -> "confirm_close".equals(e.kind()) && Objects.equals(e.resolved(), false) && e.messageId() == null));
    }

    // --- confirm_close null confirmReason (fail-closed) ---

    @Test
    void confirmClose_nullConfirmReason_failsClosedToParkedSemantics() {
        var update = closeUpdate(true, "Closing", "Label", null);

        service.handleConfirmClose(nullReasonJob, update);

        // Nothing persisted, no outcome (fail-closed: identical to parked_progress)
        verify(irisMessageService, never()).saveMessage(any(), any(), any());
        verify(irisMessageRepository, never()).setProactiveOutcomeIfNull(anyLong(), any());
        verify(irisMessageRepository, never()).findEpisodeOutcomes(any(), anyLong());
        verify(irisChatWebsocketService).sendStruggleEvent(any(), argThat(e -> "confirm_close".equals(e.kind()) && e.messageId() == null));
    }

    // --- confirm_close terminal gate ---

    @Test
    void confirmClose_alreadyTerminal_skipsPersistandEmitsNoop() {
        // Episode already has DISMISSED: persist must be skipped and a noop event emitted.
        when(irisMessageRepository.findEpisodeOutcomes("ep-cc", 3L)).thenReturn(List.of(IrisProactiveOutcome.DISMISSED));
        var update = closeUpdate(true, "Closing", "Done", null);

        service.handleConfirmClose(progressJob, update);

        verify(irisMessageService, never()).saveMessage(any(), any(), any());
        verify(irisMessageRepository, never()).setProactiveOutcomeIfNull(anyLong(), any());
        verify(irisChatWebsocketService).sendStruggleEvent(any(), argThat(e -> "confirm_close".equals(e.kind()) && e.messageId() == null));
    }

    // --- confirm_close persist-failure resilience (Fix 2) ---

    @Test
    void confirmClose_progress_resolved_true_persistFailure_stillEmitsCompletionEvent() {
        // Fix 2: a permanent persist failure on confirm_close must NOT propagate out of the handler - that would skip
        // sendStruggleEvent and leave the client's in-flight confirm_close (and the single-flight slot) stuck. The
        // completion event must still be emitted (messageId=null) and RECOVERED must NOT be written (no row to anchor).
        var session = exerciseSession(42L);
        when(irisChatSessionService.getCurrentSessionOrCreateIfNotExists(eq(IrisChatMode.PROGRAMMING_EXERCISE_CHAT), eq(42L), any())).thenReturn(session);
        when(irisMessageRepository.findEpisodeOutcomes("ep-cc", 3L)).thenReturn(List.of());
        when(irisMessageService.saveMessage(any(), eq(session), eq(IrisMessageSender.LLM))).thenThrow(new DataIntegrityViolationException("persist failed"));
        var update = closeUpdate(true, "Closing", "Done", null);

        service.handleConfirmClose(progressJob, update);   // must not throw

        verify(irisChatWebsocketService, never()).sendMessage(any(), any(), any(), any());
        verify(irisMessageRepository, never()).setProactiveOutcomeIfNull(anyLong(), any());
        verify(irisChatWebsocketService).sendStruggleEvent(any(), argThat(e -> "confirm_close".equals(e.kind()) && Objects.equals(e.resolved(), true) && e.messageId() == null));
    }

    // --- helpers ---

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

    private IrisMessage savedMsg(long id) {
        var m = new IrisMessage();
        m.setId(id);
        return m;
    }

    private PyrisStruggleInterventionStatusUpdateDTO closeUpdate(boolean resolved, String closingSentence, String episodeLabel, String rationale) {
        return new PyrisStruggleInterventionStatusUpdateDTO(null, null, null, rationale, PyrisRunState.FINISHED, null, List.of(), null, null, null, resolved, closingSentence,
                episodeLabel);
    }

    private PyrisStruggleInterventionStatusUpdateDTO closeUpdateWithRationale(boolean resolved, String rationale) {
        return closeUpdate(resolved, null, null, rationale);
    }
}
