package de.tum.cit.aet.artemis.iris.struggle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import jakarta.ws.rs.BadRequestException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.service.UserAiPreferenceService;
import de.tum.cit.aet.artemis.account.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.admin.domain.LLMRequest;
import de.tum.cit.aet.artemis.admin.domain.LLMServiceType;
import de.tum.cit.aet.artemis.admin.service.LLMTokenUsageService;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessage;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessageOrigin;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessageSender;
import de.tum.cit.aet.artemis.iris.domain.message.IrisProactiveEpisode;
import de.tum.cit.aet.artemis.iris.domain.message.IrisProactiveOutcome;
import de.tum.cit.aet.artemis.iris.domain.session.IrisChatMode;
import de.tum.cit.aet.artemis.iris.domain.session.IrisChatSession;
import de.tum.cit.aet.artemis.iris.repository.IrisChatSessionRepository;
import de.tum.cit.aet.artemis.iris.repository.IrisMessageRepository;
import de.tum.cit.aet.artemis.iris.repository.IrisProactiveEpisodeRepository;
import de.tum.cit.aet.artemis.iris.repository.IrisSessionRepository;
import de.tum.cit.aet.artemis.iris.service.IrisMessageService;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisDTOService;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisJobService;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisPipelineService;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisRunState;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.struggle.PyrisStruggleInterventionStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.job.StruggleInterventionJob;
import de.tum.cit.aet.artemis.iris.service.session.IrisChatSessionService;
import de.tum.cit.aet.artemis.iris.service.session.IrisProactiveEpisodeService;
import de.tum.cit.aet.artemis.iris.service.session.IrisStruggleInterventionService;
import de.tum.cit.aet.artemis.iris.service.settings.IrisSettingsService;
import de.tum.cit.aet.artemis.iris.service.websocket.IrisChatWebsocketService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestRepository;

