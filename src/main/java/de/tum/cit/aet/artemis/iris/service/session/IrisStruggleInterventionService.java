package de.tum.cit.aet.artemis.iris.service.session;

import java.util.Objects;

import jakarta.ws.rs.BadRequestException;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.admin.domain.LLMServiceType;
import de.tum.cit.aet.artemis.admin.service.LLMTokenUsageService;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.iris.config.IrisEnabled;
import de.tum.cit.aet.artemis.iris.config.IrisProactiveProperties;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessage;
import de.tum.cit.aet.artemis.iris.domain.message.IrisProactiveOutcome;
import de.tum.cit.aet.artemis.iris.domain.session.IrisChatMode;
import de.tum.cit.aet.artemis.iris.domain.session.IrisChatSession;
import de.tum.cit.aet.artemis.iris.dto.IrisMessageResponseDTO;
import de.tum.cit.aet.artemis.iris.dto.StruggleEpisodeDTO;
import de.tum.cit.aet.artemis.iris.dto.StruggleInterventionEventDTO;
import de.tum.cit.aet.artemis.iris.repository.IrisEpisodeWentTerminalException;
import de.tum.cit.aet.artemis.iris.repository.IrisProactiveEpisodeRepository;
import de.tum.cit.aet.artemis.iris.repository.IrisSessionRepository;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisRunState;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.struggle.PyrisStruggleInterventionStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.job.StruggleInterventionJob;
import de.tum.cit.aet.artemis.iris.service.websocket.IrisChatWebsocketService;

/**
 * Applies Iris' gated result for the proactive struggle-intervention feature: what happens once the Pyris pipeline
 * calls back. Detection stays in the client engine, and the other end of a run is {@link IrisStruggleTriggerService}.
 * The episode's registry row and the writes that settle its outcome belong to {@link IrisProactiveEpisodeService};
 * this service orchestrates them and owns the chat-message persistence around them.
 *
 * <p>
 * An {@code ambient} decision is event-only, and no message row is persisted until the student clicks.
 * {@code active} persists a message and pushes it live over the socket. Every path that surfaces nothing still emits
 * a completion event, so the client's in-flight {@code decide} always clears.
 */
@Lazy
@Service
@Conditional(IrisEnabled.class)
public class IrisStruggleInterventionService {

    private static final Logger log = LoggerFactory.getLogger(IrisStruggleInterventionService.class);

    private final UserRepository userRepository;

    private final IrisChatSessionService irisChatSessionService;

    private final IrisChatWebsocketService irisChatWebsocketService;

    private final IrisSessionRepository irisSessionRepository;

    private final IrisProactiveEpisodeRepository irisProactiveEpisodeRepository;

    private final IrisProactiveEpisodeService irisProactiveEpisodeService;

    private final LLMTokenUsageService llmTokenUsageService;

    private final IrisProactiveProperties proactiveProperties;

    public IrisStruggleInterventionService(UserRepository userRepository, IrisChatSessionService irisChatSessionService, IrisChatWebsocketService irisChatWebsocketService,
            IrisSessionRepository irisSessionRepository, IrisProactiveEpisodeRepository irisProactiveEpisodeRepository, IrisProactiveEpisodeService irisProactiveEpisodeService,
            LLMTokenUsageService llmTokenUsageService, IrisProactiveProperties proactiveProperties) {
        this.userRepository = userRepository;
        this.irisChatSessionService = irisChatSessionService;
        this.irisChatWebsocketService = irisChatWebsocketService;
        this.irisSessionRepository = irisSessionRepository;
        this.irisProactiveEpisodeRepository = irisProactiveEpisodeRepository;
        this.irisProactiveEpisodeService = irisProactiveEpisodeService;
        this.llmTokenUsageService = llmTokenUsageService;
        this.proactiveProperties = proactiveProperties;
    }

