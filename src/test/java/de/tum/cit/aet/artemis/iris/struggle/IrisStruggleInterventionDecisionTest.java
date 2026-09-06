package de.tum.cit.aet.artemis.iris.struggle;

import static org.assertj.core.api.Assertions.assertThat;
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
import java.util.Objects;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.admin.service.LLMTokenUsageService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessage;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessageOrigin;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessageSender;
import de.tum.cit.aet.artemis.iris.domain.message.IrisProactiveEpisode;
import de.tum.cit.aet.artemis.iris.domain.message.IrisProactiveOutcome;
import de.tum.cit.aet.artemis.iris.domain.session.IrisChatMode;
import de.tum.cit.aet.artemis.iris.domain.session.IrisChatSession;
import de.tum.cit.aet.artemis.iris.repository.IrisMessageRepository;
import de.tum.cit.aet.artemis.iris.repository.IrisProactiveEpisodeRepository;
import de.tum.cit.aet.artemis.iris.repository.IrisSessionRepository;
import de.tum.cit.aet.artemis.iris.service.IrisMessageService;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisRunState;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.struggle.PyrisStruggleInterventionStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.job.StruggleInterventionJob;
import de.tum.cit.aet.artemis.iris.service.session.IrisChatSessionService;
import de.tum.cit.aet.artemis.iris.service.session.IrisProactiveEpisodeService;
import de.tum.cit.aet.artemis.iris.service.session.IrisStruggleInterventionService;
import de.tum.cit.aet.artemis.iris.service.websocket.IrisChatWebsocketService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