/**
 * Plain Mockito unit tests for the student-action primitives: {@code revealAmbient}, {@code writeEpisodeOutcome},
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
    private PlatformTransactionManager transactionManager;

    @Mock
    private IrisSessionRepository irisSessionRepository;

    @Mock
    private IrisProactiveEpisodeRepository irisProactiveEpisodeRepository;

    @Mock
    private UserAiPreferenceService userAiPreferenceService;

    @Mock
    private LLMTokenUsageService llmTokenUsageService;

    private IrisStruggleInterventionService service;

    private IrisProactiveEpisodeService episodeService;

    private User user;

    private static final long EXERCISE_ID = 42L;

    private static final long USER_ID = 3L;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(USER_ID);
        user.setLogin("student1");
        // The episode service is the real one, built on the same mocked repositories, so the registry logic these
        // tests exercise still runs. Mocking it away would leave the assertions below asserting nothing.
        episodeService = new IrisProactiveEpisodeService(irisProactiveEpisodeRepository, irisMessageRepository, transactionManager);
        service = new IrisStruggleInterventionService(userRepository, irisChatSessionService, irisMessageService, irisChatWebsocketService, irisMessageRepository,
                transactionManager, irisSessionRepository, episodeService, llmTokenUsageService);
        ReflectionTestUtils.setField(service, "confidenceThreshold", 0.6);
    }

    // ---- revealAmbient ----

    /**
     * Stub the episode carrying the offer Artemis recorded when it emitted the pointer. A reveal requires one: the
     * row is what makes the persisted text the server's rather than the caller's, and what makes the offer
     * single-use.
     *
     * @param episodeId  the episode the offer belongs to
     * @param serverText the hint text as authored by Pyris
     * @return the stubbed episode
     */
    private IrisProactiveEpisode offeredEpisode(String episodeId, String serverText) {
        var episode = episodeFor(episodeId, serverText);
        // findForUpdate, not the plain finder: the reveal takes the episode under a write lock so the terminal
        // check, the unconsumed check and the claim cannot interleave with anything writing to the same episode.
        when(irisProactiveEpisodeRepository.findForUpdate(USER_ID, EXERCISE_ID, episodeId)).thenReturn(Optional.of(episode));
        return episode;
    }

    /**
     * Build an unstubbed episode carrying an offer. Used where a test needs SEPARATE instances across two lookups,
     * so one call's in-memory mutation cannot silently satisfy the next one.
     *
     * @param episodeId  the episode the offer belongs to
     * @param serverText the hint text as authored by Pyris
     * @return the episode, not wired into any lookup stub
     */
    private IrisProactiveEpisode episodeFor(String episodeId, String serverText) {
        var episode = new IrisProactiveEpisode();
        episode.setId(900L);
        episode.setUserId(USER_ID);
        episode.setExerciseId(EXERCISE_ID);
        episode.setEpisodeId(episodeId);
        episode.setHintText(serverText);
        episode.setLastTriggeredAt(ZonedDateTime.now());
        // lenient: the tests that assert a rejection never reach the claim, and an offered episode is still the
        // correct precondition for them - the rejection must come from the guard under test, not from a missing offer.
        lenient().when(irisProactiveEpisodeRepository.save(episode)).thenReturn(episode);
        return episode;
    }

    @Test
    void revealAmbient_createsRowWithServerSentAt_andReturnsDtoWithoutSendMessage() {
        offeredEpisode("ep-1", "Re-check the loop.");
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
    void revealAmbient_sessionMovedToAnotherExercise_failsAndLeavesTheOfferUnconsumed() {
        // The episode lock says nothing about the session. A run for a DIFFERENT exercise can switch the same
        // session between resolving it and the write, and the hint would land in that exercise's history.
        // The reveal must fail instead, so the offer stays unconsumed and the student can reveal it again later.
        var episode = offeredEpisode("ep-1", "Re-check the loop.");
        var session = exerciseSession(EXERCISE_ID);
        when(irisChatSessionService.getCurrentSessionOrCreateIfNotExists(eq(IrisChatMode.PROGRAMMING_EXERCISE_CHAT), eq(EXERCISE_ID), any())).thenReturn(session);
        // Re-stubs the write-locked lookup for the same session id: under the lock it points at another exercise.
        exerciseSession(EXERCISE_ID + 1000);

        assertThatThrownBy(() -> service.revealAmbient(user, EXERCISE_ID, "ep-1")).isInstanceOf(ConflictException.class);

        verify(irisMessageService, never()).saveMessage(any(), any(), any());
        assertThat(episode.getConsumedAt()).as("a failed reveal must leave the offer revealable").isNull();
        assertThat(episode.getConsumedMessageId()).isNull();
    }

    @Test
    void revealAmbient_replay_returnsTheRowTheFirstRevealCreated_noDuplicate() {
        // Contract test, not a red proof: idempotency is scoped to (user, exercise, episode) and enforced by the
        // episode row. A replay finds the offer already consumed and resolves the row that reveal created.
        var episode = offeredEpisode("ep-1", "Re-check the loop.");
        episode.setConsumedAt(ZonedDateTime.now());
        episode.setConsumedMessageId(101L);
        var firstReveal = new IrisMessage();
        firstReveal.setId(101L);
        firstReveal.setProactiveEpisodeId("ep-1");
        when(irisMessageRepository.findById(101L)).thenReturn(Optional.of(firstReveal));
        // The session is resolved before the transaction now, so a replay resolves it too. It is the session the
        // first reveal already wrote into, so this finds it and switches nothing.
        var session = exerciseSession(EXERCISE_ID);
        when(irisChatSessionService.getCurrentSessionOrCreateIfNotExists(eq(IrisChatMode.PROGRAMMING_EXERCISE_CHAT), eq(EXERCISE_ID), any())).thenReturn(session);

        var dto = service.revealAmbient(user, EXERCISE_ID, "ep-1");

        assertThat(dto.id()).isEqualTo(101L);
        // No second row: the consumed offer short-circuits before any insert.
        verify(irisMessageService, never()).saveMessage(any(), any(), any());
    }

    @Test
    void revealAmbient_secondRevealOfTheSameEpisode_returnsTheSameRow() {
        // The episode row makes the offer single-use, so a second call must resolve the FIRST reveal's row instead
        // of inserting another one, however the client varies its message id.
        //
        // Two DISTINCT episode instances on purpose. Returning one shared object would let the first call's in-memory
        // mutation satisfy the second lookup, and the test would still pass if the persist of the claim were deleted.
        var unconsumed = episodeFor("ep-1", "Re-check the loop.");
        var consumed = episodeFor("ep-1", "Re-check the loop.");
        consumed.setConsumedAt(ZonedDateTime.now());
        consumed.setConsumedMessageId(303L);
        when(irisProactiveEpisodeRepository.findForUpdate(USER_ID, EXERCISE_ID, "ep-1")).thenReturn(Optional.of(unconsumed), Optional.of(consumed));
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
        verify(irisProactiveEpisodeRepository).save(unconsumed);
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

    // ---- recordTokenUsage ----

    @Test
    void recordTokenUsage_attributesTheSpendToCourseExerciseAndUser() {
        var job = new StruggleInterventionJob("t", 7L, EXERCISE_ID, USER_ID, "decide", "ep-1", null, null, null);
        var request = new LLMRequest("gpt-4", 100, 0.5f, 40, 1.5f, "struggle-intervention");
        var update = statusUpdateWithTokens(List.of(request));

        service.recordTokenUsage(job, update);

        ArgumentCaptor<Function<LLMTokenUsageService.LLMTokenUsageBuilder, LLMTokenUsageService.LLMTokenUsageBuilder>> builder = ArgumentCaptor.forClass(Function.class);
        verify(llmTokenUsageService).saveLLMTokenUsage(eq(List.of(request)), eq(LLMServiceType.IRIS), builder.capture());
        // The scope the admin view groups by. Taken from the job rather than from a message, because silent, ambient
        // and quiet-close runs never persist one and their cost must still be counted.
        var applied = builder.getValue().apply(new LLMTokenUsageService.LLMTokenUsageBuilder());
        assertThat(applied.getCourseID()).contains(7L);
        assertThat(applied.getExerciseID()).contains(EXERCISE_ID);
        assertThat(applied.getUserID()).contains(USER_ID);
    }

    @Test
    void recordTokenUsage_withoutTokens_writesNothing() {
        var job = new StruggleInterventionJob("t", 7L, EXERCISE_ID, USER_ID, "decide", "ep-1", null, null, null);

        service.recordTokenUsage(job, statusUpdateWithTokens(List.of()));

        verify(llmTokenUsageService, never()).saveLLMTokenUsage(any(), any(), any());
    }

    @Test
    void recordTokenUsage_whenAccountingFails_doesNotBreakTheCallback() {
        // The caller has already claimed and removed the job, so an exception escaping here would hang the client's
        // in-flight request. Losing one accounting row is the lesser cost.
        var job = new StruggleInterventionJob("t", 7L, EXERCISE_ID, USER_ID, "decide", "ep-1", null, null, null);
        var update = statusUpdateWithTokens(List.of(new LLMRequest("gpt-4", 100, 0.5f, 40, 1.5f, "struggle-intervention")));
        when(llmTokenUsageService.saveLLMTokenUsage(any(), any(), any())).thenThrow(new DataIntegrityViolationException("accounting is down"));

        assertThatCode(() -> service.recordTokenUsage(job, update)).doesNotThrowAnyException();
    }

    private PyrisStruggleInterventionStatusUpdateDTO statusUpdateWithTokens(List<LLMRequest> tokens) {
        return new PyrisStruggleInterventionStatusUpdateDTO("hint", "active", 0.8, null, PyrisRunState.FINISHED, null, tokens, null, null, null, null, null, null);
    }

    // ---- writeEpisodeOutcome ----

    @Test
    void writeEpisodeOutcome_noRowYet_returnsFalse_deferred() {
        when(irisMessageRepository.findEpisodeRowIdsForUserOrderByIdAsc("ep-x", USER_ID, EXERCISE_ID)).thenReturn(List.of());

        boolean applied = episodeService.writeEpisodeOutcome("ep-x", IrisProactiveOutcome.DISMISSED, USER_ID, EXERCISE_ID);

        assertThat(applied).isFalse();
        verify(irisMessageRepository, never()).setProactiveOutcomeIfNull(anyLong(), any());
    }

    @Test
    void writeEpisodeOutcome_rowExists_noOutcomeYet_setsOutcomeAndReturnsTrue() {
        when(irisMessageRepository.findEpisodeRowIdsForUserOrderByIdAsc("ep-1", USER_ID, EXERCISE_ID)).thenReturn(List.of(500L));
        when(irisMessageRepository.findEpisodeOutcomes("ep-1", USER_ID, EXERCISE_ID)).thenReturn(List.of());   // no outcome episode-wide yet
        when(irisMessageRepository.setProactiveOutcomeIfNull(500L, IrisProactiveOutcome.DISMISSED)).thenReturn(1);

        boolean applied = episodeService.writeEpisodeOutcome("ep-1", IrisProactiveOutcome.DISMISSED, USER_ID, EXERCISE_ID);

        assertThat(applied).isTrue();
        verify(irisMessageRepository).setProactiveOutcomeIfNull(500L, IrisProactiveOutcome.DISMISSED);
    }

    @Test
    void writeEpisodeOutcome_firstTerminalAlreadySet_sameValue_isNoopReturnsTrue() {
        // The episode already holds a terminal outcome (episode-wide pre-check non-empty): re-writing is a no-op.
        when(irisMessageRepository.findEpisodeRowIdsForUserOrderByIdAsc("ep-1", USER_ID, EXERCISE_ID)).thenReturn(List.of(500L));
        when(irisMessageRepository.findEpisodeOutcomes("ep-1", USER_ID, EXERCISE_ID)).thenReturn(List.of(IrisProactiveOutcome.RECOVERED));

        boolean applied = episodeService.writeEpisodeOutcome("ep-1", IrisProactiveOutcome.RECOVERED, USER_ID, EXERCISE_ID);

        assertThat(applied).isTrue();
        verify(irisMessageRepository, never()).setProactiveOutcomeIfNull(anyLong(), any());
    }

    @Test
    void writeEpisodeOutcome_differentValueIgnored_firstTerminalWins_returnsTrue() {
        // Episode already terminal (DISMISSED); a DIFFERENT value (ABANDONED) is silently ignored (first wins).
        when(irisMessageRepository.findEpisodeRowIdsForUserOrderByIdAsc("ep-1", USER_ID, EXERCISE_ID)).thenReturn(List.of(500L));
        when(irisMessageRepository.findEpisodeOutcomes("ep-1", USER_ID, EXERCISE_ID)).thenReturn(List.of(IrisProactiveOutcome.DISMISSED));

        boolean applied = episodeService.writeEpisodeOutcome("ep-1", IrisProactiveOutcome.ABANDONED, USER_ID, EXERCISE_ID);

        assertThat(applied).isTrue();
        verify(irisMessageRepository, never()).setProactiveOutcomeIfNull(anyLong(), any());
    }

    @Test
    void writeEpisodeOutcome_smallestIdTargetIsStable_thenEpisodeWideNoop() {
        // The target is the smallest-id (first-persisted) row 600. A later-inserted row (larger id, even with an
        // earlier sentAt) never becomes the target. Once 600 carries the outcome, a second call is a no-op.
        when(irisMessageRepository.findEpisodeRowIdsForUserOrderByIdAsc("ep-2", USER_ID, EXERCISE_ID)).thenReturn(List.of(600L));
        when(irisMessageRepository.findEpisodeOutcomes("ep-2", USER_ID, EXERCISE_ID)).thenReturn(List.of());   // first call: not terminal yet
        when(irisMessageRepository.setProactiveOutcomeIfNull(600L, IrisProactiveOutcome.DISMISSED)).thenReturn(1);

        boolean firstApplied = episodeService.writeEpisodeOutcome("ep-2", IrisProactiveOutcome.DISMISSED, USER_ID, EXERCISE_ID);
        assertThat(firstApplied).isTrue();
        verify(irisMessageRepository).setProactiveOutcomeIfNull(600L, IrisProactiveOutcome.DISMISSED);

        // Second call: the episode already holds an outcome, so it is a no-op regardless of newer rows.
        when(irisMessageRepository.findEpisodeOutcomes("ep-2", USER_ID, EXERCISE_ID)).thenReturn(List.of(IrisProactiveOutcome.DISMISSED));

        boolean secondApplied = episodeService.writeEpisodeOutcome("ep-2", IrisProactiveOutcome.DISMISSED, USER_ID, EXERCISE_ID);
        assertThat(secondApplied).isTrue();

        // setProactiveOutcomeIfNull is invoked exactly once across both calls (only the first call writes).
        verify(irisMessageRepository).setProactiveOutcomeIfNull(anyLong(), any());
    }

    @Test
    void writeEpisodeOutcome_targetVanished_butOutcomeNowExists_returnsTrue() {
        // The guarded update affects 0 rows because the target was concurrently given an outcome; the re-check finds
        // an episode-wide outcome, so applied = true. The re-check is a LOCKING read: the plain one would answer from
        // this transaction's snapshot, which predates the write that just won the row.
        when(irisMessageRepository.findEpisodeRowIdsForUserOrderByIdAsc("ep-3", USER_ID, EXERCISE_ID)).thenReturn(List.of(500L));
        when(irisMessageRepository.findEpisodeOutcomes("ep-3", USER_ID, EXERCISE_ID)).thenReturn(List.of());                     // pre-check: empty
        when(irisMessageRepository.findEpisodeOutcomesForUpdate("ep-3", USER_ID, EXERCISE_ID)).thenReturn(List.of(IrisProactiveOutcome.RECOVERED)); // re-check: now set
        when(irisMessageRepository.setProactiveOutcomeIfNull(500L, IrisProactiveOutcome.DISMISSED)).thenReturn(0);

        boolean applied = episodeService.writeEpisodeOutcome("ep-3", IrisProactiveOutcome.DISMISSED, USER_ID, EXERCISE_ID);

        assertThat(applied).isTrue();
    }

    @Test
    void writeEpisodeOutcome_targetVanished_andNoOutcomeEstablished_returnsFalseDeferred() {
        // The guarded update affects 0 rows because the target row was concurrently DELETED, and no outcome stands
        // anywhere: nothing is established, so applied = false (deferred - the client back-fills once a row exists).
        when(irisMessageRepository.findEpisodeRowIdsForUserOrderByIdAsc("ep-4", USER_ID, EXERCISE_ID)).thenReturn(List.of(500L));
        when(irisMessageRepository.findEpisodeOutcomes("ep-4", USER_ID, EXERCISE_ID)).thenReturn(List.of());          // pre-check: empty
        when(irisMessageRepository.findEpisodeOutcomesForUpdate("ep-4", USER_ID, EXERCISE_ID)).thenReturn(List.of()); // re-check under lock: still empty
        when(irisMessageRepository.setProactiveOutcomeIfNull(500L, IrisProactiveOutcome.DISMISSED)).thenReturn(0);

        boolean applied = episodeService.writeEpisodeOutcome("ep-4", IrisProactiveOutcome.DISMISSED, USER_ID, EXERCISE_ID);

        assertThat(applied).isFalse();
    }

    @Test
    void writeEpisodeOutcome_interrupted_writesToFirstRow() {
        // A delivered episode interrupted by an exercise switch persists INTERRUPTED on the smallest-id row.
        when(irisMessageRepository.findEpisodeRowIdsForUserOrderByIdAsc("ep-int", USER_ID, EXERCISE_ID)).thenReturn(List.of(700L));
        when(irisMessageRepository.findEpisodeOutcomes("ep-int", USER_ID, EXERCISE_ID)).thenReturn(List.of());
        when(irisMessageRepository.setProactiveOutcomeIfNull(700L, IrisProactiveOutcome.INTERRUPTED)).thenReturn(1);

        boolean applied = episodeService.writeEpisodeOutcome("ep-int", IrisProactiveOutcome.INTERRUPTED, USER_ID, EXERCISE_ID);

        assertThat(applied).isTrue();
        verify(irisMessageRepository).setProactiveOutcomeIfNull(700L, IrisProactiveOutcome.INTERRUPTED);
    }

    // ---- deleteSupersededProactiveMessage ----
    // The guard logic (proactive-origin AND null outcome AND user ownership) lives in ONE atomic SQL statement, so it
    // cannot be meaningfully exercised against a mock; the guards are verified end-to-end in the integration test
    // (the real database enforces the WHERE). What is worth asserting here is the sequence around that statement:
    // the session lock, and the list compaction that must follow a delete and only a delete.

    @Test
    void deleteSupersededProactiveMessage_compactsTheListUnderTheSessionLock() {
        when(irisMessageRepository.findOwnedSessionId(77L, USER_ID)).thenReturn(Optional.of(99L));
        when(irisMessageRepository.findListIndex(77L)).thenReturn(Optional.of(1));
        when(irisMessageRepository.deleteSupersededProactiveMessage(77L, USER_ID)).thenReturn(1);

        service.deleteSupersededProactiveMessage(user, 77L);

        // The order is the point: the lock is taken before anything is read or written, and the list is only
        // renumbered after the row is actually gone.
        InOrder order = inOrder(irisSessionRepository, irisMessageRepository);
        order.verify(irisSessionRepository).findByIdWithWriteLockElseThrow(99L);
        order.verify(irisMessageRepository).deleteSupersededProactiveMessage(77L, USER_ID);
        order.verify(irisMessageRepository).compactMessageOrderAfter(99L, 1);
    }

    @Test
    void deleteSupersededProactiveMessage_rowFailedTheGuards_leavesTheOrderAlone() {
        // The delete's own guards rejected the row (wrong origin, or an outcome landed on it), so it is still there
        // and still holds its index. Renumbering around a row that stayed would be the corruption, not the fix.
        when(irisMessageRepository.findOwnedSessionId(77L, USER_ID)).thenReturn(Optional.of(99L));
        when(irisMessageRepository.findListIndex(77L)).thenReturn(Optional.of(1));
        when(irisMessageRepository.deleteSupersededProactiveMessage(77L, USER_ID)).thenReturn(0);

        service.deleteSupersededProactiveMessage(user, 77L);

        verify(irisMessageRepository, never()).compactMessageOrderAfter(anyLong(), anyInt());
    }

    @Test
    void deleteSupersededProactiveMessage_foreignOrMissingRow_locksNothing() {
        // No session id means the row does not exist or belongs to someone else. Silent noop, and notably it must
        // not take a write lock on a session the caller named indirectly.
        service.deleteSupersededProactiveMessage(user, 77L);

        verify(irisSessionRepository, never()).findByIdWithWriteLockElseThrow(anyLong());
        verify(irisMessageRepository, never()).deleteSupersededProactiveMessage(anyLong(), anyLong());
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
        // The proactive append re-reads the session under a write lock and re-checks its exercise binding before
        // writing, so hand the same instance back for that lookup. Lenient because the paths that never persist
        // (ambient, silent, early drops) do not reach it.
        lenient().when(irisSessionRepository.findByIdWithWriteLockElseThrow(session.getId())).thenReturn(session);
        return session;
    }
}
