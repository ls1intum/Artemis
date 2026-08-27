package de.tum.cit.aet.artemis.iris.struggle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import jakarta.ws.rs.BadRequestException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.service.UserAiPreferenceService;
import de.tum.cit.aet.artemis.account.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.iris.domain.message.IrisAmbientDecision;
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

    @Mock
    private IrisAmbientDecisionRepository irisAmbientDecisionRepository;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Mock
    private UserAiPreferenceService userAiPreferenceService;

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
                pyrisPipelineService, pyrisJobService, userRepository, irisChatSessionService, irisMessageService, irisChatWebsocketService, irisMessageRepository,
                irisAmbientDecisionRepository, transactionManager, userAiPreferenceService);
        ReflectionTestUtils.setField(service, "confidenceThreshold", 0.6);
    }

    // ---- revealAmbient ----

    /**
     * Stub the ambient decision Artemis recorded when it emitted the pointer. A reveal now requires one: it is
     * what makes the persisted text the server's rather than the caller's, and what makes the offer single-use.
     *
     * @param episodeId  the episode the offer belongs to
     * @param serverText the hint text as authored by Pyris
     * @return the stubbed decision
     */
    private IrisAmbientDecision offeredDecision(String episodeId, String serverText) {
        var decision = new IrisAmbientDecision();
        decision.setId(900L);
        decision.setUserId(USER_ID);
        decision.setExerciseId(EXERCISE_ID);
        decision.setEpisodeId(episodeId);
        decision.setHintText(serverText);
        decision.setCreatedAt(ZonedDateTime.now());
        // findForReveal, not the plain finder: the reveal takes the decision under a write lock so the
        // unconsumed-check and the claim cannot interleave with a concurrent reveal of the same offer.
        when(irisAmbientDecisionRepository.findForReveal(USER_ID, EXERCISE_ID, episodeId)).thenReturn(Optional.of(decision));
        // lenient: the tests that assert a rejection never reach the claim, and an offered decision is still the
        // correct precondition for them - the rejection must come from the guard under test, not from a missing offer.
        lenient().when(irisAmbientDecisionRepository.save(decision)).thenReturn(decision);
        return decision;
    }

    /**
     * Build an unstubbed decision. Used where a test needs SEPARATE instances across two lookups, so one call's
     * in-memory mutation cannot silently satisfy the next one.
     *
     * @param episodeId  the episode the offer belongs to
     * @param serverText the hint text as authored by Pyris
     * @return the decision, not wired into any stub
     */
    private IrisAmbientDecision decisionFor(String episodeId, String serverText) {
        var decision = new IrisAmbientDecision();
        decision.setId(900L);
        decision.setUserId(USER_ID);
        decision.setExerciseId(EXERCISE_ID);
        decision.setEpisodeId(episodeId);
        decision.setHintText(serverText);
        decision.setCreatedAt(ZonedDateTime.now());
        lenient().when(irisAmbientDecisionRepository.save(decision)).thenReturn(decision);
        return decision;
    }

    @Test
    void revealAmbient_createsRowWithServerSentAt_andReturnsDtoWithoutSendMessage() {
        offeredDecision("ep-1", "Re-check the loop.");
        var session = exerciseSession(EXERCISE_ID);
        when(irisChatSessionService.getCurrentSessionOrCreateIfNotExists(eq(IrisChatMode.PROGRAMMING_EXERCISE_CHAT), eq(EXERCISE_ID), any())).thenReturn(session);
        when(irisMessageService.saveMessage(any(), eq(session), eq(IrisMessageSender.LLM))).thenAnswer(inv -> {
            IrisMessage m = inv.getArgument(0);
            m.setId(101L);
            return m;
        });

        var dto = service.revealAmbient(user, EXERCISE_ID, "ep-1");

        assertThat(dto.id()).isEqualTo(101L);
        assertThat(dto.proactiveEpisodeId()).isEqualTo("ep-1");
        verify(irisMessageService).saveMessage(argThat(m -> m.getOrigin() == IrisMessageOrigin.PROACTIVE_STRUGGLE && "ep-1".equals(m.getProactiveEpisodeId())), eq(session),
                eq(IrisMessageSender.LLM));
        // CRITICAL: reveal must NOT broadcast over the chat websocket (client owns the optimistic bubble)
        verify(irisChatWebsocketService, never()).sendMessage(any(), any(), any(), any());
    }

    @Test
    void revealAmbient_replay_returnsTheRowTheFirstRevealCreated_noDuplicate() {
        // Contract test, not a red proof: idempotency is scoped to (user, exercise, episode) and enforced by the
        // decision record. A replay finds the offer already consumed and resolves the row that reveal created.
        var decision = offeredDecision("ep-1", "Re-check the loop.");
        decision.setConsumedAt(ZonedDateTime.now());
        decision.setConsumedMessageId(101L);
        var firstReveal = new IrisMessage();
        firstReveal.setId(101L);
        firstReveal.setProactiveEpisodeId("ep-1");
        when(irisMessageRepository.findById(101L)).thenReturn(Optional.of(firstReveal));

        var dto = service.revealAmbient(user, EXERCISE_ID, "ep-1");

        assertThat(dto.id()).isEqualTo(101L);
        // No second row: the consumed decision short-circuits before any insert.
        verify(irisMessageService, never()).saveMessage(any(), any(), any());
    }

    @Test
    void revealAmbient_secondRevealOfTheSameEpisode_returnsTheSameRow() {
        // The old design allowed two rows for one episode when the client varied its message id. The decision record
        // makes the offer single-use, so the second call must resolve the FIRST reveal's row instead of inserting.
        //
        // Two DISTINCT decision instances on purpose. Returning one shared object would let the first call's in-memory
        // mutation satisfy the second lookup, and the test would still pass if the persist of the claim were deleted.
        var unconsumed = decisionFor("ep-1", "Re-check the loop.");
        var consumed = decisionFor("ep-1", "Re-check the loop.");
        consumed.setConsumedAt(ZonedDateTime.now());
        consumed.setConsumedMessageId(303L);
        when(irisAmbientDecisionRepository.findForReveal(USER_ID, EXERCISE_ID, "ep-1")).thenReturn(Optional.of(unconsumed), Optional.of(consumed));
        var session = exerciseSession(EXERCISE_ID);
        when(irisChatSessionService.getCurrentSessionOrCreateIfNotExists(eq(IrisChatMode.PROGRAMMING_EXERCISE_CHAT), eq(EXERCISE_ID), any())).thenReturn(session);
        when(irisMessageService.saveMessage(any(), eq(session), eq(IrisMessageSender.LLM))).thenAnswer(inv -> {
            IrisMessage m = inv.getArgument(0);
            m.setId(303L);
            return m;
        });
        var firstRow = new IrisMessage();
        firstRow.setId(303L);
        firstRow.setProactiveEpisodeId("ep-1");
        when(irisMessageRepository.findById(303L)).thenReturn(Optional.of(firstRow));

        var first = service.revealAmbient(user, EXERCISE_ID, "ep-1");
        var second = service.revealAmbient(user, EXERCISE_ID, "ep-1");

        assertThat(second.id()).isEqualTo(first.id());
        // The claim has to be persisted, not merely set in memory, or the offer would survive a restart unconsumed.
        assertThat(unconsumed.getConsumedAt()).isNotNull();
        assertThat(unconsumed.getConsumedMessageId()).isEqualTo(303L);
        verify(irisAmbientDecisionRepository).save(unconsumed);
        // Exactly one insert across both calls.
        verify(irisMessageService).saveMessage(any(), any(), any());
    }

    @Test
    void revealAmbient_blankEpisodeId_throwsBadRequest() {
        // The episode is what addresses the offer; without it there is nothing a reveal could resolve.
        assertThatThrownBy(() -> service.revealAmbient(user, EXERCISE_ID, "  ")).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.revealAmbient(user, EXERCISE_ID, null)).isInstanceOf(BadRequestException.class);
        verify(irisMessageService, never()).saveMessage(any(), any(), any());
    }

    // ---- writeEpisodeOutcome ----

    @Test
    void writeEpisodeOutcome_noRowYet_returnsFalse_deferred() {
        when(irisMessageRepository.findEpisodeRowsForUserOrderByIdAsc("ep-x", USER_ID)).thenReturn(List.of());

        boolean applied = service.writeEpisodeOutcome("ep-x", IrisProactiveOutcome.DISMISSED, USER_ID);

        assertThat(applied).isFalse();
        verify(irisMessageRepository, never()).setProactiveOutcomeIfNull(anyLong(), any());
    }

    @Test
    void writeEpisodeOutcome_rowExists_noOutcomeYet_setsOutcomeAndReturnsTrue() {
        var target = new IrisMessage();
        target.setId(500L);
        when(irisMessageRepository.findEpisodeRowsForUserOrderByIdAsc("ep-1", USER_ID)).thenReturn(List.of(target));
        when(irisMessageRepository.findEpisodeOutcomes("ep-1", USER_ID)).thenReturn(List.of());   // no outcome episode-wide yet
        when(irisMessageRepository.setProactiveOutcomeIfNull(500L, IrisProactiveOutcome.DISMISSED)).thenReturn(1);

        boolean applied = service.writeEpisodeOutcome("ep-1", IrisProactiveOutcome.DISMISSED, USER_ID);

        assertThat(applied).isTrue();
        verify(irisMessageRepository).setProactiveOutcomeIfNull(500L, IrisProactiveOutcome.DISMISSED);
    }

    @Test
    void writeEpisodeOutcome_firstTerminalAlreadySet_sameValue_isNoopReturnsTrue() {
        // The episode already holds a terminal outcome (episode-wide pre-check non-empty): re-writing is a no-op.
        var target = new IrisMessage();
        target.setId(500L);
        when(irisMessageRepository.findEpisodeRowsForUserOrderByIdAsc("ep-1", USER_ID)).thenReturn(List.of(target));
        when(irisMessageRepository.findEpisodeOutcomes("ep-1", USER_ID)).thenReturn(List.of(IrisProactiveOutcome.RECOVERED));

        boolean applied = service.writeEpisodeOutcome("ep-1", IrisProactiveOutcome.RECOVERED, USER_ID);

        assertThat(applied).isTrue();
        verify(irisMessageRepository, never()).setProactiveOutcomeIfNull(anyLong(), any());
    }

    @Test
    void writeEpisodeOutcome_differentValueIgnored_firstTerminalWins_returnsTrue() {
        // Episode already terminal (DISMISSED); a DIFFERENT value (ABANDONED) is silently ignored (first wins).
        var target = new IrisMessage();
        target.setId(500L);
        when(irisMessageRepository.findEpisodeRowsForUserOrderByIdAsc("ep-1", USER_ID)).thenReturn(List.of(target));
        when(irisMessageRepository.findEpisodeOutcomes("ep-1", USER_ID)).thenReturn(List.of(IrisProactiveOutcome.DISMISSED));

        boolean applied = service.writeEpisodeOutcome("ep-1", IrisProactiveOutcome.ABANDONED, USER_ID);

        assertThat(applied).isTrue();
        verify(irisMessageRepository, never()).setProactiveOutcomeIfNull(anyLong(), any());
    }

    @Test
    void writeEpisodeOutcome_smallestIdTargetIsStable_thenEpisodeWideNoop() {
        // The target is the smallest-id (first-persisted) row 600. A later-inserted row (larger id, even with an
        // earlier sentAt) never becomes the target. Once 600 carries the outcome, a second call is a no-op.
        var firstPersisted = new IrisMessage();
        firstPersisted.setId(600L);
        when(irisMessageRepository.findEpisodeRowsForUserOrderByIdAsc("ep-2", USER_ID)).thenReturn(List.of(firstPersisted));
        when(irisMessageRepository.findEpisodeOutcomes("ep-2", USER_ID)).thenReturn(List.of());   // first call: not terminal yet
        when(irisMessageRepository.setProactiveOutcomeIfNull(600L, IrisProactiveOutcome.DISMISSED)).thenReturn(1);

        boolean firstApplied = service.writeEpisodeOutcome("ep-2", IrisProactiveOutcome.DISMISSED, USER_ID);
        assertThat(firstApplied).isTrue();
        verify(irisMessageRepository).setProactiveOutcomeIfNull(600L, IrisProactiveOutcome.DISMISSED);

        // Second call: the episode already holds an outcome, so it is a no-op regardless of newer rows.
        when(irisMessageRepository.findEpisodeOutcomes("ep-2", USER_ID)).thenReturn(List.of(IrisProactiveOutcome.DISMISSED));

        boolean secondApplied = service.writeEpisodeOutcome("ep-2", IrisProactiveOutcome.DISMISSED, USER_ID);
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
        when(irisMessageRepository.findEpisodeRowsForUserOrderByIdAsc("ep-3", USER_ID)).thenReturn(List.of(target));
        when(irisMessageRepository.findEpisodeOutcomes("ep-3", USER_ID)).thenReturn(List.of())                      // pre-check: empty
                .thenReturn(List.of(IrisProactiveOutcome.RECOVERED));                                      // re-check: now set
        when(irisMessageRepository.setProactiveOutcomeIfNull(500L, IrisProactiveOutcome.DISMISSED)).thenReturn(0);

        boolean applied = service.writeEpisodeOutcome("ep-3", IrisProactiveOutcome.DISMISSED, USER_ID);

        assertThat(applied).isTrue();
    }

    @Test
    void writeEpisodeOutcome_targetVanished_andNoOutcomeEstablished_returnsFalseDeferred() {
        // The guarded update affects 0 rows because the target row was concurrently DELETED, and no outcome stands
        // anywhere: nothing is established, so applied = false (deferred - the client back-fills once a row exists).
        var target = new IrisMessage();
        target.setId(500L);
        when(irisMessageRepository.findEpisodeRowsForUserOrderByIdAsc("ep-4", USER_ID)).thenReturn(List.of(target));
        when(irisMessageRepository.findEpisodeOutcomes("ep-4", USER_ID)).thenReturn(List.of());   // empty on both the pre-check and the re-check
        when(irisMessageRepository.setProactiveOutcomeIfNull(500L, IrisProactiveOutcome.DISMISSED)).thenReturn(0);

        boolean applied = service.writeEpisodeOutcome("ep-4", IrisProactiveOutcome.DISMISSED, USER_ID);

        assertThat(applied).isFalse();
    }

    @Test
    void writeEpisodeOutcome_interrupted_writesToFirstRow() {
        // A delivered episode interrupted by an exercise switch persists INTERRUPTED on the smallest-id row.
        var target = new IrisMessage();
        target.setId(700L);
        when(irisMessageRepository.findEpisodeRowsForUserOrderByIdAsc("ep-int", USER_ID)).thenReturn(List.of(target));
        when(irisMessageRepository.findEpisodeOutcomes("ep-int", USER_ID)).thenReturn(List.of());
        when(irisMessageRepository.setProactiveOutcomeIfNull(700L, IrisProactiveOutcome.INTERRUPTED)).thenReturn(1);

        boolean applied = service.writeEpisodeOutcome("ep-int", IrisProactiveOutcome.INTERRUPTED, USER_ID);

        assertThat(applied).isTrue();
        verify(irisMessageRepository).setProactiveOutcomeIfNull(700L, IrisProactiveOutcome.INTERRUPTED);
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