    /**
     * Apply Iris' gated decision for a completed run. Called once per run by the status handler, after the job has
     * been removed, which is what makes it idempotent. Active persists and pushes the bubble, with bounded retry on
     * transient failures and a fallback event frame on permanent failure.
     *
     * @param job          the struggle-intervention job (ids only; the session is resolved here)
     * @param statusUpdate the gated decision posted back by Pyris
     */
    public void handleDecision(StruggleInterventionJob job, PyrisStruggleInterventionStatusUpdateDTO statusUpdate) {
        var user = userRepository.findByIdElseThrow(job.userId());
        var action = statusUpdate.action();
        var confidence = statusUpdate.confidence();
        boolean helpRequest = "help_request".equals(job.intent());
        boolean belowThreshold = confidence == null || confidence < proactiveProperties.getStruggle().getConfidenceThreshold();   // fail-closed on null
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
        // One definition for the nine exits that surface nothing, so they cannot drift apart. The confidence and
        // rationale travel even when no hint is shown, because the client logs them for the eval.
        Runnable completeSilently = () -> irisChatWebsocketService.sendStruggleEvent(user,
                StruggleInterventionEventDTO.silentDecide(job.exerciseId(), confidence, episodeId, statusUpdate.rationale()));

        if (result == null || result.isEmpty()) {
            completeSilently.run();
            return;
        }

        switch (finalAction) {
            case "active" -> {
                // Skip a late escalation arriving after the student dismissed. This unlocked read is the cheap fast
                // path; the locked re-check inside saveProactiveMessageWithRetry rolls back what it misses.
                if (episodeId != null && irisProactiveEpisodeService.isEpisodeTerminal(episodeId, user.getId(), job.exerciseId())) {
                    completeSilently.run();
                    break;
                }
                var session = resolveProactiveSession(user, job.exerciseId());
                if (session == null) {
                    // The resolved session is not exercise-bound, which is a structural mismatch.
                    completeSilently.run();
                    break;
                }
                var appended = saveProactiveMessageWithRetry(session, user, job.exerciseId(), result, episodeId, null);
                if (appended.terminal()) {
                    // Terminal between the cheap pre-check and the locked write, so nothing was persisted.
                    completeSilently.run();
                    break;
                }
                IrisMessage saved = appended.message();
                if (saved != null) {
                    irisChatWebsocketService.sendMessage(session, saved, terminalRunStateOf(statusUpdate), statusUpdate.error());
                }
                // messageId on success, null on permanent failure. The text always travels, so the client can
                // render a fallback bubble.
                Long messageId = saved != null ? saved.getId() : null;
                irisChatWebsocketService.sendStruggleEvent(user, new StruggleInterventionEventDTO(job.exerciseId(), "decide", "active", result, session.getId(), messageId,
                        statusUpdate.anchorFile(), statusUpdate.anchorLine(), statusUpdate.inlineHint(), confidence, episodeId, null, null, null, statusUpdate.rationale()));
            }
            case "ambient" -> {
                // The same late-arrival gate the active path applies, so a stale offer cannot resurface after a
                // terminal outcome. Cheap fast path; the authoritative read runs under the registry lock below.
                if (episodeId != null && irisProactiveEpisodeService.isEpisodeTerminal(episodeId, user.getId(), job.exerciseId())) {
                    completeSilently.run();
                    break;
                }
                // Nothing is persisted here. The session is resolved only to supply its id on the event, so the
                // client knows which session to reveal into.
                var session = resolveProactiveSession(user, job.exerciseId());
                if (session == null) {
                    // A null-session ambient pointer is unrevealable by the client.
                    completeSilently.run();
                    break;
                }
                // Record what we are about to offer BEFORE telling the client about it, so a reveal that races the
                // event still finds the decision. With an episode id, the pointer goes out only when recording left
                // a revealable decision behind it, since pointing the client at a reveal that would 409 helps no one.
                // A job that carried no id at all still gets its bookkeeping pointer, while an unusable id stays
                // silent. Two statements rather than a ternary, because mixing the primitive with the nullable
                // Boolean would unbox the offer and throw on null before the check below.
                Boolean offered;
                if (episodeId != null) {
                    offered = irisProactiveEpisodeService.offerAmbientHint(user.getId(), job.exerciseId(), episodeId, result);
                }
                else {
                    offered = job.episodeId() == null;
                }
                if (offered == null) {
                    // Terminal between the fast path and the locked check, so nothing was offered.
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
            default -> completeSilently.run();
        }
    }

    /**
     * Apply Iris' response for a {@code confirm_close} request, routed by the authoritative
     * {@code job.confirmReason()}:
     * <ul>
     * <li>{@code progress}: {@code resolved=true} persists a closing message and writes {@code RECOVERED};
     * {@code resolved=false} is quiet.</li>
     * <li>{@code parked_progress}: silent on both results, and the terminal gate is not consulted.</li>
     * <li>null or unknown: fails closed to {@code parked_progress} semantics, plus a warn log.</li>
     * </ul>
     *
     * <p>
     * {@code resolved=true} on the emitted event means a closing row and its {@code RECOVERED} outcome committed, not
     * that Pyris said the episode was resolved. Every other path emits
     * {@link StruggleInterventionEventDTO#unresolvedClose}, including the ones Pyris answered {@code resolved=true}
     * for. Forwarding the gate's verdict there let the client mark an episode recovered that carried no closing row
     * and no outcome, and nothing later would have corrected it.
     *
     * @param job          the struggle-intervention job (ids + episodeId + confirmReason)
     * @param statusUpdate the Pyris response payload
     */
    public void handleConfirmClose(StruggleInterventionJob job, PyrisStruggleInterventionStatusUpdateDTO statusUpdate) {
        var user = userRepository.findByIdElseThrow(job.userId());
        String episodeId = StruggleEpisodeDTO.usableEpisodeId(job.episodeId());
        String confirmReason = job.confirmReason();
        boolean resolved = statusUpdate.resolved() != null ? statusUpdate.resolved() : false;
        // One definition for every exit that does not commit a closing row, so none can start forwarding the gate's
        // verdict by accident.
        Runnable completeUnresolved = () -> irisChatWebsocketService.sendStruggleEvent(user,
                StruggleInterventionEventDTO.unresolvedClose(job.exerciseId(), episodeId, statusUpdate.rationale()));

        // parked_progress, and null or unknown failing closed to it: persist nothing, write no outcome.
        if (!"progress".equals(confirmReason)) {
            if (!"parked_progress".equals(confirmReason)) {
                log.warn("Unexpected confirmReason '{}' on confirm_close for episodeId={} exercise={} user={}, failing closed to parked_progress semantics", confirmReason,
                        episodeId, job.exerciseId(), job.userId());
            }
            completeUnresolved.run();
            return;
        }

        // Terminal gate for delivered reasons. Cheap fast path; the authoritative check runs under the episode's
        // registry lock in the same transaction as the write.
        if (episodeId != null && irisProactiveEpisodeService.isEpisodeTerminal(episodeId, user.getId(), job.exerciseId())) {
            completeUnresolved.run();
            return;
        }

        if (resolved) {
            String closingSentence = statusUpdate.closingSentence();
            if (closingSentence == null || closingSentence.isBlank()) {
                closingSentence = "Nice work, that is resolved.";
            }
            String episodeLabel = statusUpdate.episodeLabel();
            if (episodeLabel == null || episodeLabel.isBlank()) {
                episodeLabel = "Resolved";
            }
            // Row and RECOVERED outcome commit together under the episode's registry lock, and only then is
            // anything broadcast: persisting first left a window for a dismiss to land between the two. Outcome-last
            // still holds inside the transaction, so the close is never gated away by its own outcome.
            var persisted = persistProactiveMessage(user, job.exerciseId(), closingSentence, episodeId, IrisProactiveOutcome.RECOVERED);
            if (persisted == null || persisted.terminal()) {
                // Terminal between the gate and the locked write, or the append was dropped. Neither wrote a closing
                // row, so neither may report resolved=true.
                completeUnresolved.run();
                return;
            }
            irisChatWebsocketService.sendMessage(persisted.session(), persisted.saved(), terminalRunStateOf(statusUpdate), statusUpdate.error());
            irisChatWebsocketService.sendStruggleEvent(user, new StruggleInterventionEventDTO(job.exerciseId(), "confirm_close", null, null, null, persisted.saved().getId(), null,
                    null, null, null, episodeId, true, closingSentence, episodeLabel, statusUpdate.rationale()));
        }
        else {
            // progress with resolved=false stays quiet: the slot stays taken, nothing is posted, no outcome.
            completeUnresolved.run();
        }
    }

    /**
     * Record what the struggle pipeline spent on this callback, so the run shows up in admin token accounting like
     * every other Iris pipeline. Without it the proactive path was the one pipeline whose LLM cost was invisible:
     * the callback carried {@code tokens} and nothing read them.
     *
     * <p>
     * Called once per claimed callback, before the frame is routed, so spend reported on an intermediate frame or on
     * a failure is counted too. Claimed is the exact guarantee rather than once per frame: a retransmitted
     * non-terminal frame arrives while the job is still alive and is counted again, and there is no callback id to
     * key on, so that is accepted as an over-count rather than prevented.
     *
     * <p>
     * Attributed to the job rather than to a message, because the message a decision persists does not exist yet
     * here and several outcomes never persist one at all. Course, exercise and user come off the job, which is the
     * scope the admin view groups by; a null message id is counted there under "other" rather than dropped. Each
     * token-bearing callback gets its own trace, so if Pyris ever splits a run's payload across callbacks the cost
     * totals stay correct while trace counts become callback-granular.
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
            // Accounting must never cost the student their intervention: this runs inside the callback handler,
            // which has already claimed the job, so an escaping failure would hang the client's request.
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
     * Persist a previously-hidden ambient hint as a {@code PROACTIVE_STRUGGLE} message. Idempotency is scoped to
     * {@code (user, exercise, episode)} and enforced by the episode row rather than any client-supplied key: a replay
     * finds the offer consumed and returns the row that reveal created. Deliberately does not broadcast, because the
     * client owns the optimistic bubble and a broadcast here would duplicate it.
     *
     * @param user       the student performing the reveal
     * @param exerciseId the programming exercise id (session scope)
     * @param episodeId  the client-allocated episode UUID to stamp on the row
     * @return the persisted message as a DTO (id + proactiveEpisodeId visible to the client for reconciliation)
     */
    public IrisMessageResponseDTO revealAmbient(User user, long exerciseId, String episodeId) {
        // The reveal may only surface a hint Artemis offered for this episode, and it persists the server's copy.
        // Trusting the caller's hintText let a student post arbitrary content as an LLM message, which is fed back
        // into the Pyris prompt as assistant history.
        if (episodeId == null || episodeId.isBlank()) {
            throw new BadRequestException("An episode id is required to reveal an ambient hint");
        }
        // Resolve the session before the transaction, as the callback paths do: inside it, applyContextChange's
        // CTXSWAP frame would go out before the commit and survive a rollback, and the session lock would be held
        // while the registry lock is taken.
        var session = resolveProactiveSession(user, exerciseId);
        if (session == null) {
            throw new ConflictException("Cannot persist reveal: the exercise-chat session could not be resolved", "IrisMessage", "revealSessionConflict");
        }
        // Insert and claim commit together, otherwise a crash between them leaves a message no decision records as
        // consumed and the offer can be revealed twice.
        return IrisMessageResponseDTO.of(irisProactiveEpisodeRepository.revealAmbient(user.getId(), exerciseId, episodeId, session.getId()));
    }

    /**
     * Delete a superseded proactive message row, making stale-row suppression durable rather than merely live. The
     * guards and the delete are one atomic statement, so a concurrent outcome write can never cause a now-terminal
     * row to be deleted; everything it rejects is a silent noop, which is what gives the endpoint its idempotent 204.
     *
     * <p>
     * A registered episode cannot reach this state through a race, because the append re-checks the outcome under the
     * episode's write lock. What is left is an episode with no registry row and, more commonly, a hint the client
     * superseded before either acquired an outcome. Both leave a row that would keep being replayed to Pyris as
     * something the tutor said.
     *
     * @param user      the requesting student
     * @param messageId the id of the message to delete
     */
    public void deleteSupersededProactiveMessage(User user, long messageId) {
        // The guarded delete cannot stand alone: it does not go through the collection that owns
        // iris_message_order, so removing anything but the last message leaves a hole the next load materialises as
        // a null element. Read, delete and compact share one transaction under the session's write lock.
        irisSessionRepository.deleteSupersededProactiveMessageAndCompact(messageId, user.getId());
    }

    // Null when the resolved session is not exercise-bound. Callers decide whether to persist into it.
    private @Nullable IrisChatSession resolveProactiveSession(User user, long exerciseId) {
        var session = irisChatSessionService.getCurrentSessionOrCreateIfNotExists(IrisChatMode.PROGRAMMING_EXERCISE_CHAT, exerciseId, user);
        // Every session is born a COURSE_CHAT and only points at an exercise after an explicit context switch, so
        // asking for an exercise chat that does not exist yet yields the course session. Without the switch the hint
        // would land in the course chat, where the client's exercise-scoped reveal cannot find it.
        if (session.getMode() == IrisChatMode.COURSE_CHAT) {
            irisChatSessionService.applyContextChange(session, IrisChatMode.PROGRAMMING_EXERCISE_CHAT, exerciseId, user);
        }
        if (session.getMode() != IrisChatMode.PROGRAMMING_EXERCISE_CHAT || !Objects.equals(session.getEntityId(), exerciseId)) {
            log.info("Dropping stale struggle intervention: resolved session for exercise {} is not exercise-bound", exerciseId);
            return null;
        }
        return session;
    }

    // Resolves the session and persists an origin-tagged proactive message, for the paths that need both together.
    // Does not push over the socket.
    @Nullable
    PersistedProactive persistProactiveMessage(User user, long exerciseId, String result, @Nullable String episodeId, @Nullable IrisProactiveOutcome outcomeOnSuccess) {
        var session = resolveProactiveSession(user, exerciseId);
        if (session == null) {
            return null;
        }
        // A dropped message returns null rather than propagating, so the caller still emits its completion frame
        // with messageId=null and the client's in-flight slot clears.
        var appended = saveProactiveMessageWithRetry(session, user, exerciseId, result, episodeId, outcomeOnSuccess);
        if (appended.terminal()) {
            return PersistedProactive.alreadyTerminal();
        }
        return appended.message() == null ? null : new PersistedProactive(session, appended.message());
    }

    // Bounded retry on transient failures, and deliberately never propagates a persistence failure: both callers
    // still have to emit their completion frame, and an exception escaping here would strand the client's slot. The
    // session is a parameter rather than resolved here, because the active path needs its id even for a dropped
    // message. A non-null outcomeOnSuccess is recorded in the same transaction as the append.
    private ProactiveAppend saveProactiveMessageWithRetry(IrisChatSession session, User user, long exerciseId, String result, @Nullable String episodeId,
            @Nullable IrisProactiveOutcome outcomeOnSuccess) {
        int maxAttempts = proactiveProperties.getPersistMaxAttempts();
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            try {
                // One repository transaction: registry lock, terminal re-check under it, append, outcome. The
                // callers' cheap pre-check reads outside any lock, so an outcome can commit between it and this.
                var appended = irisProactiveEpisodeRepository.appendProactiveMessageWithOutcome(session.getId(), user.getId(), exerciseId, result, episodeId, outcomeOnSuccess);
                return appended.terminal() ? ProactiveAppend.alreadyTerminal() : ProactiveAppend.of(appended.message());
            }
            catch (IrisEpisodeWentTerminalException terminal) {
                // Rolled back because a foreign terminal outcome won under the lock. Not retryable.
                return ProactiveAppend.alreadyTerminal();
            }
            catch (TransientDataAccessException ex) {
                // The retry wraps the whole transaction, never an operation inside one: a failed statement marks
                // its transaction rollback-only, so each attempt starts fresh and re-takes the registry lock.
                log.warn("Transient proactive persist failure attempt {}/{} for exercise={} user={}", attempt + 1, maxAttempts, exerciseId, user.getId(), ex);
            }
            catch (DataAccessException ex) {
                // Non-transient, so there is no point retrying.
                log.warn("Permanent proactive persist failure for exercise={} user={}", exerciseId, user.getId(), ex);
                return ProactiveAppend.of(null);
            }
        }
        log.warn("Proactive persist failed after {} attempts for exercise={} user={}", maxAttempts, exerciseId, user.getId());
        return ProactiveAppend.of(null);
    }

    // terminal and a null message are different outcomes: a terminal episode completes silently, while a dropped
    // message still emits its control event with messageId=null.
    private record ProactiveAppend(boolean terminal, @Nullable IrisMessage message) {

        static ProactiveAppend alreadyTerminal() {
            return new ProactiveAppend(true, null);
        }

        static ProactiveAppend of(@Nullable IrisMessage message) {
            return new ProactiveAppend(false, message);
        }
    }

    // Both call sites run on the terminal frame, so a frame that omits the run state is still a completed run.
    private static PyrisRunState terminalRunStateOf(PyrisStruggleInterventionStatusUpdateDTO statusUpdate) {
        return statusUpdate.runState() != null ? statusUpdate.runState() : PyrisRunState.FINISHED;
    }
}
