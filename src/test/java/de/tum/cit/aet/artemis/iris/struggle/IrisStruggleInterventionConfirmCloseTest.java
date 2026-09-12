package de.tum.cit.aet.artemis.iris.struggle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.admin.service.LLMTokenUsageService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.iris.config.IrisProactiveProperties;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessage;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessageOrigin;
import de.tum.cit.aet.artemis.iris.domain.message.IrisProactiveEpisode;
import de.tum.cit.aet.artemis.iris.domain.message.IrisProactiveOutcome;
import de.tum.cit.aet.artemis.iris.domain.session.IrisChatMode;
import de.tum.cit.aet.artemis.iris.domain.session.IrisChatSession;
import de.tum.cit.aet.artemis.iris.domain.session.IrisSession;
import de.tum.cit.aet.artemis.iris.repository.IrisMessageRepository;
import de.tum.cit.aet.artemis.iris.repository.IrisProactiveEpisodeRepository;
import de.tum.cit.aet.artemis.iris.repository.IrisSessionRepository;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisRunState;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.struggle.PyrisStruggleInterventionStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.job.StruggleInterventionJob;
import de.tum.cit.aet.artemis.iris.service.session.IrisChatSessionService;
import de.tum.cit.aet.artemis.iris.service.session.IrisProactiveEpisodeService;
import de.tum.cit.aet.artemis.iris.service.session.IrisStruggleInterventionService;
import de.tum.cit.aet.artemis.iris.service.websocket.IrisChatWebsocketService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

/**
 * Plain Mockito unit tests for {@link IrisStruggleInterventionService#handleConfirmClose}: which confirm reason and
 * result persist a closing row and a {@code RECOVERED} outcome, and which stay quiet.
 *
 * <p>
 * Every case that commits neither asserts {@code resolved=false}, including the ones Pyris answered
 * {@code resolved=true} for: the event reports what the server committed, not what the gate concluded.
 */
@ExtendWith(MockitoExtension.class)
class IrisStruggleInterventionConfirmCloseTest {

    @Mock
    private UserTestRepository userRepository;

    @Mock
    private IrisChatSessionService irisChatSessionService;

    @Mock
    private IrisChatWebsocketService irisChatWebsocketService;

    @Mock
    private IrisMessageRepository irisMessageRepository;

    @Mock
    private IrisSessionRepository irisSessionRepository;

    @Mock
    private IrisProactiveEpisodeRepository irisProactiveEpisodeRepository;

    @Mock
    private LLMTokenUsageService llmTokenUsageService;

    private IrisStruggleInterventionService service;

    private IrisProactiveEpisodeService episodeService;

    private User user;

    private final StruggleInterventionJob progressJob = new StruggleInterventionJob("t1", 7L, 42L, 3L, "confirm_close", "ep-cc", "progress", null, null);

    private final StruggleInterventionJob parkedJob = new StruggleInterventionJob("t3", 7L, 42L, 3L, "confirm_close", "ep-cc", "parked_progress", null, null);