/**
 * Plain Mockito unit test for the decision side of {@link IrisStruggleInterventionService#handleDecision}.
 *
 * <p>
 * Contracts verified here:
 * <ul>
 * <li>active above threshold: persist with episodeId, sendMessage, emit kind="decide"/action="active" event.</li>
 * <li>active below threshold: downgrades to silent, no session created, emits kind="decide"/action="silent" noop.</li>
 * <li>ambient above threshold: NO persist (pull model), session resolved for sessionId, emits kind="decide"/action="ambient" event.</li>
 * <li>active with terminal episode: no persist, emits kind="decide"/action="silent" noop.</li>
 * <li>active resolved session not exercise-bound: defensive drop, no save, emits kind="decide"/action="silent" noop.</li>
 * <li>active non-transient persist failure: no save, still emits kind="decide"/action="active" with messageId=null.</li>
 * <li>null result: emits kind="decide"/action="silent" noop regardless of action.</li>
 * <li>active with episodeId: episodeId stamped on the persisted message.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class IrisStruggleInterventionDecisionTest {

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
    private LLMTokenUsageService llmTokenUsageService;

    private IrisStruggleInterventionService service;

    private IrisProactiveEpisodeService episodeService;

    private User user;

    // job with no episodeId (legacy / single-episode scenarios); null proactivityMode == push (no downgrade)
    private final StruggleInterventionJob job = new StruggleInterventionJob("t", 7L, 42L, 3L, null, null, null, null, null);

    // job with an explicit episodeId (episodeId-threading tests)
    private final StruggleInterventionJob jobWithEpisode = new StruggleInterventionJob("t2", 7L, 42L, 3L, "decide", "ep-123", null, null, null);

    // job in Pull (Less): an above-threshold active decision must be deterministically capped to ambient
    private final StruggleInterventionJob pullJob = new StruggleInterventionJob("tp", 7L, 42L, 3L, null, null, null, null, "pull");

    // consented follow-up: must deliver an active bubble even below threshold and even in pull
    private final StruggleInterventionJob helpRequestPullJob = new StruggleInterventionJob("th", 7L, 42L, 3L, "help_request", "ep-hr", null, null, "pull");

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(3L);
        user.setLogin("student1");
        // The episode service is the real one, built on the same mocked repositories, so the registry logic these
        // tests exercise still runs. Mocking it away would leave the assertions below asserting nothing.
        episodeService = new IrisProactiveEpisodeService(irisProactiveEpisodeRepository, irisMessageRepository, transactionManager);
        service = new IrisStruggleInterventionService(userRepository, irisChatSessionService, irisMessageService, irisChatWebsocketService, irisMessageRepository,
                transactionManager, irisSessionRepository, episodeService, llmTokenUsageService);
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
        var update = new PyrisStruggleInterventionStatusUpdateDTO("Check empty list.", "active", 0.8, "FM", PyrisRunState.FINISHED, null, List.of(), null, null, null, null, null,
                null);
        service.handleDecision(job, update);
        verify(irisMessageService).saveMessage(argThat(m -> m.getOrigin() == IrisMessageOrigin.PROACTIVE_STRUGGLE), eq(session), eq(IrisMessageSender.LLM));
        verify(irisChatWebsocketService).sendMessage(eq(session), any(), any(), any());
        // Objects.equals: sessionId is a @Nullable Long, so a regression to null fails as a clean assertion mismatch
        // rather than throwing NPE inside argThat. confidence is forwarded for the eval log.
        verify(irisChatWebsocketService).sendStruggleEvent(any(),
                argThat(e -> "active".equals(e.action()) && Objects.equals(e.sessionId(), 99L) && Objects.equals(e.messageId(), 555L) && Objects.equals(e.confidence(), 0.8)));
    }

    @Test
    void active_belowThreshold_downgradesToSilent_noSessionCreated_emitsSilentNoop() {
        var update = new PyrisStruggleInterventionStatusUpdateDTO("Check empty list.", "active", 0.4, "FM", PyrisRunState.FINISHED, null, List.of(), null, null, null, null, null,
                null);
        service.handleDecision(job, update);
        verify(irisChatSessionService, never()).getCurrentSessionOrCreateIfNotExists(any(), eq(42L), any());
        verify(irisMessageService, never()).saveMessage(any(), any(), any());
        // Silent downgrade always emits a kind="decide"/action="silent" noop so the client's in-flight clears.
        verify(irisChatWebsocketService).sendStruggleEvent(any(), argThat(e -> "decide".equals(e.kind()) && "silent".equals(e.action())));
    }

    @Test
    void ambient_aboveThreshold_emitsEventWithSessionId_noPersistedMessage() {
        // Pull model: ambient does NOT persist. Session is resolved to supply sessionId on the event.
        var session = exerciseSession(42L);
        when(irisChatSessionService.getCurrentSessionOrCreateIfNotExists(eq(IrisChatMode.PROGRAMMING_EXERCISE_CHAT), eq(42L), any())).thenReturn(session);
        var update = new PyrisStruggleInterventionStatusUpdateDTO("Re-check the logic.", "ambient", 0.7, null, PyrisRunState.FINISHED, null, List.of(), "Sort.java", 42,
                "off-by-one?", null, null, null);

        service.handleDecision(job, update);

        // ambient never saves a message row (pull model)
        verify(irisMessageService, never()).saveMessage(any(), any(), any());
        verify(irisChatWebsocketService, never()).sendMessage(any(), any(), any(), any());
        // event carries kind="decide", action="ambient", the hint text, and the resolved sessionId (no messageId)
        verify(irisChatWebsocketService).sendStruggleEvent(any(),
                argThat(e -> "decide".equals(e.kind()) && "ambient".equals(e.action()) && Objects.equals(e.message(), "Re-check the logic.") && Objects.equals(e.sessionId(), 99L)
                        && e.messageId() == null && "Sort.java".equals(e.anchorFile()) && Objects.equals(e.anchorLine(), 42) && "off-by-one?".equals(e.inlineHint())
                        && Objects.equals(e.confidence(), 0.7)));
    }

    /** A registered, open episode for the given id, with its lock stubbed and the registration refresh reporting a hit. */
    private IrisProactiveEpisode registeredEpisode(String episodeId) {
        var episode = new IrisProactiveEpisode();
        episode.setUserId(3L);
        episode.setExerciseId(42L);
        episode.setEpisodeId(episodeId);
        episode.setLastTriggeredAt(ZonedDateTime.now());
        when(irisProactiveEpisodeRepository.touchLastTriggeredAt(eq(3L), eq(42L), eq(episodeId), any())).thenReturn(1);
        when(irisProactiveEpisodeRepository.find(3L, 42L, episodeId)).thenReturn(Optional.of(episode));
        when(irisProactiveEpisodeRepository.findForUpdate(3L, 42L, episodeId)).thenReturn(Optional.of(episode));
        return episode;
    }

    @Test
    void ambient_withAnExistingOffer_refreshesTheOfferOnTheLockedEpisode() {
        // Guard against a lost update. The callback runs outside a transaction, so anything it loads is detached,
        // and saving a detached aggregate merges EVERY column: reading the decision, checking consumedAt and saving
        // that entity would overwrite a reveal committing in between, reset consumedAt and consumedMessageId to NULL,
        // and make an already-revealed offer revealable a second time.
        //
        // What this test proves: a repeat callback updates the row the caller holds the episode lock on, rather than
        // creating a second offer. It does NOT prove the database race itself - reproducing that interleaving
        // deterministically would need a @MockitoSpyBean seam, and AbstractIrisIntegrationTest is not in
        // ALLOWED_BASE_CLASSES in SpringContextConfigurationArchitectureTest, so such a seam would break the
        // ArchUnit rule.
        var session = exerciseSession(42L);
        when(irisChatSessionService.getCurrentSessionOrCreateIfNotExists(eq(IrisChatMode.PROGRAMMING_EXERCISE_CHAT), eq(42L), any())).thenReturn(session);
        var episode = registeredEpisode("ep-123");
        episode.setHintText("An older hint.");
        var update = new PyrisStruggleInterventionStatusUpdateDTO("Re-check the logic.", "ambient", 0.7, null, PyrisRunState.FINISHED, null, List.of(), null, null, null, null,
                null, null);

        service.handleDecision(jobWithEpisode, update);

        // The newest hint replaces the older one on the SAME row; no second episode row is created.
        assertThat(episode.getHintText()).isEqualTo("Re-check the logic.");
        verify(irisProactiveEpisodeRepository).save(episode);
        verify(irisProactiveEpisodeRepository, never()).saveAndFlush(any(IrisProactiveEpisode.class));
    }

    @Test
    void ambient_previousOfferAlreadyRevealed_emitsSilentNotAmbient() {
        // The episode's prior offer was already revealed, so its message exists and there is nothing fresh to
        // surface. The client is completed silently rather than pointed at a reveal that would return the stale,
        // already-used row. Overwriting the text would also rewrite history the student has already seen.
        var session = exerciseSession(42L);
        when(irisChatSessionService.getCurrentSessionOrCreateIfNotExists(eq(IrisChatMode.PROGRAMMING_EXERCISE_CHAT), eq(42L), any())).thenReturn(session);
        var episode = registeredEpisode("ep-123");
        episode.setHintText("The revealed hint.");
        episode.setConsumedAt(ZonedDateTime.now());
        episode.setConsumedMessageId(77L);
        var update = new PyrisStruggleInterventionStatusUpdateDTO("Re-check the logic.", "ambient", 0.7, null, PyrisRunState.FINISHED, null, List.of(), null, null, null, null,
                null, null);

        service.handleDecision(jobWithEpisode, update);

        assertThat(episode.getHintText()).isEqualTo("The revealed hint.");
        verify(irisProactiveEpisodeRepository, never()).save(any(IrisProactiveEpisode.class));
        verify(irisMessageService, never()).saveMessage(any(), any(), any());
        verify(irisChatWebsocketService).sendStruggleEvent(any(), argThat(e -> "decide".equals(e.kind()) && "silent".equals(e.action())));
        verify(irisChatWebsocketService, never()).sendStruggleEvent(any(), argThat(e -> "ambient".equals(e.action())));
    }

    @Test
    void ambient_overlongEpisodeId_emitsSilentNotAmbient_recordsNothing() {
        // Defence at the recording boundary (the trigger also bean-validates the id): an id past the 64-char column
        // width records nothing, so no ambient pointer is announced - a reveal would 409. Complete silently instead.
        var session = exerciseSession(42L);
        when(irisChatSessionService.getCurrentSessionOrCreateIfNotExists(eq(IrisChatMode.PROGRAMMING_EXERCISE_CHAT), eq(42L), any())).thenReturn(session);
        String overlong = "e".repeat(65);
        var overlongJob = new StruggleInterventionJob("t3", 7L, 42L, 3L, "decide", overlong, null, null, null);
        var update = new PyrisStruggleInterventionStatusUpdateDTO("Re-check the logic.", "ambient", 0.7, null, PyrisRunState.FINISHED, null, List.of(), null, null, null, null,
                null, null);

        service.handleDecision(overlongJob, update);

        // Rejected before any repository work: usableEpisodeId filters the id out before the episode is registered.
        verify(irisProactiveEpisodeRepository, never()).touchLastTriggeredAt(anyLong(), anyLong(), any(), any());
        verify(irisProactiveEpisodeRepository, never()).save(any(IrisProactiveEpisode.class));
        verify(irisChatWebsocketService).sendStruggleEvent(any(), argThat(e -> "silent".equals(e.action())));
        verify(irisChatWebsocketService, never()).sendStruggleEvent(any(), argThat(e -> "ambient".equals(e.action())));
    }

    @Test
    void ambient_blankEpisodeId_emitsSilentNotAmbient_recordsNothing() {
        // A blank id passes the trigger's @Size(max=64) but a reveal rejects it, so it must not be announced as an
        // ambient offer. Recording returns false and the client is completed silently instead.
        var session = exerciseSession(42L);
        when(irisChatSessionService.getCurrentSessionOrCreateIfNotExists(eq(IrisChatMode.PROGRAMMING_EXERCISE_CHAT), eq(42L), any())).thenReturn(session);
        var blankJob = new StruggleInterventionJob("t4", 7L, 42L, 3L, "decide", "  ", null, null, null);
        var update = new PyrisStruggleInterventionStatusUpdateDTO("Re-check the logic.", "ambient", 0.7, null, PyrisRunState.FINISHED, null, List.of(), null, null, null, null,
                null, null);

        service.handleDecision(blankJob, update);

        verify(irisProactiveEpisodeRepository, never()).touchLastTriggeredAt(anyLong(), anyLong(), any(), any());
        verify(irisProactiveEpisodeRepository, never()).save(any(IrisProactiveEpisode.class));
        verify(irisChatWebsocketService).sendStruggleEvent(any(), argThat(e -> "silent".equals(e.action())));
        verify(irisChatWebsocketService, never()).sendStruggleEvent(any(), argThat(e -> "ambient".equals(e.action())));
    }

    @Test
    void active_sessionMovedToAnotherExerciseBeforeTheAppend_doesNotPersist() {
        // Single-flight is keyed by (user, exercise), so the same student can have a second run in flight for a
        // DIFFERENT exercise. Both resolve the SAME session - a session is born a COURSE_CHAT and only points at an
        // exercise through a context switch - so that other run can switch it away between resolveProactiveSession
        // validating it and this append. The hint must not be written into the other exercise's history.
        var session = exerciseSession(42L);
        when(irisChatSessionService.getCurrentSessionOrCreateIfNotExists(eq(IrisChatMode.PROGRAMMING_EXERCISE_CHAT), eq(42L), any())).thenReturn(session);
        // Re-stubs the write-locked lookup for the same session id: under the lock it now points at another exercise.
        exerciseSession(4242L);
        var update = new PyrisStruggleInterventionStatusUpdateDTO("Check empty list.", "active", 0.9, null, PyrisRunState.FINISHED, null, List.of(), null, null, null, null, null,
                null);

        service.handleDecision(job, update);

        verify(irisMessageService, never()).saveMessage(any(), any(), any());
        verify(irisChatWebsocketService, never()).sendMessage(any(), any(), any(), any());
        // The client's in-flight decide still has to clear, so the active frame is emitted with messageId = null.
        verify(irisChatWebsocketService).sendStruggleEvent(any(), argThat(e -> "active".equals(e.action()) && e.messageId() == null));
    }

    @Test
    void active_blankEpisodeId_persistsNoEpisodeAndSkipsTheTerminalLookup() {
        // A job can carry a blank id even though the trigger endpoint now rejects one: job entries live in the
        // distributed map with a TTL, so a run minted before that validation can still be handled after a deployment.
        // A blank id must never reach the terminal-outcome lookup or the persisted column - it would key every
        // episode-scoped query, making the first blank-id episode to end swallow every later one.
        var session = exerciseSession(42L);
        when(irisChatSessionService.getCurrentSessionOrCreateIfNotExists(eq(IrisChatMode.PROGRAMMING_EXERCISE_CHAT), eq(42L), any())).thenReturn(session);
        when(irisMessageService.saveMessage(any(), any(), any())).thenAnswer(invocation -> invocation.getArgument(0));
        var blankJob = new StruggleInterventionJob("t9", 7L, 42L, 3L, "decide", "   ", null, null, null);
        var update = new PyrisStruggleInterventionStatusUpdateDTO("Re-check the logic.", "active", 0.9, null, PyrisRunState.FINISHED, null, List.of(), null, null, null, null, null,
                null);

        service.handleDecision(blankJob, update);

        verify(irisMessageRepository, never()).findEpisodeOutcomes(argThat(id -> id == null || id.isBlank()), anyLong(), anyLong());
        verify(irisMessageService).saveMessage(argThat(m -> m.getProactiveEpisodeId() == null), any(), any());
        verify(irisChatWebsocketService).sendStruggleEvent(any(), argThat(e -> "active".equals(e.action()) && e.episodeId() == null));
    }

    @Test
    void ambient_lateArrivalOnTerminalEpisode_emitsSilent_skipsRecording() {
        // The student already dismissed this episode (a terminal outcome exists). A late ambient offer must not
        // resurface: the same gate the active path applies. No recording, no ambient pointer, just a silent completion.
        when(irisMessageRepository.findEpisodeOutcomes("ep-123", 3L, 42L)).thenReturn(List.of(IrisProactiveOutcome.DISMISSED));
        var update = new PyrisStruggleInterventionStatusUpdateDTO("Re-check the logic.", "ambient", 0.7, null, PyrisRunState.FINISHED, null, List.of(), null, null, null, null,
                null, null);

        service.handleDecision(jobWithEpisode, update);

        verify(irisProactiveEpisodeRepository, never()).touchLastTriggeredAt(anyLong(), anyLong(), any(), any());
        verify(irisProactiveEpisodeRepository, never()).save(any(IrisProactiveEpisode.class));
        verify(irisChatSessionService, never()).getCurrentSessionOrCreateIfNotExists(any(), eq(42L), any());
        verify(irisChatWebsocketService).sendStruggleEvent(any(), argThat(e -> "decide".equals(e.kind()) && "silent".equals(e.action())));
        verify(irisChatWebsocketService, never()).sendStruggleEvent(any(), argThat(e -> "ambient".equals(e.action())));
    }

    @Test
    void active_aboveThreshold_pullMode_cappedToAmbient_noPersist() {
        // Less/Pull: an above-threshold ACTIVE decision is deterministically capped to ambient.
        // No message row is persisted, no chat message is sent, and the emitted event carries action="ambient".
        var session = exerciseSession(42L);
        when(irisChatSessionService.getCurrentSessionOrCreateIfNotExists(eq(IrisChatMode.PROGRAMMING_EXERCISE_CHAT), eq(42L), any())).thenReturn(session);
        var update = new PyrisStruggleInterventionStatusUpdateDTO("Re-check the logic.", "active", 0.8, "FM", PyrisRunState.FINISHED, null, List.of(), "Sort.java", 42,
                "off-by-one?", null, null, null);

        service.handleDecision(pullJob, update);

        verify(irisMessageService, never()).saveMessage(any(), any(), any());
        verify(irisChatWebsocketService, never()).sendMessage(any(), any(), any(), any());
        verify(irisChatWebsocketService).sendStruggleEvent(any(), argThat(e -> "decide".equals(e.kind()) && "ambient".equals(e.action())
                && Objects.equals(e.message(), "Re-check the logic.") && Objects.equals(e.sessionId(), 99L) && e.messageId() == null));
        // the downgrade is total: NO separate action="active" event frame is ever emitted in Pull
        verify(irisChatWebsocketService, never()).sendStruggleEvent(any(), argThat(e -> "active".equals(e.action())));
    }

    @Test
    void active_resolvedSessionNotExerciseBound_isDropped() {
        var session = exerciseSession(999L);   // defensive: resolved session not bound to job.exerciseId()
        when(irisChatSessionService.getCurrentSessionOrCreateIfNotExists(eq(IrisChatMode.PROGRAMMING_EXERCISE_CHAT), eq(42L), any())).thenReturn(session);
        var update = new PyrisStruggleInterventionStatusUpdateDTO("Check empty list.", "active", 0.9, "FM", PyrisRunState.FINISHED, null, List.of(), null, null, null, null, null,
                null);
        service.handleDecision(job, update);
        verify(irisMessageService, never()).saveMessage(any(), any(), any());
        // The not-bound drop emits a silent completion frame, so the client's in-flight request clears.
        verify(irisChatWebsocketService).sendStruggleEvent(any(), argThat(e -> "decide".equals(e.kind()) && "silent".equals(e.action())));
    }

    @Test
    void active_nonTransientPersistFailure_emitsActiveEventWithNullMessageId() {
        // A non-transient persist failure (DataIntegrityViolationException) must NOT propagate out of handleDecision.
        // The active control event must still be emitted with messageId=null + hint text so the client's in-flight
        // decide clears and can render a runtime fallback bubble.
        var session = exerciseSession(42L);
        when(irisChatSessionService.getCurrentSessionOrCreateIfNotExists(eq(IrisChatMode.PROGRAMMING_EXERCISE_CHAT), eq(42L), any())).thenReturn(session);
        when(irisMessageService.saveMessage(any(), eq(session), eq(IrisMessageSender.LLM))).thenThrow(new DataIntegrityViolationException("unique constraint violation"));
        var update = new PyrisStruggleInterventionStatusUpdateDTO("Hint text.", "active", 0.9, "FM", PyrisRunState.FINISHED, null, List.of(), null, null, null, null, null, null);

        service.handleDecision(job, update);

        // No row was saved, so sendMessage must not be called.
        verify(irisChatWebsocketService, never()).sendMessage(any(), any(), any(), any());
        // The active control event is still emitted with messageId=null and the hint text (client fallback).
        verify(irisChatWebsocketService).sendStruggleEvent(any(),
                argThat(e -> "decide".equals(e.kind()) && "active".equals(e.action()) && e.messageId() == null && "Hint text.".equals(e.message())));
    }

    @Test
    void nullResult_emitsSilentDecideEvent_noPersistedMessage() {
        // Empty/null result: a completion noop is always emitted so the client's in-flight decide clears (Critical fix).
        var update = new PyrisStruggleInterventionStatusUpdateDTO(null, "active", 0.9, "FM", PyrisRunState.FINISHED, null, List.of(), null, null, null, null, null, null);
        service.handleDecision(job, update);
        verify(irisMessageService, never()).saveMessage(any(), any(), any());
        verify(irisChatWebsocketService).sendStruggleEvent(any(), argThat(e -> "decide".equals(e.kind()) && "silent".equals(e.action())));
    }

    @Test
    void emptyResult_emitsSilentDecideEvent_noPersistedMessage() {
        var update = new PyrisStruggleInterventionStatusUpdateDTO("", "active", 0.9, "FM", PyrisRunState.FINISHED, null, List.of(), null, null, null, null, null, null);
        service.handleDecision(job, update);
        verify(irisMessageService, never()).saveMessage(any(), any(), any());
        // The confidence has to survive the empty-result path too: the client logs it for the eval even
        // when nothing is surfaced. It was dropped here while every other silent frame forwarded it, which is the
        // kind of slip a fourteen-field positional constructor invites - hence the silentDecide factory.
        verify(irisChatWebsocketService).sendStruggleEvent(any(),
                argThat(e -> "decide".equals(e.kind()) && "silent".equals(e.action()) && Double.valueOf(0.9).equals(e.confidence())));
    }

    @Test
    void active_withEpisodeId_stampsEpisodeIdOnPersistedMessage() {
        // The episodeId from the job must be set on the saved IrisMessage row.
        var session = exerciseSession(42L);
        when(irisChatSessionService.getCurrentSessionOrCreateIfNotExists(eq(IrisChatMode.PROGRAMMING_EXERCISE_CHAT), eq(42L), any())).thenReturn(session);
        when(irisMessageRepository.findEpisodeOutcomes("ep-123", 3L, 42L)).thenReturn(List.of());   // not yet terminal
        when(irisMessageService.saveMessage(any(), eq(session), eq(IrisMessageSender.LLM))).thenAnswer(inv -> {
            IrisMessage m = inv.getArgument(0);
            m.setId(777L);
            return m;
        });
        var update = new PyrisStruggleInterventionStatusUpdateDTO("Hint text.", "active", 0.9, "FM", PyrisRunState.FINISHED, null, List.of(), null, null, null, null, null, null);
        service.handleDecision(jobWithEpisode, update);
        // The persisted message must have proactiveEpisodeId set.
        verify(irisMessageService).saveMessage(argThat(m -> "ep-123".equals(m.getProactiveEpisodeId())), eq(session), eq(IrisMessageSender.LLM));
        // The active control event must carry the episodeId.
        verify(irisChatWebsocketService).sendStruggleEvent(any(),
                argThat(e -> "decide".equals(e.kind()) && "active".equals(e.action()) && Objects.equals(e.episodeId(), "ep-123") && Objects.equals(e.messageId(), 777L)));
    }

    @Test
    void active_withTerminalEpisode_emitsSilentEvent_noPersistedMessage() {
        // If the episode is already terminal (DISMISSED), a late escalation is skipped and a silent noop emitted.
        when(irisMessageRepository.findEpisodeOutcomes("ep-123", 3L, 42L)).thenReturn(List.of(IrisProactiveOutcome.DISMISSED));
        var update = new PyrisStruggleInterventionStatusUpdateDTO("Hint text.", "active", 0.9, "FM", PyrisRunState.FINISHED, null, List.of(), null, null, null, null, null, null);
        service.handleDecision(jobWithEpisode, update);
        verify(irisMessageService, never()).saveMessage(any(), any(), any());
        verify(irisChatWebsocketService).sendStruggleEvent(any(),
                argThat(e -> "decide".equals(e.kind()) && "silent".equals(e.action()) && Objects.equals(e.episodeId(), "ep-123")));
    }

    @Test
    void helpRequest_belowThreshold_stillPersistsAndPushesActive() {
        var session = exerciseSession(42L);
        when(irisChatSessionService.getCurrentSessionOrCreateIfNotExists(eq(IrisChatMode.PROGRAMMING_EXERCISE_CHAT), eq(42L), any())).thenReturn(session);
        when(irisMessageService.saveMessage(any(), eq(session), eq(IrisMessageSender.LLM))).thenAnswer(inv -> {
            IrisMessage m = inv.getArgument(0);
            m.setId(556L);
            return m;
        });
        var update = new PyrisStruggleInterventionStatusUpdateDTO("Try the empty-list case.", "active", 0.3, "FM", PyrisRunState.FINISHED, null, List.of(), null, null, null, null,
                null, null);
        service.handleDecision(helpRequestPullJob, update);
        verify(irisMessageService).saveMessage(argThat(m -> m.getOrigin() == IrisMessageOrigin.PROACTIVE_STRUGGLE), eq(session), eq(IrisMessageSender.LLM));
        verify(irisChatWebsocketService).sendMessage(eq(session), any(), any(), any());
        verify(irisChatWebsocketService).sendStruggleEvent(any(), argThat(e -> "active".equals(e.action()) && Objects.equals(e.messageId(), 556L)));
    }

    @Test
    void helpRequest_ambientAction_isCoercedToActiveBubble() {
        var session = exerciseSession(42L);
        when(irisChatSessionService.getCurrentSessionOrCreateIfNotExists(eq(IrisChatMode.PROGRAMMING_EXERCISE_CHAT), eq(42L), any())).thenReturn(session);
        when(irisMessageService.saveMessage(any(), eq(session), eq(IrisMessageSender.LLM))).thenAnswer(inv -> {
            IrisMessage m = inv.getArgument(0);
            m.setId(557L);
            return m;
        });
        var update = new PyrisStruggleInterventionStatusUpdateDTO("One notch further.", "ambient", 0.9, "FM", PyrisRunState.FINISHED, null, List.of(), null, null, null, null, null,
                null);
        service.handleDecision(helpRequestPullJob, update);
        verify(irisMessageService).saveMessage(any(), eq(session), eq(IrisMessageSender.LLM));
        verify(irisChatWebsocketService).sendStruggleEvent(any(), argThat(e -> "active".equals(e.action())));
    }

    @Test
    void helpRequest_silentFromPyris_staysSilent() {
        var update = new PyrisStruggleInterventionStatusUpdateDTO("", "silent", 0.9, "FM", PyrisRunState.FINISHED, null, List.of(), null, null, null, null, null, null);
        service.handleDecision(helpRequestPullJob, update);
        verify(irisMessageService, never()).saveMessage(any(), any(), any());
        verify(irisChatWebsocketService).sendStruggleEvent(any(), argThat(e -> "silent".equals(e.action())));
    }

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
