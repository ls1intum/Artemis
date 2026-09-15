package de.tum.cit.aet.artemis.iris.struggle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
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

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.admin.domain.LLMRequest;
import de.tum.cit.aet.artemis.admin.domain.LLMServiceType;
import de.tum.cit.aet.artemis.admin.service.LLMTokenUsageService;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
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
 * Plain Mockito unit tests for the student-action primitives: {@code revealAmbient}, {@code writeEpisodeOutcome},
 * {@code deleteSupersededProactiveMessage}, and {@code cancelOutstandingStruggleJob}.
 */
@ExtendWith(MockitoExtension.class)
class IrisStruggleInterventionPrimitivesTest {

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

    private static final long EXERCISE_ID = 42L;

    private static final long USER_ID = 3L;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(USER_ID);
        user.setLogin("student1");
        // Real episode service and real write fragments on top of the mocked repositories, so the registry logic
        // these tests exercise actually runs instead of the mocks answering null.
        IrisWriteFragments.attachTo(irisSessionRepository, irisMessageRepository, irisProactiveEpisodeRepository);
        episodeService = new IrisProactiveEpisodeService(irisProactiveEpisodeRepository, irisMessageRepository);
        // The confidence gate reads this bean, so the default 0.6 the tests assume comes from the bean's own default.
        var properties = new IrisProactiveProperties();
        service = new IrisStruggleInterventionService(userRepository, irisChatSessionService, irisChatWebsocketService, irisSessionRepository, irisProactiveEpisodeRepository,
                episodeService, llmTokenUsageService, properties);
    }

    // ---- revealAmbient ----

    // The offer row a reveal requires: it makes the persisted text the server's and the offer single-use.
    private IrisProactiveEpisode offeredEpisode(String episodeId, String serverText) {
        var episode = episodeFor(episodeId, serverText);
        // findForUpdate, because the reveal takes the episode under a write lock.
        when(irisProactiveEpisodeRepository.findForUpdate(USER_ID, EXERCISE_ID, episodeId)).thenReturn(Optional.of(episode));
        return episode;
    }

    // Unstubbed, for tests needing separate instances across two lookups so one call's in-memory mutation cannot
    // satisfy the next.
    private IrisProactiveEpisode episodeFor(String episodeId, String serverText) {
        var episode = new IrisProactiveEpisode();
        episode.setId(900L);
        episode.setUserId(USER_ID);
        episode.setExerciseId(EXERCISE_ID);
        episode.setEpisodeId(episodeId);
        episode.setHintText(serverText);
        episode.setLastTriggeredAt(ZonedDateTime.now());
        // lenient: rejection tests never reach the claim, and the rejection must come from the guard under test.
        lenient().when(irisProactiveEpisodeRepository.save(episode)).thenReturn(episode);
        return episode;
    }

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
    void revealAmbient_createsRowWithServerSentAt_andReturnsDtoWithoutSendMessage() {
        offeredEpisode("ep-1", "Re-check the loop.");
        var session = exerciseSession(EXERCISE_ID);
        when(irisChatSessionService.getCurrentSessionOrCreateIfNotExists(eq(IrisChatMode.PROGRAMMING_EXERCISE_CHAT), eq(EXERCISE_ID), any())).thenReturn(session);
        stubProactiveAppend(session, 101L);

        var dto = service.revealAmbient(user, EXERCISE_ID, "ep-1");

        assertThat(dto.id()).isEqualTo(101L);
        assertThat(dto.proactiveEpisodeId()).isEqualTo("ep-1");
        assertThat(appendedMessage).isNotNull();
        assertThat(appendedMessage.getOrigin()).isEqualTo(IrisMessageOrigin.PROACTIVE_STRUGGLE);
        assertThat(appendedMessage.getProactiveEpisodeId()).isEqualTo("ep-1");
        // CRITICAL: reveal must NOT broadcast over the chat websocket (client owns the optimistic bubble)
        verify(irisChatWebsocketService, never()).sendMessage(any(), any(), any(), any());
    }

    @Test
    void revealAmbient_sessionMovedToAnotherExercise_failsAndLeavesTheOfferUnconsumed() {
        // The episode lock says nothing about the session, so a run for another exercise can switch it between
        // resolution and write. The reveal must fail, leaving the offer unconsumed.
        var episode = offeredEpisode("ep-1", "Re-check the loop.");
        var session = exerciseSession(EXERCISE_ID);
        when(irisChatSessionService.getCurrentSessionOrCreateIfNotExists(eq(IrisChatMode.PROGRAMMING_EXERCISE_CHAT), eq(EXERCISE_ID), any())).thenReturn(session);
        // Re-stubs the write-locked lookup for the same session id: under the lock it points at another exercise.
        exerciseSession(EXERCISE_ID + 1000);

        assertThatThrownBy(() -> service.revealAmbient(user, EXERCISE_ID, "ep-1")).isInstanceOf(ConflictException.class);

        assertThat(appendedMessage).isNull();
        assertThat(episode.getConsumedAt()).as("a failed reveal must leave the offer revealable").isNull();
        assertThat(episode.getConsumedMessageId()).isNull();
    }

    @Test
    void revealAmbient_replay_returnsTheRowTheFirstRevealCreated_noDuplicate() {
        // Contract test: idempotency is scoped to (user, exercise, episode) and enforced by the episode row.
        var episode = offeredEpisode("ep-1", "Re-check the loop.");
        episode.setConsumedAt(ZonedDateTime.now());
        episode.setConsumedMessageId(101L);
        var firstReveal = new IrisMessage();
        firstReveal.setId(101L);
        firstReveal.setProactiveEpisodeId("ep-1");
        when(irisMessageRepository.findById(101L)).thenReturn(Optional.of(firstReveal));
        // A replay resolves the same session the first reveal wrote into, so it switches nothing.
        var session = exerciseSession(EXERCISE_ID);
        when(irisChatSessionService.getCurrentSessionOrCreateIfNotExists(eq(IrisChatMode.PROGRAMMING_EXERCISE_CHAT), eq(EXERCISE_ID), any())).thenReturn(session);

        var dto = service.revealAmbient(user, EXERCISE_ID, "ep-1");

        assertThat(dto.id()).isEqualTo(101L);
        // No second row: the consumed offer short-circuits before any insert.
        assertThat(appendedMessage).isNull();
    }

    @Test
    void revealAmbient_secondRevealOfTheSameEpisode_returnsTheSameRow() {
        // Two distinct episode instances on purpose: one shared object would let the first call's in-memory
        // mutation satisfy the second lookup, and the test would pass even if the claim were never persisted.
        var unconsumed = episodeFor("ep-1", "Re-check the loop.");
        var consumed = episodeFor("ep-1", "Re-check the loop.");
        consumed.setConsumedAt(ZonedDateTime.now());
        consumed.setConsumedMessageId(303L);
        when(irisProactiveEpisodeRepository.findForUpdate(USER_ID, EXERCISE_ID, "ep-1")).thenReturn(Optional.of(unconsumed), Optional.of(consumed));
        var session = exerciseSession(EXERCISE_ID);
        when(irisChatSessionService.getCurrentSessionOrCreateIfNotExists(eq(IrisChatMode.PROGRAMMING_EXERCISE_CHAT), eq(EXERCISE_ID), any())).thenReturn(session);
        stubProactiveAppend(session, 303L);
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
        assertThat(appendedMessage).isNotNull();
    }

    @Test
    void revealAmbient_blankEpisodeId_throwsBadRequest() {
        // The episode is what addresses the offer; without it there is nothing a reveal could resolve.
        assertThatThrownBy(() -> service.revealAmbient(user, EXERCISE_ID, "  ")).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.revealAmbient(user, EXERCISE_ID, null)).isInstanceOf(BadRequestException.class);
        assertThat(appendedMessage).isNull();
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
    void writeEpisodeOutcome_registered_mirrorSkipsARowDeletedUnderIt() {
        // The mirror runs holding the EPISODE row locked, while deleteSupersededProactiveMessage holds the SESSION
        // row, so a superseded-hint delete can take the row this projection aimed at between the read and the write.
        // Giving up on the zero-row update would leave a surviving message of the episode without its outcome, and a
        // dismissed hint replayed to Pyris without its dismissed tag is one the pipeline is free to repeat.
        when(irisProactiveEpisodeRepository.findForUpdate(USER_ID, EXERCISE_ID, "ep-race")).thenReturn(Optional.of(registeredEpisode("ep-race")));
        when(irisMessageRepository.findEpisodeRowIdsForUserOrderByIdAsc("ep-race", USER_ID, EXERCISE_ID)).thenReturn(List.of(100L, 200L));
        when(irisMessageRepository.findEpisodeOutcomes("ep-race", USER_ID, EXERCISE_ID)).thenReturn(List.of());
        when(irisMessageRepository.setProactiveOutcomeIfNull(100L, IrisProactiveOutcome.DISMISSED)).thenReturn(0);   // deleted under us
        when(irisMessageRepository.setProactiveOutcomeIfNull(200L, IrisProactiveOutcome.DISMISSED)).thenReturn(1);

        boolean established = episodeService.writeEpisodeOutcome("ep-race", IrisProactiveOutcome.DISMISSED, USER_ID, EXERCISE_ID);

        assertThat(established).isTrue();
        InOrder order = inOrder(irisMessageRepository);
        order.verify(irisMessageRepository).setProactiveOutcomeIfNull(100L, IrisProactiveOutcome.DISMISSED);
        order.verify(irisMessageRepository).setProactiveOutcomeIfNull(200L, IrisProactiveOutcome.DISMISSED);
    }

    @Test
    void writeEpisodeOutcome_registered_mirrorStopsAtTheRowItWrote() {
        // One outcome row per episode: the walk exists for the deleted-row case, not to stamp every candidate.
        when(irisProactiveEpisodeRepository.findForUpdate(USER_ID, EXERCISE_ID, "ep-one")).thenReturn(Optional.of(registeredEpisode("ep-one")));
        when(irisMessageRepository.findEpisodeRowIdsForUserOrderByIdAsc("ep-one", USER_ID, EXERCISE_ID)).thenReturn(List.of(100L, 200L));
        when(irisMessageRepository.findEpisodeOutcomes("ep-one", USER_ID, EXERCISE_ID)).thenReturn(List.of());
        when(irisMessageRepository.setProactiveOutcomeIfNull(100L, IrisProactiveOutcome.DISMISSED)).thenReturn(1);

        episodeService.writeEpisodeOutcome("ep-one", IrisProactiveOutcome.DISMISSED, USER_ID, EXERCISE_ID);

        verify(irisMessageRepository).setProactiveOutcomeIfNull(100L, IrisProactiveOutcome.DISMISSED);
        verify(irisMessageRepository, never()).setProactiveOutcomeIfNull(eq(200L), any());
    }

    @Test
    void writeEpisodeOutcome_registered_mirrorWritesNothingWhenTheEpisodeAlreadyCarriesAnOutcome() {
        // Without the episode-wide pre-check, a row that already carries an outcome reports zero rows exactly like
        // a deleted one, and the walk would stamp a second row.
        when(irisProactiveEpisodeRepository.findForUpdate(USER_ID, EXERCISE_ID, "ep-done")).thenReturn(Optional.of(registeredEpisode("ep-done")));
        when(irisMessageRepository.findEpisodeRowIdsForUserOrderByIdAsc("ep-done", USER_ID, EXERCISE_ID)).thenReturn(List.of(100L, 200L));
        when(irisMessageRepository.findEpisodeOutcomes("ep-done", USER_ID, EXERCISE_ID)).thenReturn(List.of(IrisProactiveOutcome.RECOVERED));

        episodeService.writeEpisodeOutcome("ep-done", IrisProactiveOutcome.DISMISSED, USER_ID, EXERCISE_ID);

        verify(irisMessageRepository, never()).setProactiveOutcomeIfNull(anyLong(), any());
    }

    /** A registry row that stands for the given episode and carries no outcome yet. */
    private IrisProactiveEpisode registeredEpisode(String episodeId) {
        var episode = new IrisProactiveEpisode();
        episode.setId(7L);
        episode.setUserId(USER_ID);
        episode.setExerciseId(EXERCISE_ID);
        episode.setEpisodeId(episodeId);
        return episode;
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
        // The target is the smallest-id row 600; a later-inserted row never becomes the target.
        when(irisMessageRepository.findEpisodeRowIdsForUserOrderByIdAsc("ep-2", USER_ID, EXERCISE_ID)).thenReturn(List.of(600L));
        when(irisMessageRepository.findEpisodeOutcomes("ep-2", USER_ID, EXERCISE_ID)).thenReturn(List.of());   // first call: not terminal yet
        when(irisMessageRepository.setProactiveOutcomeIfNull(600L, IrisProactiveOutcome.DISMISSED)).thenReturn(1);

        boolean firstApplied = episodeService.writeEpisodeOutcome("ep-2", IrisProactiveOutcome.DISMISSED, USER_ID, EXERCISE_ID);
        assertThat(firstApplied).isTrue();
        verify(irisMessageRepository).setProactiveOutcomeIfNull(600L, IrisProactiveOutcome.DISMISSED);

        // The episode already holds an outcome, so the second call is a no-op regardless of newer rows.
        when(irisMessageRepository.findEpisodeOutcomes("ep-2", USER_ID, EXERCISE_ID)).thenReturn(List.of(IrisProactiveOutcome.DISMISSED));

        boolean secondApplied = episodeService.writeEpisodeOutcome("ep-2", IrisProactiveOutcome.DISMISSED, USER_ID, EXERCISE_ID);
        assertThat(secondApplied).isTrue();

        // Invoked exactly once across both calls.
        verify(irisMessageRepository).setProactiveOutcomeIfNull(anyLong(), any());
    }

    @Test
    void writeEpisodeOutcome_targetVanished_butOutcomeNowExists_returnsTrue() {
        // The guarded update affects 0 rows because the target was concurrently given an outcome. The re-check is a
        // locking read, because a plain one would answer from a snapshot predating the write that won the row.
        when(irisMessageRepository.findEpisodeRowIdsForUserOrderByIdAsc("ep-3", USER_ID, EXERCISE_ID)).thenReturn(List.of(500L));
        when(irisMessageRepository.findEpisodeOutcomes("ep-3", USER_ID, EXERCISE_ID)).thenReturn(List.of());                     // pre-check: empty
        when(irisMessageRepository.findEpisodeOutcomesForUpdate("ep-3", USER_ID, EXERCISE_ID)).thenReturn(List.of(IrisProactiveOutcome.RECOVERED)); // re-check: now set
        when(irisMessageRepository.setProactiveOutcomeIfNull(500L, IrisProactiveOutcome.DISMISSED)).thenReturn(0);

        boolean applied = episodeService.writeEpisodeOutcome("ep-3", IrisProactiveOutcome.DISMISSED, USER_ID, EXERCISE_ID);

        assertThat(applied).isTrue();
    }

    @Test
    void writeEpisodeOutcome_targetVanished_andNoOutcomeEstablished_returnsFalseDeferred() {
        // The target row was concurrently deleted and no outcome stands anywhere, so the client back-fills later.
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
    // The guards live in one atomic statement and are verified end-to-end in the integration test. What is worth
    // asserting here is the sequence around it: the session lock, and the compaction that must follow a delete.

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
        // The guards rejected the row, so it still holds its index and renumbering around it would corrupt.
        when(irisMessageRepository.findOwnedSessionId(77L, USER_ID)).thenReturn(Optional.of(99L));
        when(irisMessageRepository.findListIndex(77L)).thenReturn(Optional.of(1));
        when(irisMessageRepository.deleteSupersededProactiveMessage(77L, USER_ID)).thenReturn(0);

        service.deleteSupersededProactiveMessage(user, 77L);

        verify(irisMessageRepository, never()).compactMessageOrderAfter(anyLong(), anyInt());
    }

    @Test
    void deleteSupersededProactiveMessage_foreignOrMissingRow_locksNothing() {
        // No session id means the row is missing or someone else's, and no lock may be taken.
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
        // The append re-reads the session under a write lock, so hand the same instance back. Lenient because the
        // paths that never persist do not reach it.
        lenient().when(irisSessionRepository.findByIdWithWriteLockElseThrow(session.getId())).thenReturn(session);
        return session;
    }
}
