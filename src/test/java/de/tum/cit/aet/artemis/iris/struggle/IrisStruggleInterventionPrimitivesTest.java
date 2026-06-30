package de.tum.cit.aet.artemis.iris.struggle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import jakarta.ws.rs.BadRequestException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
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
import de.tum.cit.aet.artemis.iris.service.session.IrisChatSessionService;
import de.tum.cit.aet.artemis.iris.service.session.IrisStruggleInterventionService;
import de.tum.cit.aet.artemis.iris.service.settings.IrisSettingsService;
import de.tum.cit.aet.artemis.iris.service.websocket.IrisChatWebsocketService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestRepository;

/**
 * Plain Mockito unit tests for the A10 primitives: {@code revealAmbient}, {@code writeEpisodeOutcome},
 * {@code deleteSupersededProactiveMessage}, and {@code cancelOutstandingStruggleJob}.
 */
@ExtendWith(MockitoExtension.class)
class IrisStruggleInterventionPrimitivesTest {

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

    private static final long EXERCISE_ID = 42L;

    private static final long USER_ID = 3L;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(USER_ID);
        user.setLogin("student1");
        service = new IrisStruggleInterventionService(programmingExerciseRepository, authCheckService, irisSettingsService, irisChatSessionRepository, pyrisDTOService,
                pyrisPipelineService, pyrisJobService, userRepository, irisChatSessionService, irisMessageService, irisChatWebsocketService, irisMessageRepository);
        ReflectionTestUtils.setField(service, "confidenceThreshold", 0.6);
    }

    // ---- revealAmbient ----

    @Test
    void revealAmbient_createsRowWithServerSentAt_andReturnsDtoWithoutSendMessage() {
        var session = exerciseSession(EXERCISE_ID);
        when(irisChatSessionService.getCurrentSessionOrCreateIfNotExists(eq(IrisChatMode.PROGRAMMING_EXERCISE_CHAT), eq(EXERCISE_ID), any())).thenReturn(session);
        when(irisMessageRepository.findByProactiveClientMessageId("cid-1")).thenReturn(Optional.empty());
        when(irisMessageService.saveMessage(any(), eq(session), eq(IrisMessageSender.LLM))).thenAnswer(inv -> {
            IrisMessage m = inv.getArgument(0);
            m.setId(101L);
            return m;
        });

        var dto = service.revealAmbient(user, EXERCISE_ID, "ep-1", "Re-check the loop.", "ambient", "cid-1");

        assertThat(dto.id()).isEqualTo(101L);
        assertThat(dto.proactiveEpisodeId()).isEqualTo("ep-1");
        verify(irisMessageService).saveMessage(
                argThat(m -> m.getOrigin() == IrisMessageOrigin.PROACTIVE_STRUGGLE && "ep-1".equals(m.getProactiveEpisodeId()) && "cid-1".equals(m.getProactiveClientMessageId())),
                eq(session), eq(IrisMessageSender.LLM));
        // CRITICAL: reveal must NOT broadcast over the chat websocket (client owns the optimistic bubble)
        verify(irisChatWebsocketService, never()).sendMessage(any(), any(), any());
    }

    @Test
    void revealAmbient_retry_sameClientMessageId_returnsExistingRowNoDuplicate() {
        var existingMessage = new IrisMessage();
        existingMessage.setId(101L);
        existingMessage.setProactiveEpisodeId("ep-1");
        when(irisMessageRepository.findByProactiveClientMessageId("cid-1")).thenReturn(Optional.of(existingMessage));

        var dto = service.revealAmbient(user, EXERCISE_ID, "ep-1", "Re-check the loop.", "ambient", "cid-1");

        assertThat(dto.id()).isEqualTo(101L);
        // No new row created
        verify(irisMessageService, never()).saveMessage(any(), any(), any());
    }

    @Test
    void revealAmbient_concurrentRetry_catchesIntegrityViolation_andReSelects() {
        var session = exerciseSession(EXERCISE_ID);
        when(irisChatSessionService.getCurrentSessionOrCreateIfNotExists(eq(IrisChatMode.PROGRAMMING_EXERCISE_CHAT), eq(EXERCISE_ID), any())).thenReturn(session);
        // First findBy returns empty (no row yet), save throws on the unique constraint, second findBy returns the row
        var concurrentRow = new IrisMessage();
        concurrentRow.setId(202L);
        concurrentRow.setProactiveEpisodeId("ep-1");
        when(irisMessageRepository.findByProactiveClientMessageId("cid-1")).thenReturn(Optional.empty())            // first check: row doesn't exist
                .thenReturn(Optional.of(concurrentRow)); // re-select after IntegrityViolation
        when(irisMessageService.saveMessage(any(), eq(session), eq(IrisMessageSender.LLM))).thenThrow(new DataIntegrityViolationException("unique constraint violation"));

        var dto = service.revealAmbient(user, EXERCISE_ID, "ep-1", "Re-check the loop.", "ambient", "cid-1");

        assertThat(dto.id()).isEqualTo(202L);
    }

    @Test
    void revealAmbient_differentClientMessageId_doesNotCollideWithSameEpisodeOtherRow() {
        // Two reveals for the same episode with different clientMessageIds must result in two distinct rows
        var session = exerciseSession(EXERCISE_ID);
        when(irisChatSessionService.getCurrentSessionOrCreateIfNotExists(eq(IrisChatMode.PROGRAMMING_EXERCISE_CHAT), eq(EXERCISE_ID), any())).thenReturn(session);
        when(irisMessageRepository.findByProactiveClientMessageId("cid-reveal")).thenReturn(Optional.empty());
        when(irisMessageService.saveMessage(any(), eq(session), eq(IrisMessageSender.LLM))).thenAnswer(inv -> {
            IrisMessage m = inv.getArgument(0);
            m.setId(303L);
            return m;
        });

        var dto = service.revealAmbient(user, EXERCISE_ID, "ep-1", "Same text as escalation.", "ambient", "cid-reveal");

        assertThat(dto.id()).isEqualTo(303L);
        verify(irisMessageService).saveMessage(argThat(m -> "cid-reveal".equals(m.getProactiveClientMessageId())), any(), any());
    }

    @Test
    void revealAmbient_blankClientMessageId_throwsBadRequest() {
        // The idempotency key is mandatory: a null/blank clientMessageId cannot dedupe (NULLs are not unique in SQL).
        assertThatThrownBy(() -> service.revealAmbient(user, EXERCISE_ID, "ep-1", "Re-check the loop.", "ambient", "  ")).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.revealAmbient(user, EXERCISE_ID, "ep-1", "Re-check the loop.", "ambient", null)).isInstanceOf(BadRequestException.class);
        verify(irisMessageService, never()).saveMessage(any(), any(), any());
    }

    // ---- writeEpisodeOutcome ----

    @Test
    void writeEpisodeOutcome_noRowYet_returnsFalse_deferred() {
        when(irisMessageRepository.findFirstByProactiveEpisodeIdOrderByIdAsc("ep-x")).thenReturn(Optional.empty());

        boolean applied = service.writeEpisodeOutcome("ep-x", IrisProactiveOutcome.DISMISSED);

        assertThat(applied).isFalse();
        verify(irisMessageRepository, never()).setProactiveOutcomeIfNull(anyLong(), any());
    }

    @Test
    void writeEpisodeOutcome_rowExists_noOutcomeYet_setsOutcomeAndReturnsTrue() {
        var target = new IrisMessage();
        target.setId(500L);
        when(irisMessageRepository.findFirstByProactiveEpisodeIdOrderByIdAsc("ep-1")).thenReturn(Optional.of(target));
        when(irisMessageRepository.findEpisodeOutcomes("ep-1")).thenReturn(List.of());   // no outcome episode-wide yet
        when(irisMessageRepository.setProactiveOutcomeIfNull(500L, IrisProactiveOutcome.DISMISSED)).thenReturn(1);

        boolean applied = service.writeEpisodeOutcome("ep-1", IrisProactiveOutcome.DISMISSED);

        assertThat(applied).isTrue();
        verify(irisMessageRepository).setProactiveOutcomeIfNull(500L, IrisProactiveOutcome.DISMISSED);
    }

    @Test
    void writeEpisodeOutcome_firstTerminalAlreadySet_sameValue_isNoopReturnsTrue() {
        // The episode already holds a terminal outcome (episode-wide pre-check non-empty): re-writing is a no-op.
        var target = new IrisMessage();
        target.setId(500L);
        when(irisMessageRepository.findFirstByProactiveEpisodeIdOrderByIdAsc("ep-1")).thenReturn(Optional.of(target));
        when(irisMessageRepository.findEpisodeOutcomes("ep-1")).thenReturn(List.of(IrisProactiveOutcome.RECOVERED));

        boolean applied = service.writeEpisodeOutcome("ep-1", IrisProactiveOutcome.RECOVERED);

        assertThat(applied).isTrue();
        verify(irisMessageRepository, never()).setProactiveOutcomeIfNull(anyLong(), any());
    }

    @Test
    void writeEpisodeOutcome_differentValueIgnored_firstTerminalWins_returnsTrue() {
        // Episode already terminal (DISMISSED); a DIFFERENT value (ABANDONED) is silently ignored (first wins).
        var target = new IrisMessage();
        target.setId(500L);
        when(irisMessageRepository.findFirstByProactiveEpisodeIdOrderByIdAsc("ep-1")).thenReturn(Optional.of(target));
        when(irisMessageRepository.findEpisodeOutcomes("ep-1")).thenReturn(List.of(IrisProactiveOutcome.DISMISSED));

        boolean applied = service.writeEpisodeOutcome("ep-1", IrisProactiveOutcome.ABANDONED);

        assertThat(applied).isTrue();
        verify(irisMessageRepository, never()).setProactiveOutcomeIfNull(anyLong(), any());
    }

    @Test
    void writeEpisodeOutcome_smallestIdTargetIsStable_thenEpisodeWideNoop() {
        // The target is the smallest-id (first-persisted) row 600. A later-inserted row (larger id, even with an
        // earlier sentAt) never becomes the target. Once 600 carries the outcome, a second call is a no-op.
        var firstPersisted = new IrisMessage();
        firstPersisted.setId(600L);
        when(irisMessageRepository.findFirstByProactiveEpisodeIdOrderByIdAsc("ep-2")).thenReturn(Optional.of(firstPersisted));
        when(irisMessageRepository.findEpisodeOutcomes("ep-2")).thenReturn(List.of());   // first call: not terminal yet
        when(irisMessageRepository.setProactiveOutcomeIfNull(600L, IrisProactiveOutcome.DISMISSED)).thenReturn(1);

        boolean firstApplied = service.writeEpisodeOutcome("ep-2", IrisProactiveOutcome.DISMISSED);
        assertThat(firstApplied).isTrue();
        verify(irisMessageRepository).setProactiveOutcomeIfNull(600L, IrisProactiveOutcome.DISMISSED);

        // Second call: the episode already holds an outcome, so it is a no-op regardless of newer rows.
        when(irisMessageRepository.findEpisodeOutcomes("ep-2")).thenReturn(List.of(IrisProactiveOutcome.DISMISSED));

        boolean secondApplied = service.writeEpisodeOutcome("ep-2", IrisProactiveOutcome.DISMISSED);
        assertThat(secondApplied).isTrue();

        // setProactiveOutcomeIfNull is invoked exactly once across both calls (only the first call writes).
        verify(irisMessageRepository).setProactiveOutcomeIfNull(anyLong(), any());
    }

    @Test
    void writeEpisodeOutcome_targetVanished_butOutcomeNowExists_returnsTrue() {
        // The guarded update affects 0 rows because the target was concurrently given an outcome; the re-check finds
        // an episode-wide outcome, so applied = true.
        var target = new IrisMessage();
        target.setId(500L);
        when(irisMessageRepository.findFirstByProactiveEpisodeIdOrderByIdAsc("ep-3")).thenReturn(Optional.of(target));
        when(irisMessageRepository.findEpisodeOutcomes("ep-3")).thenReturn(List.of())                      // pre-check: empty
                .thenReturn(List.of(IrisProactiveOutcome.RECOVERED));                                      // re-check: now set
        when(irisMessageRepository.setProactiveOutcomeIfNull(500L, IrisProactiveOutcome.DISMISSED)).thenReturn(0);

        boolean applied = service.writeEpisodeOutcome("ep-3", IrisProactiveOutcome.DISMISSED);

        assertThat(applied).isTrue();
    }

    @Test
    void writeEpisodeOutcome_targetVanished_andNoOutcomeEstablished_returnsFalseDeferred() {
        // The guarded update affects 0 rows because the target row was concurrently DELETED, and no outcome stands
        // anywhere: nothing is established, so applied = false (deferred - the client back-fills once a row exists).
        var target = new IrisMessage();
        target.setId(500L);
        when(irisMessageRepository.findFirstByProactiveEpisodeIdOrderByIdAsc("ep-4")).thenReturn(Optional.of(target));
        when(irisMessageRepository.findEpisodeOutcomes("ep-4")).thenReturn(List.of());   // empty on both the pre-check and the re-check
        when(irisMessageRepository.setProactiveOutcomeIfNull(500L, IrisProactiveOutcome.DISMISSED)).thenReturn(0);

        boolean applied = service.writeEpisodeOutcome("ep-4", IrisProactiveOutcome.DISMISSED);

        assertThat(applied).isFalse();
    }

    // ---- deleteSupersededProactiveMessage ----
    // The guard logic (proactive-origin AND null outcome AND user ownership) lives in ONE atomic SQL statement, so it
    // cannot be meaningfully exercised against a mock; the guards are verified end-to-end in the integration test
    // (real H2 enforces the WHERE). Here we only assert the service delegates with the messageId + the user's id.

    @Test
    void deleteSupersededProactiveMessage_delegatesToAtomicGuardedDelete() {
        service.deleteSupersededProactiveMessage(user, 77L);
        verify(irisMessageRepository).deleteSupersededProactiveMessage(77L, USER_ID);
    }

    // ---- cancelOutstandingStruggleJob ----

    @Test
    void cancelOutstandingStruggleJob_matchingToken_removesJob() {
        service.cancelOutstandingStruggleJob(user, EXERCISE_ID, "tok-A");
        verify(pyrisJobService).removeStruggleJobIfTokenMatches(USER_ID, EXERCISE_ID, "tok-A");
    }

    // ---- helpers ----

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