    private final StruggleInterventionJob nullReasonJob = new StruggleInterventionJob("t4", 7L, 42L, 3L, "confirm_close", "ep-cc", null, null, null);

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(3L);
        user.setLogin("student1");
        // Real episode service and real write fragments on top of the mocked repositories, so the registry logic
        // these tests exercise actually runs instead of the mocks answering null.
        IrisWriteFragments.attachTo(irisSessionRepository, irisMessageRepository, irisProactiveEpisodeRepository);
        episodeService = new IrisProactiveEpisodeService(irisProactiveEpisodeRepository, irisMessageRepository);
        service = new IrisStruggleInterventionService(userRepository, irisChatSessionService, irisChatWebsocketService, irisSessionRepository, irisProactiveEpisodeRepository,
                episodeService, llmTokenUsageService, new IrisProactiveProperties());
        when(userRepository.findByIdElseThrow(3L)).thenReturn(user);
    }

    // --- confirm_close progress resolved=true ---

    /** The proactive message the append fragment built and cascaded, or {@code null} when nothing was appended. */
    private IrisMessage appendedMessage;

    // The append seam: the session repository's write fragment locks the session, builds the message and cascades
    // it, so a test stubs the lock and the merge and inspects the message the fragment built.
    private void stubProactiveAppend(IrisChatSession session, @Nullable Long assignedId) {
        when(irisSessionRepository.findByIdWithWriteLockElseThrow(session.getId())).thenReturn(session);
        when(irisSessionRepository.saveAndFlush(any(IrisSession.class))).thenAnswer(call -> {
            IrisSession saved = call.getArgument(0);
            appendedMessage = saved.getMessages().getLast();
            if (assignedId != null) {
                appendedMessage.setId(assignedId);
            }
            return saved;
        });
    }

    @Test
    void confirmClose_progress_resolved_true_persistsClosingAndWritesRecovered() {
        var session = exerciseSession(42L);
        when(irisChatSessionService.getCurrentSessionOrCreateIfNotExists(eq(IrisChatMode.PROGRAMMING_EXERCISE_CHAT), eq(42L), any())).thenReturn(session);
        when(irisMessageRepository.findEpisodeOutcomes("ep-cc", 3L, 42L)).thenReturn(List.of());
        when(irisMessageRepository.findEpisodeRowIdsForUserOrderByIdAsc("ep-cc", 3L, 42L)).thenReturn(List.of(201L));
        when(irisMessageRepository.setProactiveOutcomeIfNull(201L, IrisProactiveOutcome.RECOVERED)).thenReturn(1);
        stubProactiveAppend(session, 201L);
        var update = closeUpdate(true, "You nailed it!", "Challenge cleared", null);

        service.handleConfirmClose(progressJob, update);

        assertThat(appendedMessage).isNotNull();
        assertThat(appendedMessage.getOrigin()).isEqualTo(IrisMessageOrigin.PROACTIVE_STRUGGLE);
        assertThat(appendedMessage.getProactiveEpisodeId()).isEqualTo("ep-cc");
        verify(irisChatWebsocketService).sendMessage(eq(session), any(), any(), any());
        verify(irisMessageRepository).setProactiveOutcomeIfNull(201L, IrisProactiveOutcome.RECOVERED);
        verify(irisChatWebsocketService).sendStruggleEvent(any(),
                argThat(e -> "confirm_close".equals(e.kind()) && Objects.equals(e.resolved(), true) && "You nailed it!".equals(e.closingSentence())
                        && "Challenge cleared".equals(e.episodeLabel()) && Objects.equals(e.messageId(), 201L) && Objects.equals(e.episodeId(), "ep-cc")));
        // Persist, outcome, broadcast. The row is inserted before its outcome, so a close never gates away its own
        // row, and the broadcast trails both, closing the window a concurrent dismiss used to slip through.
        InOrder order = inOrder(irisSessionRepository, irisMessageRepository, irisChatWebsocketService);
        order.verify(irisSessionRepository).saveAndFlush(any(IrisSession.class));
        order.verify(irisMessageRepository).setProactiveOutcomeIfNull(anyLong(), eq(IrisProactiveOutcome.RECOVERED));
        order.verify(irisChatWebsocketService).sendMessage(eq(session), any(), any(), any());
    }

    @Test
    void confirmClose_unregisteredEpisode_decidesTerminalUnderTheSessionLockBeforeAppending() {
        // The registered branch decides under the registry write lock. An unregistered episode falls back to its
        // message rows, read plainly and only once the session lock is held, because locking them first deadlocks
        // with the superseded-message delete.
        var session = exerciseSession(42L);
        when(irisChatSessionService.getCurrentSessionOrCreateIfNotExists(eq(IrisChatMode.PROGRAMMING_EXERCISE_CHAT), eq(42L), any())).thenReturn(session);
        when(irisMessageRepository.findEpisodeOutcomes("ep-cc", 3L, 42L)).thenReturn(List.of());
        when(irisMessageRepository.findEpisodeRowIdsForUserOrderByIdAsc("ep-cc", 3L, 42L)).thenReturn(List.of(204L));
        when(irisMessageRepository.setProactiveOutcomeIfNull(204L, IrisProactiveOutcome.RECOVERED)).thenReturn(1);
        stubProactiveAppend(session, 204L);

        service.handleConfirmClose(progressJob, closeUpdate(true, "Closing", "Done", null));

        InOrder order = inOrder(irisSessionRepository, irisMessageRepository);
        order.verify(irisSessionRepository).findByIdWithWriteLockElseThrow(session.getId());
        order.verify(irisMessageRepository).findEpisodeOutcomes("ep-cc", 3L, 42L);
        order.verify(irisSessionRepository).saveAndFlush(any(IrisSession.class));
    }

    @Test
    void confirmClose_progress_resolved_true_missingFields_usesDefaults() {
        var session = exerciseSession(42L);
        when(irisChatSessionService.getCurrentSessionOrCreateIfNotExists(eq(IrisChatMode.PROGRAMMING_EXERCISE_CHAT), eq(42L), any())).thenReturn(session);
        when(irisMessageRepository.findEpisodeOutcomes("ep-cc", 3L, 42L)).thenReturn(List.of());
        when(irisMessageRepository.findEpisodeRowIdsForUserOrderByIdAsc("ep-cc", 3L, 42L)).thenReturn(List.of(202L));
        when(irisMessageRepository.setProactiveOutcomeIfNull(202L, IrisProactiveOutcome.RECOVERED)).thenReturn(1);
        stubProactiveAppend(session, 202L);
        // closingSentence and episodeLabel are null/blank: defaults must apply
        var update = closeUpdate(true, null, null, null);

        service.handleConfirmClose(progressJob, update);

        verify(irisChatWebsocketService).sendStruggleEvent(any(),
                argThat(e -> "confirm_close".equals(e.kind()) && "Nice work, that is resolved.".equals(e.closingSentence()) && "Resolved".equals(e.episodeLabel())));
    }

    @Test
    void confirmClose_progress_resolved_false_isQuiet() {
        when(irisMessageRepository.findEpisodeOutcomes("ep-cc", 3L, 42L)).thenReturn(List.of());
        var update = closeUpdate(false, null, null, null);

        service.handleConfirmClose(progressJob, update);

        // No persist, no outcome write
        assertThat(appendedMessage).isNull();
        verify(irisMessageRepository, never()).setProactiveOutcomeIfNull(anyLong(), any());
        // Quiet event: resolved=false, no messageId
        verify(irisChatWebsocketService).sendStruggleEvent(any(),
                argThat(e -> "confirm_close".equals(e.kind()) && Objects.equals(e.resolved(), false) && e.messageId() == null && Objects.equals(e.episodeId(), "ep-cc")));
    }

    @Test
    void confirmClose_forwardsPyrisRationale_onBothTheCommittedAndTheUnresolvedFrame() {
        // The rationale never reaches the student, it rides along for the client's eval log, and it has to survive
        // both frame shapes.
        var session = exerciseSession(42L);
        when(irisChatSessionService.getCurrentSessionOrCreateIfNotExists(eq(IrisChatMode.PROGRAMMING_EXERCISE_CHAT), eq(42L), any())).thenReturn(session);
        when(irisMessageRepository.findEpisodeOutcomes("ep-cc", 3L, 42L)).thenReturn(List.of());
        when(irisMessageRepository.findEpisodeRowIdsForUserOrderByIdAsc("ep-cc", 3L, 42L)).thenReturn(List.of(203L));
        when(irisMessageRepository.setProactiveOutcomeIfNull(203L, IrisProactiveOutcome.RECOVERED)).thenReturn(1);
        stubProactiveAppend(session, 203L);

        service.handleConfirmClose(progressJob, closeUpdateWithRationale(true, "tests pass and the student moved on"));
        service.handleConfirmClose(parkedJob, closeUpdateWithRationale(false, "still stuck on the same failure"));

        verify(irisChatWebsocketService).sendStruggleEvent(any(), argThat(e -> Objects.equals(e.resolved(), true) && "tests pass and the student moved on".equals(e.rationale())));
        verify(irisChatWebsocketService).sendStruggleEvent(any(), argThat(e -> Objects.equals(e.resolved(), false) && "still stuck on the same failure".equals(e.rationale())));
    }

    // --- confirm_close parked_progress ---

    @Test
    void confirmClose_parkedProgress_resolved_true_isFullySilent() {
        var update = closeUpdate(true, "Closing", "Label", null);

        service.handleConfirmClose(parkedJob, update);

        // Nothing persisted, no outcome
        assertThat(appendedMessage).isNull();
        verify(irisMessageRepository, never()).setProactiveOutcomeIfNull(anyLong(), any());
        // Terminal gate NOT consulted (no findEpisodeOutcomes call for parked_progress)
        verify(irisMessageRepository, never()).findEpisodeOutcomes(any(), anyLong(), anyLong());
        // resolved=false even though Pyris answered true: parked_progress commits nothing, so forwarding the gate's
        // verdict would tell the client an episode had recovered that the server never closed.
        verify(irisChatWebsocketService).sendStruggleEvent(any(), argThat(e -> "confirm_close".equals(e.kind()) && Objects.equals(e.resolved(), false) && e.messageId() == null
                && e.closingSentence() == null && Objects.equals(e.episodeId(), "ep-cc")));
    }

    @Test
    void confirmClose_parkedProgress_resolved_false_isFullySilent() {
        var update = closeUpdate(false, null, null, null);

        service.handleConfirmClose(parkedJob, update);

        assertThat(appendedMessage).isNull();
        verify(irisMessageRepository, never()).setProactiveOutcomeIfNull(anyLong(), any());
        verify(irisMessageRepository, never()).findEpisodeOutcomes(any(), anyLong(), anyLong());
        verify(irisChatWebsocketService).sendStruggleEvent(any(), argThat(e -> "confirm_close".equals(e.kind()) && Objects.equals(e.resolved(), false) && e.messageId() == null));
    }

    // --- confirm_close null confirmReason (fail-closed) ---

    @Test
    void confirmClose_nullConfirmReason_failsClosedToParkedSemantics() {
        var update = closeUpdate(true, "Closing", "Label", null);

        service.handleConfirmClose(nullReasonJob, update);

        // Nothing persisted, no outcome (fail-closed: identical to parked_progress)
        assertThat(appendedMessage).isNull();
        verify(irisMessageRepository, never()).setProactiveOutcomeIfNull(anyLong(), any());
        verify(irisMessageRepository, never()).findEpisodeOutcomes(any(), anyLong(), anyLong());
        verify(irisChatWebsocketService).sendStruggleEvent(any(), argThat(e -> "confirm_close".equals(e.kind()) && Objects.equals(e.resolved(), false) && e.messageId() == null));
    }

    // --- confirm_close terminal gate ---

    @Test
    void confirmClose_alreadyTerminal_skipsPersistandEmitsNoop() {
        // Episode already has DISMISSED: persist must be skipped and a noop event emitted.
        when(irisMessageRepository.findEpisodeOutcomes("ep-cc", 3L, 42L)).thenReturn(List.of(IrisProactiveOutcome.DISMISSED));
        var update = closeUpdate(true, "Closing", "Done", null);

        service.handleConfirmClose(progressJob, update);

        assertThat(appendedMessage).isNull();
        verify(irisMessageRepository, never()).setProactiveOutcomeIfNull(anyLong(), any());
        verify(irisChatWebsocketService).sendStruggleEvent(any(), argThat(e -> "confirm_close".equals(e.kind()) && Objects.equals(e.resolved(), false) && e.messageId() == null));
    }

    @Test
    void confirmClose_terminalOnlyUnderTheLock_reportsUnresolved() {
        // The fast gate passes but the registry row already carries an outcome, so the locked check finds the
        // episode terminal. This is the window the fast gate cannot cover.
        var session = exerciseSession(42L);
        when(irisChatSessionService.getCurrentSessionOrCreateIfNotExists(eq(IrisChatMode.PROGRAMMING_EXERCISE_CHAT), eq(42L), any())).thenReturn(session);
        when(irisMessageRepository.findEpisodeOutcomes("ep-cc", 3L, 42L)).thenReturn(List.of());
        var episode = new IrisProactiveEpisode();
        episode.setUserId(3L);
        episode.setExerciseId(42L);
        episode.setEpisodeId("ep-cc");
        episode.setOutcome(IrisProactiveOutcome.DISMISSED);
        when(irisProactiveEpisodeRepository.findForUpdate(3L, 42L, "ep-cc")).thenReturn(Optional.of(episode));

        service.handleConfirmClose(progressJob, closeUpdate(true, "Closing", "Done", null));

        assertThat(appendedMessage).isNull();
        verify(irisChatWebsocketService, never()).sendMessage(any(), any(), any(), any());
        verify(irisChatWebsocketService).sendStruggleEvent(any(), argThat(e -> "confirm_close".equals(e.kind()) && Objects.equals(e.resolved(), false) && e.messageId() == null));
    }

    // --- confirm_close persist-failure resilience (Fix 2) ---

    @Test
    void confirmClose_progress_resolved_true_persistFailure_stillEmitsCompletionEvent() {
        // A permanent persist failure must not propagate, or it would skip sendStruggleEvent and leave the client's
        // in-flight confirm_close stuck. The completion still goes out with messageId=null and resolved=false, and
        // RECOVERED is not written, since there is no row to anchor it to.
        var session = exerciseSession(42L);
        when(irisChatSessionService.getCurrentSessionOrCreateIfNotExists(eq(IrisChatMode.PROGRAMMING_EXERCISE_CHAT), eq(42L), any())).thenReturn(session);
        when(irisMessageRepository.findEpisodeOutcomes("ep-cc", 3L, 42L)).thenReturn(List.of());
        when(irisSessionRepository.findByIdWithWriteLockElseThrow(session.getId())).thenReturn(session);
        when(irisSessionRepository.saveAndFlush(any(IrisSession.class))).thenThrow(new DataIntegrityViolationException("persist failed"));
        var update = closeUpdate(true, "Closing", "Done", null);

        service.handleConfirmClose(progressJob, update);   // must not throw

        verify(irisChatWebsocketService, never()).sendMessage(any(), any(), any(), any());
        verify(irisMessageRepository, never()).setProactiveOutcomeIfNull(anyLong(), any());
        verify(irisChatWebsocketService).sendStruggleEvent(any(), argThat(e -> "confirm_close".equals(e.kind()) && Objects.equals(e.resolved(), false) && e.messageId() == null));
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
        // The append re-reads the session under a write lock, so hand the same instance back. Lenient because the
        // paths that never persist do not reach it.
        lenient().when(irisSessionRepository.findByIdWithWriteLockElseThrow(session.getId())).thenReturn(session);
        return session;
    }

    private PyrisStruggleInterventionStatusUpdateDTO closeUpdate(boolean resolved, String closingSentence, String episodeLabel, String rationale) {
        return new PyrisStruggleInterventionStatusUpdateDTO(null, null, null, rationale, PyrisRunState.FINISHED, null, List.of(), null, null, null, resolved, closingSentence,
                episodeLabel);
    }

    private PyrisStruggleInterventionStatusUpdateDTO closeUpdateWithRationale(boolean resolved, String rationale) {
        return closeUpdate(resolved, null, null, rationale);
    }
}
