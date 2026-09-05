package de.tum.cit.aet.artemis.iris.service.session;

import java.util.Objects;

import jakarta.ws.rs.BadRequestException;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.admin.domain.LLMServiceType;
import de.tum.cit.aet.artemis.admin.service.LLMTokenUsageService;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.iris.config.IrisEnabled;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessage;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessageOrigin;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessageSender;
import de.tum.cit.aet.artemis.iris.domain.message.IrisProactiveOutcome;
import de.tum.cit.aet.artemis.iris.domain.message.IrisTextMessageContent;
import de.tum.cit.aet.artemis.iris.domain.session.IrisChatMode;
import de.tum.cit.aet.artemis.iris.domain.session.IrisChatSession;
import de.tum.cit.aet.artemis.iris.dto.IrisMessageResponseDTO;
import de.tum.cit.aet.artemis.iris.dto.StruggleEpisodeDTO;
import de.tum.cit.aet.artemis.iris.dto.StruggleInterventionEventDTO;
import de.tum.cit.aet.artemis.iris.repository.IrisMessageRepository;
import de.tum.cit.aet.artemis.iris.repository.IrisSessionRepository;
import de.tum.cit.aet.artemis.iris.service.IrisMessageService;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisRunState;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.struggle.PyrisStruggleInterventionStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.job.StruggleInterventionJob;
import de.tum.cit.aet.artemis.iris.service.websocket.IrisChatWebsocketService;

/**
 * Applies Iris' gated result for the proactive struggle-intervention feature (spec §4): what happens once the Pyris
 * pipeline calls back. Detection stays in the client engine, and the other end of a run, authorizing the student and
 * shipping the live code and signal to Pyris, is {@link IrisStruggleTriggerService}. The episode's registry row and
 * the writes that settle its outcome belong to {@link IrisProactiveEpisodeService}; this service orchestrates them
 * and owns the chat-message persistence around them.
 *
 * <p>
 * After the pull-model change (spec §5, A9) {@code ambient} is event-only: no message row is persisted until the
 * student clicks (A10 {@code revealAmbient} handles that). {@code active} persists a message and pushes it live over
 * the socket. {@code silent} (and empty results) always emit a noop completion event so the client's in-flight
 * {@code decide} always clears.
 */
@Lazy
@Service
@Conditional(IrisEnabled.class)
public class IrisStruggleInterventionService {

    private static final Logger log = LoggerFactory.getLogger(IrisStruggleInterventionService.class);

    private static final int PERSIST_MAX_ATTEMPTS = 3;

    private final UserRepository userRepository;

    private final IrisChatSessionService irisChatSessionService;

    private final IrisMessageService irisMessageService;

    private final IrisChatWebsocketService irisChatWebsocketService;

    private final IrisMessageRepository irisMessageRepository;

    private final IrisSessionRepository irisSessionRepository;

    private final IrisProactiveEpisodeService irisProactiveEpisodeService;

    private final LLMTokenUsageService llmTokenUsageService;

    private final TransactionTemplate transactionTemplate;

    @Value("${artemis.iris.proactive.struggle.confidence-threshold:0.6}")
    private double confidenceThreshold;

    public IrisStruggleInterventionService(UserRepository userRepository, IrisChatSessionService irisChatSessionService, IrisMessageService irisMessageService,
            IrisChatWebsocketService irisChatWebsocketService, IrisMessageRepository irisMessageRepository, PlatformTransactionManager transactionManager,
            IrisSessionRepository irisSessionRepository, IrisProactiveEpisodeService irisProactiveEpisodeService, LLMTokenUsageService llmTokenUsageService) {
        this.userRepository = userRepository;
        this.irisChatSessionService = irisChatSessionService;
        this.irisMessageService = irisMessageService;
        this.irisChatWebsocketService = irisChatWebsocketService;
        this.irisMessageRepository = irisMessageRepository;
        this.irisSessionRepository = irisSessionRepository;
        this.irisProactiveEpisodeService = irisProactiveEpisodeService;
        this.llmTokenUsageService = llmTokenUsageService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * Apply Iris's gated decision for a completed run (spec §5.4, §5.5, §11). Called once per run by the status
     * handler, AFTER the job has been removed (idempotency).
     *
     * <p>
     * Pull-model change (A9): ambient is now event-only (no persist). The client holds the hint text frozen and
     * promotes it to a chat message only when the student clicks (A10 {@code revealAmbient}). Active still persists
     * and pushes the bubble, with bounded retry on transient failures and a fallback event frame on permanent failure.
     * Silent (and empty results) always emit a noop {@code kind="decide", action="silent"} frame so the client's
     * in-flight {@code decide} always clears.
     *
     * @param job          the struggle-intervention job (ids only; the session is resolved here)
     * @param statusUpdate the gated decision posted back by Pyris
     */
    public void handleDecision(StruggleInterventionJob job, PyrisStruggleInterventionStatusUpdateDTO statusUpdate) {
        var user = userRepository.findByIdElseThrow(job.userId());
        var action = statusUpdate.action();
        var confidence = statusUpdate.confidence();
        boolean helpRequest = "help_request".equals(job.intent());
        boolean belowThreshold = confidence == null || confidence < confidenceThreshold;   // fail-closed on null
        // A consented help_request bypasses the confidence gate (an invited hint must reach the student);
        // an unsolicited decide still downgrades below threshold.
        boolean forceSilent = "silent".equals(action) || (belowThreshold && !helpRequest);
        String finalAction = forceSilent ? "silent" : action;
        // Pull (Less): unsolicited active is capped to ambient. A consented help_request is exempt.
        if ("pull".equals(job.proactivityMode()) && "active".equals(finalAction) && !helpRequest) {
            finalAction = "ambient";
        }
        // A consented, non-silent help_request is always delivered as a persisted bubble, even if Pyris
        // returned "ambient" (the student explicitly asked; no quiet-park semantics on this path).
        if (helpRequest && !"silent".equals(finalAction)) {
            finalAction = "active";
        }
        if (helpRequest && belowThreshold) {
            log.info("help_request delivering below-threshold hint exercise={} user={} confidence={}", job.exerciseId(), job.userId(), confidence);
        }
        log.info("Struggle intervention exercise={} user={} rawAction={} confidence={} finalAction={}", job.exerciseId(), job.userId(), action, confidence, finalAction);

        String episodeId = StruggleEpisodeDTO.usableEpisodeId(job.episodeId());
        String result = statusUpdate.result();
        // Every path below that surfaces nothing still has to clear the client's in-flight decide, and they all clear
        // it with the same frame: the confidence and the rationale travel even when no hint is shown, because the
        // client logs them for the eval (spec §12). One definition, so the nine exits cannot drift apart.
        Runnable completeSilently = () -> irisChatWebsocketService.sendStruggleEvent(user,
                StruggleInterventionEventDTO.silentDecide(job.exerciseId(), confidence, episodeId, statusUpdate.rationale()));

        if (result == null || result.isEmpty()) {
            // Nothing to surface; always emit a completion frame so the client's in-flight decide clears. The
            // confidence still travels: the client logs it for the eval (spec §12) even when nothing is shown.
            completeSilently.run();
            return;
        }

        switch (finalAction) {
            case "active" -> {
                // Skip if this episode is already terminal (late escalation arriving after the student dismissed).
                // A dismiss that commits between this check and the append below is not caught; see IrisProactiveEpisodeService#isEpisodeTerminal.
                if (episodeId != null && irisProactiveEpisodeService.isEpisodeTerminal(episodeId, user.getId(), job.exerciseId())) {
                    completeSilently.run();
                    break;
                }
                // Resolve the exercise-chat session; drop defensively if not exercise-bound.
                var session = resolveProactiveSession(user, job.exerciseId());
                if (session == null) {
                    // Structural mismatch: resolved session is not exercise-bound. Emit a silent completion frame
                    // so the client's in-flight decide always clears (finding 2 fix).
                    completeSilently.run();
                    break;
                }
                // Persist the message with bounded retry on transient DB failures (spec §12). A null result means
                // the message was dropped; the active control event below is still emitted with messageId=null so
                // the client's in-flight decide always clears (finding 1 fix).
                var appended = saveProactiveMessageWithRetry(session, user, job.exerciseId(), result, episodeId, null);
                if (appended.terminal()) {
                    // The episode went terminal between the cheap pre-check above and the locked write. Nothing was
                    // persisted, so complete silently rather than announcing a hint the student already closed.
                    completeSilently.run();
                    break;
                }
                IrisMessage saved = appended.message();
                if (saved != null) {
                    irisChatWebsocketService.sendMessage(session, saved, terminalRunStateOf(statusUpdate), statusUpdate.error());
                }
                // Always emit the active control event - with messageId on success, null on permanent failure.
                // The event always carries the hint text so the client can render a runtime fallback bubble (spec §5/§12).
                Long messageId = saved != null ? saved.getId() : null;
                irisChatWebsocketService.sendStruggleEvent(user, new StruggleInterventionEventDTO(job.exerciseId(), "decide", "active", result, session.getId(), messageId,
                        statusUpdate.anchorFile(), statusUpdate.anchorLine(), statusUpdate.inlineHint(), confidence, episodeId, null, null, null, statusUpdate.rationale()));
            }
            case "ambient" -> {
                // Skip if this episode is already terminal (late ambient arriving after the student dismissed) - the
                // same late-arrival gate the active path applies. A stale offer must not resurface after a terminal
                // outcome; emit a silent completion so the client's in-flight decide still clears. This read is the
                // cheap fast path; the authoritative one runs under the registry lock below.
                if (episodeId != null && irisProactiveEpisodeService.isEpisodeTerminal(episodeId, user.getId(), job.exerciseId())) {
                    completeSilently.run();
                    break;
                }
                // Pull model (spec §5): do NOT persist. Resolve the session only to supply its id on the event
                // so the client knows which session to reveal into when the student clicks (A10/C2).
                var session = resolveProactiveSession(user, job.exerciseId());
                if (session == null) {
                    // Structural mismatch: resolved session is not exercise-bound. A null-session ambient
                    // pointer is unrevealable by the client; emit a silent completion frame instead (finding 3 fix).
                    completeSilently.run();
                    break;
                }
                // Record what we are about to offer BEFORE telling the client about it, so a reveal that races the
                // event still finds the decision. With an episode id, announce the ambient pointer ONLY when recording
                // left a revealable decision behind it: an over-long id or an episode whose previous offer was already
                // revealed records nothing, and pointing the client at a reveal that would 409 helps no one, so
                // complete it silently instead. Without an episode id there is nothing a reveal could address; the
                // pointer is still emitted for the client's own bookkeeping, exactly as before.
                // A job that carried an id we cannot use is NOT the same as one that carried none. Both keep the id
                // out of every lookup and column, but a legacy job without an episode still gets its ambient
                // bookkeeping pointer, while an unusable id stays silent: it can never be revealed, so pointing the
                // client at it would only produce a 409.
                Boolean offered = episodeId == null ? job.episodeId() == null : irisProactiveEpisodeService.offerAmbientHint(user.getId(), job.exerciseId(), episodeId, result);
                if (offered == null) {
                    // The episode went terminal between the fast path above and the locked check. Nothing was
                    // offered, so complete silently instead of pointing the client at a hint it has already closed.
                    completeSilently.run();
                    break;
                }
                if (offered) {
                    irisChatWebsocketService.sendStruggleEvent(user, new StruggleInterventionEventDTO(job.exerciseId(), "decide", "ambient", result, session.getId(), null,
                            statusUpdate.anchorFile(), statusUpdate.anchorLine(), statusUpdate.inlineHint(), confidence, episodeId, null, null, null, statusUpdate.rationale()));
                }
                else {
                    completeSilently.run();
                }
            }
            default -> {
                // silent (or downgraded): emit a noop completion frame so the client's in-flight decide always clears.
                completeSilently.run();
            }
        }
    }

    /**
     * Apply Iris's response for a {@code confirm_close} request (spec §7.1/§7.3/§4/§8, A11). Routes by the
     * authoritative {@code job.confirmReason()}:
     * <ul>
     * <li>{@code progress}: {@code resolved=true} persists a closing message + writes {@code RECOVERED};
     * {@code resolved=false} is quiet (slot stays TAKEN, no offer posted).</li>
     * <li>{@code parked_progress}: silent on both results (never-delivered episode, nothing persisted, no
     * outcome). Terminal gate is NOT consulted.</li>
     * <li>null or unknown: fail-closed to {@code parked_progress} semantics (nothing persisted, no outcome,
     * bare completion event + warn log).</li>
     * </ul>
     *
     * <p>
     * Terminal gate (delivered reasons only): reads {@link IrisProactiveEpisodeService#isEpisodeTerminal} as a cheap fast
     * path, and the authoritative check runs again under the episode's registry lock in the same transaction as the
     * write. If terminal at either point, nothing is persisted and a noop event goes out. Otherwise the closing row
     * and its {@code RECOVERED} outcome commit together, and only then is anything broadcast. Outcome-last still
     * holds inside that transaction: the row is inserted before its outcome, so the close is never gated away by its
     * own outcome write.
     *
     * <p>
     * {@code resolved=true} on the emitted event means a closing row and its {@code RECOVERED} outcome committed,
     * not that Pyris said the episode was resolved. Every other path emits
     * {@link StruggleInterventionEventDTO#unresolvedClose}, including the ones Pyris answered {@code resolved=true}
     * for: a terminal episode, a locked write that found the episode terminal, a dropped append, and both quiet
     * reasons. Forwarding the gate's verdict there let the client mark an episode recovered that carried no closing
     * row and no outcome, and nothing later would have corrected it.
     *
     * @param job          the struggle-intervention job (ids + episodeId + confirmReason)
     * @param statusUpdate the Pyris response payload
     */
    public void handleConfirmClose(StruggleInterventionJob job, PyrisStruggleInterventionStatusUpdateDTO statusUpdate) {
        var user = userRepository.findByIdElseThrow(job.userId());
        String episodeId = StruggleEpisodeDTO.usableEpisodeId(job.episodeId());
        String confirmReason = job.confirmReason();
        boolean resolved = statusUpdate.resolved() != null ? statusUpdate.resolved() : false;
        // Every exit that does not commit a closing row emits the same frame, including the ones Pyris answered
        // resolved=true for (see the class of cases in this method's javadoc). One definition, so no exit can start
        // forwarding the gate's verdict by accident.
        Runnable completeUnresolved = () -> irisChatWebsocketService.sendStruggleEvent(user,
                StruggleInterventionEventDTO.unresolvedClose(job.exerciseId(), episodeId, statusUpdate.rationale()));

        // parked_progress (and null/unknown fail-closed): silent on both results.
        // Persist nothing, write no outcome. Emit bare completion event only.
        if (!"progress".equals(confirmReason)) {
            if (!"parked_progress".equals(confirmReason)) {
                log.warn("Unexpected confirmReason '{}' on confirm_close for episodeId={} exercise={} user={}, failing closed to parked_progress semantics", confirmReason,
                        episodeId, job.exerciseId(), job.userId());
            }
            completeUnresolved.run();
            return;
        }

        // Terminal gate (delivered reasons only): if the episode already has a terminal outcome (e.g. the
        // student DISMISSED mid-flight), skip persist and emit a noop event. This read is the cheap fast path; the
        // authoritative one runs under the episode's registry lock in the same transaction as the write.
        if (episodeId != null && irisProactiveEpisodeService.isEpisodeTerminal(episodeId, user.getId(), job.exerciseId())) {
            completeUnresolved.run();
            return;
        }

        if (resolved) {
            // progress resolved=true: persist the closing message and its RECOVERED outcome together, then broadcast.
            String closingSentence = statusUpdate.closingSentence();
            if (closingSentence == null || closingSentence.isBlank()) {
                closingSentence = "Nice work, that is resolved.";
            }
            String episodeLabel = statusUpdate.episodeLabel();
            if (episodeLabel == null || episodeLabel.isBlank()) {
                episodeLabel = "Resolved";
            }
            // The close row and its RECOVERED outcome commit together, under the episode's registry lock, and only
            // then is anything broadcast. Persisting first and writing the outcome afterwards left a window in which
            // a dismiss could land between the two, and the live broadcast in between announced a row whose outcome
            // was not written yet. Outcome-last still holds INSIDE the transaction: the row is inserted before the
            // outcome is recorded, so the close can never be gated away by its own outcome.
            var persisted = persistProactiveMessage(user, job.exerciseId(), closingSentence, episodeId, IrisProactiveOutcome.RECOVERED);
            if (persisted == null || persisted.terminal()) {
                // Nothing committed: either the episode went terminal between the gate above and the locked write, or
                // the session was no longer bound to this exercise and the append was dropped. Neither case wrote a
                // closing row or a RECOVERED outcome, so neither may report resolved=true - the client would mark an
                // episode recovered that the server never closed, and no later run would correct it.
                completeUnresolved.run();
                return;
            }
            // Broadcast the committed row so the webview receives it through the single chat-ws transport.
            irisChatWebsocketService.sendMessage(persisted.session(), persisted.saved(), terminalRunStateOf(statusUpdate), statusUpdate.error());
            irisChatWebsocketService.sendStruggleEvent(user, new StruggleInterventionEventDTO(job.exerciseId(), "confirm_close", null, null, null, persisted.saved().getId(), null,
                    null, null, null, episodeId, true, closingSentence, episodeLabel, statusUpdate.rationale()));
        }
        else {
            // progress resolved=false: quiet (slot stays TAKEN, no offer posted, no outcome).
            completeUnresolved.run();
        }
    }

    /**
     * The transactional core of {@link #revealAmbient}: take the offered decision under a write lock, persist the
     * message carrying the server-authored text, and mark the decision consumed. All three commit together, so a
     * failure anywhere leaves neither a message nor a consumed decision behind.
     *
     * <p>
     * The pessimistic lock is what makes the unconsumed-check and the claim indivisible. Without it two concurrent
     * reveals of the same offer could both read it as unconsumed and both insert a message; the guarded update alone
     * would then leave the loser's row orphaned.
     *
     * @param user       the student performing the reveal
     * @param exerciseId the programming exercise id (session scope)
     * @param episodeId  the client-allocated episode UUID to stamp on the row
     * @return the persisted message as a DTO
     */
    private IrisMessageResponseDTO revealAmbientInTransaction(User user, long exerciseId, String episodeId, IrisChatSession session) {
        // One lock, one row. The episode's write lock is both the terminal gate and the offer's mutex: before the
        // registry the reveal had no terminal check at all, and while the offer lived in its own table its lock said
        // nothing about the episode, so the two had to be taken in a fixed order. Now they are the same lock.
        var episode = irisProactiveEpisodeService.lockOfferForRevealInCurrentTransaction(user.getId(), exerciseId, episodeId)
                .orElseThrow(() -> new ConflictException("No ambient hint was offered for this episode", "IrisMessage", "revealWithoutDecision"));
        if (episode.getOutcome() != null) {
            throw new ConflictException("The ambient hint for this episode can no longer be revealed", "IrisMessage", "revealEpisodeTerminal");
        }
        if (episode.getHintText() == null) {
            // Registered, but nothing was ever offered for it: an active decision, a silent run, or a trigger whose
            // callback never arrived. There is no server-authored text to persist and the caller's copy must never be
            // trusted, so this is the same refusal as an unknown episode.
            throw new ConflictException("No ambient hint was offered for this episode", "IrisMessage", "revealWithoutDecision");
        }
        if (episode.getConsumedAt() != null) {
            // Already revealed. Return that reveal's row so a replay is idempotent rather than a second insert;
            // if the row is gone (superseded and deleted), the offer is spent and there is nothing to surface.
            return irisMessageRepository.findById(episode.getConsumedMessageId() == null ? -1L : episode.getConsumedMessageId()).map(IrisMessageResponseDTO::of)
                    .orElseThrow(() -> new ConflictException("The ambient hint for this episode was already revealed", "IrisMessage", "revealAlreadyConsumed"));
        }

        // Append through the guarded helper, which re-checks the session's exercise binding under the session write
        // lock. The ambient-decision lock held here says nothing about the session: a run for a DIFFERENT exercise can
        // switch this same session between resolveProactiveSession above and the write, and the reveal would then
        // persist the hint into that other exercise's history.
        var saved = saveProactiveMessage(session, exerciseId, episode.getHintText(), episodeId);
        if (saved == null) {
            // Fail the whole reveal rather than consuming the offer: rolling back leaves the decision unconsumed, so
            // the student can reveal it again once the session is back on this exercise.
            throw new ConflictException("Cannot persist reveal: the chat session moved to another exercise", "IrisMessage", "revealSessionConflict");
        }

        // The locked entity is managed, so consuming it is part of this transaction's flush.
        irisProactiveEpisodeService.consumeOfferInCurrentTransaction(episode, saved.getId());
        return IrisMessageResponseDTO.of(saved);
    }

    /**
     * Record what the struggle pipeline spent on this callback, so the run shows up in admin token accounting like
     * every other Iris pipeline. Without it the proactive path was the one pipeline whose LLM cost was invisible:
     * the callback carried {@code tokens} and nothing read them.
     *
     * <p>
     * Called once per claimed callback, before the frame is routed, so a run that reports spend on an intermediate
     * frame or on a failure is counted too. Claimed is the exact guarantee, not "once per frame": a callback whose
     * job another thread already took never reaches here, and the trailing duplicate after a terminal frame is
     * rejected, but a retransmitted non-terminal frame arrives while the job is still alive and would be counted
     * again, as would a retry after this node died between the save below and the job removal that follows it. There
     * is no callback id or sequence number to key on, so those are accepted as an over-count rather than prevented.
     *
     * <p>
     * The placement is also why this attributes to the job rather than to a message: the message a decision persists
     * does not exist yet here, and several outcomes ({@code silent}, {@code ambient}, a quiet close) never persist
     * one at all, so keying the trace on a message would drop their cost. Course, exercise and user come off the
     * job, which is the same scope the admin view groups by; a null message id is a documented case there, counted
     * under "other" rather than dropped.
     *
     * <p>
     * Each token-bearing callback gets its own trace, rather than one trace per run appended to as the chat path
     * does. The chat path needs that because it has a genuine second artifact, the interaction suggestion that
     * belongs to an already-created message trace; a one-shot struggle run has no counterpart, and threading a trace
     * id through the job would mean writing the job back to the distributed map immediately before the terminal
     * frame removes it. The consequence, should Pyris ever split a run's payload across callbacks: cost totals stay
     * correct because they sum requests regardless of trace boundaries, while trace counts and trace-level exports
     * become callback-granular rather than run-granular. Worth revisiting only if that split turns out to happen.
     *
     * @param job          the struggle-intervention job the callback belongs to
     * @param statusUpdate the callback, whose {@code tokens} may be empty
     */
    public void recordTokenUsage(StruggleInterventionJob job, PyrisStruggleInterventionStatusUpdateDTO statusUpdate) {
        if (statusUpdate.tokens().isEmpty()) {
            return;
        }
        try {
            llmTokenUsageService.saveLLMTokenUsage(statusUpdate.tokens(), LLMServiceType.IRIS,
                    builder -> builder.withCourse(job.courseId()).withExercise(job.exerciseId()).withUser(job.userId()));
        }
        catch (Exception e) {
            // Accounting must never cost the student their intervention: this runs inside the callback handler, which
            // has already claimed the job, so an escaping failure would leave the client's in-flight request hanging.
            // Deliberately an under-count with no retry, which is the lesser cost.
            log.warn("Could not record token usage for struggle job {} exercise {} user {}", job.jobId(), job.exerciseId(), job.userId(), e);
        }
    }

    private record PersistedProactive(@Nullable IrisChatSession session, @Nullable IrisMessage saved, boolean terminal) {

        PersistedProactive(IrisChatSession session, IrisMessage saved) {
            this(session, saved, false);
        }

        static PersistedProactive alreadyTerminal() {
            return new PersistedProactive(null, null, true);
        }
    }

    /**
     * Persist a previously-hidden ambient hint as a {@code PROACTIVE_STRUGGLE} message with a server-assigned
     * {@code sentAt}. Idempotency is scoped to {@code (user, exercise, episode)} and enforced by the episode row,
     * not by any client-supplied key: a replay finds the offer already consumed and returns the row that reveal
     * created, rather than inserting a second one.
     *
     * <p>
     * Deliberately does NOT call {@code irisChatWebsocketService.sendMessage}: the client owns the single insert
     * (optimistic bubble), and broadcasting here would duplicate the bubble before the client can reconcile (C2).
     *
     * @param user       the student performing the reveal
     * @param exerciseId the programming exercise id (session scope)
     * @param episodeId  the client-allocated episode UUID to stamp on the row
     * @return the persisted message as a DTO (id + proactiveEpisodeId visible to the client for reconciliation)
     */
    public IrisMessageResponseDTO revealAmbient(User user, long exerciseId, String episodeId) {
        // Authoritative text: the reveal may only surface a hint Artemis actually offered for THIS episode, and it
        // persists the server's copy. Trusting the caller's hintText let a student post arbitrary content as an LLM
        // message - which is fed back into the Pyris prompt as assistant history - and mint unlimited rows with
        // fresh ids. The lookup is scoped by user and exercise, so a guessed episode id reaches nothing.
        if (episodeId == null || episodeId.isBlank()) {
            throw new BadRequestException("An episode id is required to reveal an ambient hint");
        }
        // Resolve (and if necessary switch) the session BEFORE the transaction, the same way the callback paths do.
        // Inside it, applyContextChange's own template would join this transaction, so its CTXSWAP frame would go out
        // before the commit and survive a rollback. Resolving out here also keeps the session lock from being held
        // while the registry lock is taken, so the two are never acquired in opposing orders.
        var session = resolveProactiveSession(user, exerciseId);
        if (session == null) {
            throw new ConflictException("Cannot persist reveal: the exercise-chat session could not be resolved", "IrisMessage", "revealSessionConflict");
        }
        // The insert and the claim have to commit together, otherwise a crash between them persists a message that no
        // decision records as consumed, and the offer could be revealed a second time. TransactionTemplate rather than
        // @Transactional on this method: a same-class helper would be self-invoked and bypass the proxy.
        return transactionTemplate.execute(status -> revealAmbientInTransaction(user, exerciseId, episodeId, session));
    }

    /**
     * Delete a superseded proactive message row, making stale-row suppression durable (not just live). The guards
     * (proactive-origin AND null outcome AND the row belongs to one of the user's sessions) and the delete run as ONE
     * atomic SQL statement ({@link IrisMessageRepository#deleteSupersededProactiveMessage}), so there is no
     * check-then-delete race: a concurrent outcome write can never cause a now-terminal row to be deleted. Missing or
     * already-deleted rows, non-proactive rows, other users' rows, and rows with a terminal outcome are all silent
     * noops (idempotent 204 semantics at the endpoint level).
     *
     * <p>
     * This is also the compensation for the check-then-write window on {@link IrisProactiveEpisodeService#isEpisodeTerminal}: a hint that was
     * persisted after its episode went terminal is removed here rather than left in the history, where it would keep
     * being replayed to Pyris as something the tutor said. The delete is therefore not merely a client convenience,
     * and a client that only hides such a message locally leaves the server's history wrong.
     *
     * <p>
     * The guarded statement decides WHETHER the row goes; it cannot also keep the session's ordered message list
     * intact, because it never touches the collection that owns the order column. That is what the surrounding
     * transaction and the session write lock are for.
     *
     * @param user      the requesting student
     * @param messageId the id of the message to delete
     */
    public void deleteSupersededProactiveMessage(User user, long messageId) {
        // The delete itself stays one guarded statement, but it cannot stand alone: it does not go through the
        // collection that owns iris_message_order, so removing anything but the last message leaves a hole in the
        // list indices and the next load of the session materialises a null element on it. Reading the index,
        // deleting and closing the gap therefore happen in ONE transaction, under the session's write lock, which
        // is the same lock every append takes (see IrisMessageService#saveMessage) so the two cannot interleave.
        transactionTemplate.executeWithoutResult(status -> {
            var sessionId = irisMessageRepository.findOwnedSessionId(messageId, user.getId());
            if (sessionId.isEmpty()) {
                // Missing row, or another user's: nothing to delete and nothing to lock. Same silent noop as before.
                return;
            }
            irisSessionRepository.findByIdWithWriteLockElseThrow(sessionId.get());
            // Read the index BEFORE the row is gone; after the delete there is nothing left to read it from.
            var removedIndex = irisMessageRepository.findListIndex(messageId);
            int deleted = irisMessageRepository.deleteSupersededProactiveMessage(messageId, user.getId());
            if (deleted == 0) {
                // The row failed one of the delete's own guards (wrong origin, or an outcome landed on it): it is
                // still there, its index is still valid, and compacting would corrupt the list.
                return;
            }
            removedIndex.ifPresent(index -> irisMessageRepository.compactMessageOrderAfter(sessionId.get(), index));
        });
    }

    /**
     * Resolve the shared exercise-chat session. Returns null when the resolved session is not exercise-bound
     * (defensive drop). Callers decide whether to persist into it.
     *
     * @param user       the student
     * @param exerciseId the programming exercise id
     * @return the session, or null if not exercise-bound
     */
    private @Nullable IrisChatSession resolveProactiveSession(User user, long exerciseId) {
        var session = irisChatSessionService.getCurrentSessionOrCreateIfNotExists(IrisChatMode.PROGRAMMING_EXERCISE_CHAT, exerciseId, user);
        // Every session is born a COURSE_CHAT and only points at an exercise after an explicit context switch
        // (which also writes the CTXSWAP marker into the history), so asking for an exercise chat that does not
        // exist yet yields the course session. Switch it here, mirroring what the build-failed proactive event
        // does; without this a student with no exercise session yet would get the proactive hint into their
        // course chat, where the client's exercise-scoped reveal cannot find it.
        if (session.getMode() == IrisChatMode.COURSE_CHAT) {
            irisChatSessionService.applyContextChange(session, IrisChatMode.PROGRAMMING_EXERCISE_CHAT, exerciseId, user);
        }
        if (session.getMode() != IrisChatMode.PROGRAMMING_EXERCISE_CHAT || !Objects.equals(session.getEntityId(), exerciseId)) {
            log.info("Dropping stale struggle intervention: resolved session for exercise {} is not exercise-bound", exerciseId);
            return null;
        }
        return session;
    }

    /**
     * Build and persist a single origin-tagged proactive message into the given session. Does NOT push over the
     * socket. Callers handle retry and event emission.
     *
     * @param session   the resolved exercise-chat session
     * @param result    the proactive message text returned by the gate
     * @param episodeId the client-allocated episode UUID; set on the message when non-null (written by A9 active,
     *                      used by A10 to locate the canonical row)
     * @return the saved IrisMessage (id assigned)
     */
    private @Nullable IrisMessage saveProactiveMessage(IrisChatSession session, long exerciseId, String result, @Nullable String episodeId) {
        return transactionTemplate.execute(status -> {
            // Re-check the exercise binding under the session write lock, immediately before writing. Single-flight is
            // keyed by (user, exercise), so this student can have a second run in flight for a DIFFERENT exercise, and
            // both resolve the SAME session: a session is born a COURSE_CHAT and only ever points at an exercise
            // through a context switch. Run B can therefore switch the session to its own exercise between the moment
            // resolveProactiveSession validated it for A and the moment A appends. Without this check A's hint would
            // be persisted into B's exercise history and replayed to Pyris under the wrong exercise.
            if (!(irisSessionRepository.findByIdWithWriteLockElseThrow(session.getId()) instanceof IrisChatSession locked)) {
                return null;
            }
            irisSessionRepository.flush();
            irisSessionRepository.refresh(locked);
            if (locked.getMode() != IrisChatMode.PROGRAMMING_EXERCISE_CHAT || !Objects.equals(locked.getEntityId(), exerciseId)) {
                log.info("Dropping proactive message: session {} moved to mode={} entity={} before the append for exercise {}", locked.getId(), locked.getMode(),
                        locked.getEntityId(), exerciseId);
                return null;
            }

            var message = new IrisMessage();
            message.addContent(new IrisTextMessageContent(result));
            message.setOrigin(IrisMessageOrigin.PROACTIVE_STRUGGLE);
            // Stamp the exercise the message was decided for, not the one the session happens to point at later: the
            // session's entityId moves with every context switch, so it cannot tell an episode's rows apart once the
            // student navigates away. Episode lookups filter on this column.
            message.setProactiveExerciseId(exerciseId);
            if (episodeId != null) {
                message.setProactiveEpisodeId(episodeId);
            }
            return irisMessageService.saveMessage(message, locked, IrisMessageSender.LLM);
        });
    }

    /**
     * Resolve the shared exercise-chat session and persist an origin-tagged proactive message. Returns null when the
     * resolved session is not exercise-bound (defensive drop). Shared by paths that need the session + saved message
     * together (e.g. A10 {@code revealAmbient}). Does NOT push over the socket.
     *
     * @param user       the student the proactive message belongs to
     * @param exerciseId the programming exercise id the message is bound to
     * @param result     the proactive message text returned by the gate
     * @param episodeId  the client-allocated episode UUID; stamped on the persisted message when non-null
     * @return the resolved session + saved message, or null if the resolved session is not exercise-bound
     */
    @Nullable
    PersistedProactive persistProactiveMessage(User user, long exerciseId, String result, @Nullable String episodeId, @Nullable IrisProactiveOutcome outcomeOnSuccess) {
        var session = resolveProactiveSession(user, exerciseId);
        if (session == null) {
            return null;
        }
        // On a permanent DataAccessException (or once the transient retries are exhausted) the message is dropped and
        // null is returned rather than propagating: the confirm_close caller then still emits its completion frame
        // (with messageId=null) so the client's in-flight slot always clears (finding 2 fix) instead of the exception
        // bubbling up and leaving the single-flight slot stuck.
        var appended = saveProactiveMessageWithRetry(session, user, exerciseId, result, episodeId, outcomeOnSuccess);
        if (appended.terminal()) {
            return PersistedProactive.alreadyTerminal();
        }
        return appended.message() == null ? null : new PersistedProactive(session, appended.message());
    }

    /**
     * Persist an origin-tagged proactive message into an already-resolved session, with bounded retry on transient
     * DB failures (spec §12). Returns null when the message could not be persisted: on a permanent
     * {@link DataAccessException} (retrying cannot help) or once the transient attempts are exhausted.
     *
     * <p>
     * Deliberately never propagates a persistence failure. Both callers have to emit their completion frame with
     * {@code messageId=null} afterwards, so the client's in-flight slot always clears; an exception escaping here
     * would strand it. The session is taken as a parameter rather than resolved, because the active path still
     * needs the session id for that frame even when the message itself was dropped.
     *
     * @param session          the already-resolved exercise-chat session to persist into
     * @param user             the student the proactive message belongs to (logging scope)
     * @param exerciseId       the programming exercise id the message is bound to (logging scope)
     * @param result           the proactive message text returned by the gate
     * @param episodeId        the client-allocated episode UUID; stamped on the persisted message when non-null
     * @param outcomeOnSuccess if non-null, recorded as the episode's terminal outcome in the same transaction as the
     *                             append, which is what makes the confirm-close row and its outcome atomic
     * @return whether the episode ended up terminal by someone else's hand (nothing of this call's was kept), and the
     *         saved message when one was written
     */
    private ProactiveAppend saveProactiveMessageWithRetry(IrisChatSession session, User user, long exerciseId, String result, @Nullable String episodeId,
            @Nullable IrisProactiveOutcome outcomeOnSuccess) {
        for (int attempt = 0; attempt < PERSIST_MAX_ATTEMPTS; attempt++) {
            try {
                var appended = transactionTemplate.execute(status -> {
                    // The authoritative terminal check. The cheap one the callers run first is only a fast path: it
                    // reads outside any lock, so an outcome can commit between it and this write. Here the episode's
                    // registry row is write-locked and stays locked until this transaction commits, so no outcome can
                    // be established between the check and the append.
                    var locked = episodeId == null ? null : irisProactiveEpisodeService.lockEpisodeAndReadTerminalInCurrentTransaction(episodeId, user.getId(), exerciseId);
                    if (locked != null && locked.terminal()) {
                        return ProactiveAppend.alreadyTerminal();
                    }
                    var saved = saveProactiveMessage(session, exerciseId, result, episodeId);
                    if (saved != null && locked != null && outcomeOnSuccess != null) {
                        // Same transaction as the append, still under the same lock. Splitting the two is what let a
                        // concurrent dismiss land between a committed close row and its own outcome.
                        var write = irisProactiveEpisodeService.recordOutcomeUnderLockInCurrentTransaction(locked.episode(), episodeId, user.getId(), exerciseId, outcomeOnSuccess);
                        if (write != IrisProactiveEpisodeService.OutcomeWrite.APPLIED) {
                            // An unregistered episode has no registry row to lock, so the terminal check above is a
                            // snapshot read and a dismiss can still commit between it and this write. The guarded
                            // UPDATE inside the write is what actually detects that, and the only honest answer once
                            // it does is to take the append back: a closing row committed under a foreign terminal
                            // outcome would carry none of its own, and the caller would broadcast resolved=true for
                            // an episode the student had already ended differently. Rolling back drops the row that
                            // was inserted a few statements ago and leaves the episode exactly as the winner left it.
                            status.setRollbackOnly();
                            return ProactiveAppend.alreadyTerminal();
                        }
                    }
                    return ProactiveAppend.of(saved);
                });
                return appended == null ? ProactiveAppend.of(null) : appended;
            }
            catch (TransientDataAccessException ex) {
                // The retry wraps the WHOLE transaction, never an operation inside one: a failed statement marks its
                // transaction rollback-only, so retrying within it would only surface as an UnexpectedRollbackException
                // at commit. Each attempt therefore starts a fresh transaction and re-takes the registry lock.
                log.warn("Transient proactive persist failure attempt {}/{} for exercise={} user={}", attempt + 1, PERSIST_MAX_ATTEMPTS, exerciseId, user.getId(), ex);
            }
            catch (DataAccessException ex) {
                // Non-transient failure (e.g. DataIntegrityViolationException): no point retrying.
                log.warn("Permanent proactive persist failure for exercise={} user={}", exerciseId, user.getId(), ex);
                return ProactiveAppend.of(null);
            }
        }
        log.warn("Proactive persist failed after {} attempts for exercise={} user={}", PERSIST_MAX_ATTEMPTS, exerciseId, user.getId());
        return ProactiveAppend.of(null);
    }

    /**
     * The result of an append attempt. {@code terminal} and a null {@code message} are different outcomes and the
     * caller has to tell them apart: a terminal episode completes silently, while a dropped message still emits its
     * control event with {@code messageId=null} so the client's in-flight request clears.
     *
     * @param terminal whether the episode was already terminal, so nothing was written on purpose - either seen
     *                     before the append, or found by the outcome write afterwards, in which case the append was
     *                     rolled back
     * @param message  the persisted message, null when it could not be written
     */
    private record ProactiveAppend(boolean terminal, @Nullable IrisMessage message) {

        static ProactiveAppend alreadyTerminal() {
            return new ProactiveAppend(true, null);
        }

        static ProactiveAppend of(@Nullable IrisMessage message) {
            return new ProactiveAppend(false, message);
        }
    }

    /**
     * Run state to broadcast alongside a persisted proactive message. Both call sites run on the terminal frame
     * (a decision or a confirmed close), so a frame that omits the run state is still a completed run.
     *
     * @param statusUpdate the Pyris status update that produced the message
     * @return the frame's run state, or {@link PyrisRunState#FINISHED} when it carries none
     */
    private static PyrisRunState terminalRunStateOf(PyrisStruggleInterventionStatusUpdateDTO statusUpdate) {
        return statusUpdate.runState() != null ? statusUpdate.runState() : PyrisRunState.FINISHED;
    }
}
