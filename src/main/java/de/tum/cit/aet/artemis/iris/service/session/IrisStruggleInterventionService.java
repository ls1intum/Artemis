package de.tum.cit.aet.artemis.iris.service.session;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import jakarta.ws.rs.BadRequestException;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
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
import de.tum.cit.aet.artemis.iris.repository.IrisChatSessionRepository;
import de.tum.cit.aet.artemis.iris.repository.IrisMessageRepository;
import de.tum.cit.aet.artemis.iris.service.IrisMessageService;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisDTOService;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisJobService;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisPipelineService;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisCourseDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.struggle.PyrisStruggleInterventionStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.struggle.PyrisStruggleSignalDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.job.StruggleInterventionJob;
import de.tum.cit.aet.artemis.iris.service.settings.IrisSettingsService;
import de.tum.cit.aet.artemis.iris.service.websocket.IrisChatWebsocketService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;

/**
 * Orchestrates the proactive struggle-intervention feature (spec §4): the trigger (this task) + the downstream
 * decision (Task 11). Detection stays in the client engine; this service ships the live code + signal to the
 * dedicated Pyris pipeline and applies Iris's gated result.
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

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final AuthorizationCheckService authCheckService;

    private final IrisSettingsService irisSettingsService;

    private final IrisChatSessionRepository irisChatSessionRepository;

    private final PyrisDTOService pyrisDTOService;

    private final PyrisPipelineService pyrisPipelineService;

    private final PyrisJobService pyrisJobService;

    private final UserRepository userRepository;

    private final IrisChatSessionService irisChatSessionService;

    private final IrisMessageService irisMessageService;

    private final IrisChatWebsocketService irisChatWebsocketService;

    private final IrisMessageRepository irisMessageRepository;

    @Value("${artemis.iris.proactive.struggle.confidence-threshold:0.6}")
    private double confidenceThreshold;

    public IrisStruggleInterventionService(ProgrammingExerciseRepository programmingExerciseRepository, AuthorizationCheckService authCheckService,
            IrisSettingsService irisSettingsService, IrisChatSessionRepository irisChatSessionRepository, PyrisDTOService pyrisDTOService,
            PyrisPipelineService pyrisPipelineService, PyrisJobService pyrisJobService, UserRepository userRepository, IrisChatSessionService irisChatSessionService,
            IrisMessageService irisMessageService, IrisChatWebsocketService irisChatWebsocketService, IrisMessageRepository irisMessageRepository) {
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.authCheckService = authCheckService;
        this.irisSettingsService = irisSettingsService;
        this.irisChatSessionRepository = irisChatSessionRepository;
        this.pyrisDTOService = pyrisDTOService;
        this.pyrisPipelineService = pyrisPipelineService;
        this.pyrisJobService = pyrisJobService;
        this.userRepository = userRepository;
        this.irisChatSessionService = irisChatSessionService;
        this.irisMessageService = irisMessageService;
        this.irisChatWebsocketService = irisChatWebsocketService;
        this.irisMessageRepository = irisMessageRepository;
    }

    /**
     * Trigger a proactive struggle intervention (spec §5.2). Returns a typed outcome: accepted (with job token), or
     * rejected carrying whether the rejection was a deliberate course-off (spec §13) versus a transient in-flight skip
     * for this {@code (user, exercise)}. The sync part runs on the request thread; only the heavy DTO build + POST is
     * off-thread.
     *
     * @param exerciseId       the programming exercise id
     * @param signal           the struggle signal from the client engine
     * @param uncommittedFiles the student's live (uncommitted) working copy, merged on top of the latest submission
     * @param intent           the slot intent ({@code decide} | {@code confirm_close} | {@code stale_check})
     * @param episode          the client-allocated episode block (null when not sent by an older client)
     * @param confirmReason    the close-mode discriminator (null unless intent is {@code confirm_close})
     * @param requestToken     the scoped-cancel identity (A10); null on older clients
     * @param user             the requesting student
     * @return the trigger outcome (accepted + job token, or rejected with the course-off flag for the 202)
     */
    public StruggleTriggerOutcome requestStruggleIntervention(long exerciseId, PyrisStruggleSignalDTO signal, Map<String, String> uncommittedFiles, @Nullable String intent,
            @Nullable StruggleEpisodeDTO episode, @Nullable String confirmReason, @Nullable String requestToken, User user) {
        var prepared = prepareTrigger(exerciseId, user, intent, episode, confirmReason, requestToken);
        if (!prepared.accepted()) {
            return new StruggleTriggerOutcome(false, prepared.courseDisabled(), null);
        }
        var p = prepared.trigger();
        CompletableFuture.runAsync(() -> sendToPyris(p, signal, uncommittedFiles)).exceptionally(e -> {
            log.error("Error sending struggle intervention to Iris for exercise {} user {}", p.exerciseId(), p.userId(), e);
            pyrisJobService.releaseStruggleInFlightJob(p.jobToken(), p.userId(), p.exerciseId());
            return null;
        });
        return new StruggleTriggerOutcome(true, false, p.jobToken());
    }

    /**
     * Synchronous core: light exercise load (id only), STUDENT-role gate, then the iris-enabled + proactive gate
     * (spec §13), then reserve the single-flight slot by minting the job. A SINGLE settings read distinguishes a
     * deliberate course-off (Iris or proactive disabled) from a transient in-flight skip, both of which reject.
     *
     * @param exerciseId    the programming exercise id
     * @param user          the requesting student
     * @param intent        the slot intent; passed through to the job so async callbacks can route by intent
     * @param episode       the client episode; the episodeId is stamped on the job for correlation
     * @param confirmReason the close-mode discriminator; stamped on the job for A11 routing
     * @param requestToken  the scoped-cancel UUID; stamped on the job for A10 cancel matching
     * @return a typed preparation: the reserved trigger, or a rejection tagged course-off vs in-flight
     */
    public TriggerPreparation prepareTrigger(long exerciseId, User user, @Nullable String intent, @Nullable StruggleEpisodeDTO episode, @Nullable String confirmReason,
            @Nullable String requestToken) {
        var exercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
        var course = exercise.getCourseViaExerciseGroupOrCourseMember();
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.STUDENT, exercise, user);
        var settings = irisSettingsService.getSettingsForCourse(course);
        if (!settings.enabled() || !settings.proactiveStruggleEnabled()) {
            return TriggerPreparation.courseOff();
        }
        String episodeId = episode != null ? episode.episodeId() : null;
        var tokenOpt = pyrisJobService.addStruggleInterventionJobIfNonePending(course.getId(), user.getId(), exerciseId, intent, episodeId, confirmReason, requestToken);
        if (tokenOpt.isEmpty()) {
            log.info("Struggle intervention already in flight for user {} exercise {}, skipping", user.getId(), exerciseId);
            return TriggerPreparation.inFlight();
        }
        return TriggerPreparation.triggered(
                new PreparedTrigger(course.getId(), exerciseId, user.getId(), settings.variant().jsonValue(), tokenOpt.get(), intent, episode, confirmReason, requestToken));
    }

    /**
     * Heavy off-thread work: re-load EVERYTHING by id (no cross-thread entity), build the data DTOs, fire-and-forget to Pyris.
     * <p>
     * This deliberately runs OFF the request thread with NO surrounding {@code @Transactional} / open Hibernate session -
     * it mirrors the proven develop pattern {@code IrisChatPipelineExecutionService.execute(...)}, which the existing
     * proactive triggers already run via {@code CompletableFuture.runAsync} (see {@code IrisChatSessionService:275/309}).
     * It is LazyInit-safe because every load uses a fetch-join query that eagerly loads exactly what the DTO conversion
     * touches: {@code findByIdWithTemplateAndSolutionParticipation...} (template/solution repos), {@code ...WithMessages}
     * (the chat history), and {@code Exercise.course} is a {@code @ManyToOne} (JPA default EAGER) so navigating
     * {@code getCourseViaExerciseGroupOrCourseMember()} off-thread is safe. This method captures only ids + the immutable
     * payload - do NOT "fix" it by wrapping it in {@code @Transactional} (a self-invoked, non-proxied call would be a no-op
     * anyway) or by passing a request-thread entity across the boundary.
     *
     * @param p                the immutable trigger snapshot (ids + payload)
     * @param signal           the struggle signal from the client engine
     * @param uncommittedFiles the student's live (uncommitted) working copy
     */
    public void sendToPyris(PreparedTrigger p, PyrisStruggleSignalDTO signal, Map<String, String> uncommittedFiles) {
        var user = userRepository.findByIdElseThrow(p.userId());
        // Re-check LLM consent on the async thread: the student may have revoked their opt-in between the 202 and now.
        // Bail BEFORE any egress to Pyris and release the reserved slot (no callback will then arrive).
        if (!user.hasOptedIntoLLMUsage()) {
            log.info("Struggle intervention skipped: user {} is no longer opted into LLM usage", p.userId());
            pyrisJobService.releaseStruggleInFlightJob(p.jobToken(), p.userId(), p.exerciseId());
            return;
        }
        var exercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(p.exerciseId());
        var exerciseDTO = pyrisDTOService.toPyrisProgrammingExerciseDTO(exercise);
        var submissionDTO = latestSubmission(exercise, user).map(s -> pyrisDTOService.toPyrisSubmissionDTO(s, uncommittedFiles)).orElse(null);
        var courseDTO = new PyrisCourseDTO(exercise.getCourseViaExerciseGroupOrCourseMember());
        var chatHistory = irisChatSessionRepository
                .findLatestByEntityIdAndChatModeAndUserIdWithMessages(p.exerciseId(), IrisChatMode.PROGRAMMING_EXERCISE_CHAT, p.userId(), Pageable.ofSize(1)).stream().findFirst()
                .map(s -> pyrisDTOService.toPyrisMessageDTOListForStruggle(s.getMessages())).orElse(List.of());
        pyrisPipelineService.executeStruggleInterventionPipeline(p.variant(), p.jobToken(), user, signal, exerciseDTO, submissionDTO, courseDTO, chatHistory, p.exerciseId(),
                p.intent(), p.episode());
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
        boolean belowThreshold = confidence == null || confidence < confidenceThreshold;   // fail-closed on null
        String finalAction = ("silent".equals(action) || belowThreshold) ? "silent" : action;
        log.info("Struggle intervention exercise={} user={} rawAction={} confidence={} finalAction={}", job.exerciseId(), job.userId(), action, confidence, finalAction);

        String episodeId = job.episodeId();
        String result = statusUpdate.result();

        if (result == null || result.isEmpty()) {
            // Nothing to surface; always emit a completion frame so the client's in-flight decide clears.
            irisChatWebsocketService.sendStruggleEvent(user, new StruggleInterventionEventDTO(job.exerciseId(), "decide", "silent", null, null, null, null, null, null, null,
                    episodeId, null, null, null, null, null, null));
            return;
        }

        switch (finalAction) {
            case "active" -> {
                // Skip if this episode is already terminal (late escalation arriving after the student dismissed).
                if (episodeId != null && isEpisodeTerminal(episodeId)) {
                    irisChatWebsocketService.sendStruggleEvent(user, new StruggleInterventionEventDTO(job.exerciseId(), "decide", "silent", null, null, null, null, null, null,
                            confidence, episodeId, null, null, null, null, null, null));
                    break;
                }
                // Resolve the exercise-chat session; drop defensively if not exercise-bound.
                var session = resolveProactiveSession(user, job.exerciseId());
                if (session == null) {
                    // Structural mismatch: resolved session is not exercise-bound. Emit a silent completion frame
                    // so the client's in-flight decide always clears (finding 2 fix).
                    irisChatWebsocketService.sendStruggleEvent(user, new StruggleInterventionEventDTO(job.exerciseId(), "decide", "silent", null, null, null, null, null, null,
                            confidence, episodeId, null, null, null, null, null, null));
                    break;
                }
                // Persist the message with bounded retry on transient DB failures (spec §12).
                IrisMessage saved = null;
                for (int attempt = 0; attempt < PERSIST_MAX_ATTEMPTS; attempt++) {
                    try {
                        saved = saveProactiveMessage(session, result, episodeId);
                        break;
                    }
                    catch (TransientDataAccessException ex) {
                        log.warn("Transient persist failure attempt {}/{} for exercise={} user={}", attempt + 1, PERSIST_MAX_ATTEMPTS, job.exerciseId(), job.userId(), ex);
                    }
                    catch (DataAccessException ex) {
                        // Non-transient failure (e.g. DataIntegrityViolationException): no point retrying.
                        // saved stays null; the active control event below is still emitted with messageId=null
                        // so the client's in-flight decide always clears (finding 1 fix).
                        log.warn("Permanent persist failure for exercise={} user={}", job.exerciseId(), job.userId(), ex);
                        break;
                    }
                }
                if (saved != null) {
                    irisChatWebsocketService.sendMessage(session, saved, statusUpdate.stages());
                }
                // Always emit the active control event - with messageId on success, null on permanent failure.
                // The event always carries the hint text so the client can render a runtime fallback bubble (spec §5/§12).
                Long messageId = saved != null ? saved.getId() : null;
                irisChatWebsocketService.sendStruggleEvent(user, new StruggleInterventionEventDTO(job.exerciseId(), "decide", "active", result, session.getId(), messageId,
                        statusUpdate.anchorFile(), statusUpdate.anchorLine(), statusUpdate.inlineHint(), confidence, episodeId, null, null, null, null, null, null));
            }
            case "ambient" -> {
                // Pull model (spec §5): do NOT persist. Resolve the session only to supply its id on the event
                // so the client knows which session to reveal into when the student clicks (A10/C2).
                var session = resolveProactiveSession(user, job.exerciseId());
                if (session == null) {
                    // Structural mismatch: resolved session is not exercise-bound. A null-session ambient
                    // pointer is unrevealable by the client; emit a silent completion frame instead (finding 3 fix).
                    irisChatWebsocketService.sendStruggleEvent(user, new StruggleInterventionEventDTO(job.exerciseId(), "decide", "silent", null, null, null, null, null, null,
                            confidence, episodeId, null, null, null, null, null, null));
                    break;
                }
                irisChatWebsocketService.sendStruggleEvent(user, new StruggleInterventionEventDTO(job.exerciseId(), "decide", "ambient", result, session.getId(), null,
                        statusUpdate.anchorFile(), statusUpdate.anchorLine(), statusUpdate.inlineHint(), confidence, episodeId, null, null, null, null, null, null));
            }
            default -> {
                // silent (or downgraded): emit a noop completion frame so the client's in-flight decide always clears.
                irisChatWebsocketService.sendStruggleEvent(user, new StruggleInterventionEventDTO(job.exerciseId(), "decide", "silent", null, null, null, null, null, null,
                        confidence, episodeId, null, null, null, null, null, null));
            }
        }
    }

    /**
     * Apply Iris's response for a {@code confirm_close} request (spec §7.1/§7.3/§4/§8, A11). Routes by the
     * authoritative {@code job.confirmReason()}:
     * <ul>
     * <li>{@code progress}: {@code resolved=true} persists a closing message + writes {@code RECOVERED};
     * {@code resolved=false} is quiet (slot stays TAKEN, no offer posted).</li>
     * <li>{@code stale_solved}: {@code resolved=true} same as progress; {@code resolved=false} persists ONE
     * gentle offer (from {@code rationale}, default if empty).</li>
     * <li>{@code parked_progress}: silent on both results (never-delivered episode, nothing persisted, no
     * outcome). Terminal gate is NOT consulted.</li>
     * <li>null or unknown: fail-closed to {@code parked_progress} semantics (nothing persisted, no outcome,
     * bare completion event + warn log).</li>
     * </ul>
     *
     * <p>
     * Terminal gate (delivered reasons only): reads {@link #isEpisodeTerminal(String)} BEFORE persisting.
     * If already terminal, skips persist and emits a noop event. Otherwise persists FIRST (1), broadcasts
     * live via {@code sendMessage} (1b), THEN writes the outcome (2). Outcome-last ensures a {@code resolved=true}
     * close row is never gated away by its own outcome write.
     *
     * @param job          the struggle-intervention job (ids + episodeId + confirmReason)
     * @param statusUpdate the Pyris response payload
     */
    public void handleConfirmClose(StruggleInterventionJob job, PyrisStruggleInterventionStatusUpdateDTO statusUpdate) {
        var user = userRepository.findByIdElseThrow(job.userId());
        String episodeId = job.episodeId();
        String confirmReason = job.confirmReason();
        boolean resolved = statusUpdate.resolved() != null ? statusUpdate.resolved() : false;

        // parked_progress (and null/unknown fail-closed): silent on both results.
        // Persist nothing, write no outcome. Emit bare completion event only.
        if (!"progress".equals(confirmReason) && !"stale_solved".equals(confirmReason)) {
            if (confirmReason != null && !"parked_progress".equals(confirmReason)) {
                log.warn("Unknown confirmReason '{}' on confirm_close for exercise={} user={}, failing closed to parked_progress semantics", confirmReason, job.exerciseId(),
                        job.userId());
            }
            irisChatWebsocketService.sendStruggleEvent(user, new StruggleInterventionEventDTO(job.exerciseId(), "confirm_close", null, null, null, null, null, null, null, null,
                    episodeId, null, resolved, null, null, null, null));
            return;
        }

        // Terminal gate (delivered reasons only): if the episode already has a terminal outcome (e.g. the
        // student DISMISSED mid-flight), skip persist and emit a noop event.
        if (episodeId != null && isEpisodeTerminal(episodeId)) {
            irisChatWebsocketService.sendStruggleEvent(user, new StruggleInterventionEventDTO(job.exerciseId(), "confirm_close", null, null, null, null, null, null, null, null,
                    episodeId, null, resolved, null, null, null, null));
            return;
        }

        if (resolved) {
            // progress and stale_solved resolved=true: persist closing message (1), broadcast live (1b), write RECOVERED (2).
            String closingSentence = statusUpdate.closingSentence();
            if (closingSentence == null || closingSentence.isBlank()) {
                closingSentence = "Nice work, that is resolved.";
            }
            String episodeLabel = statusUpdate.episodeLabel();
            if (episodeLabel == null || episodeLabel.isBlank()) {
                episodeLabel = "Resolved";
            }
            var persisted = persistProactiveMessage(user, job.exerciseId(), closingSentence, episodeId);
            Long messageId = null;
            if (persisted != null) {
                // (1b) Broadcast the row live so the webview receives it through the single chat-ws transport.
                irisChatWebsocketService.sendMessage(persisted.session(), persisted.saved(), statusUpdate.stages());
                messageId = persisted.saved().getId();
                // (2) Write outcome LAST: prevents the resolved=true close from gating away its own row.
                writeEpisodeOutcome(episodeId, IrisProactiveOutcome.RECOVERED);
            }
            irisChatWebsocketService.sendStruggleEvent(user, new StruggleInterventionEventDTO(job.exerciseId(), "confirm_close", null, null, null, messageId, null, null, null,
                    null, episodeId, null, true, closingSentence, episodeLabel, null, null));
        }
        else if ("progress".equals(confirmReason)) {
            // progress resolved=false: quiet (slot stays TAKEN, no offer posted, no outcome).
            irisChatWebsocketService.sendStruggleEvent(user, new StruggleInterventionEventDTO(job.exerciseId(), "confirm_close", null, null, null, null, null, null, null, null,
                    episodeId, null, false, null, null, null, null));
        }
        else {
            // stale_solved resolved=false: persist ONE gentle offer (from rationale, default if empty).
            String offer = statusUpdate.rationale();
            if (offer == null || offer.isBlank()) {
                offer = "Want to look at it together?";
            }
            var persisted = persistProactiveMessage(user, job.exerciseId(), offer, episodeId);
            Long messageId = null;
            if (persisted != null) {
                irisChatWebsocketService.sendMessage(persisted.session(), persisted.saved(), statusUpdate.stages());
                messageId = persisted.saved().getId();
            }
            String finalOffer = offer;
            irisChatWebsocketService.sendStruggleEvent(user, new StruggleInterventionEventDTO(job.exerciseId(), "confirm_close", null, null, null, messageId, null, null, null,
                    null, episodeId, finalOffer, false, null, null, null, null));
        }
    }

    /**
     * Apply Iris's response for a {@code stale_check} request (spec §7.3, A11). {@code ask=true}: persist the
     * stale-check question (reloadable: PROACTIVE_STRUGGLE + episodeId), broadcast it live, emit
     * {@code kind="stale_check"} with {@code question}+{@code messageId}. Terminal gate applies: if the episode
     * already has a terminal outcome, skips persist and emits a noop event. {@code ask=false}: always emits a
     * noop event and persists nothing.
     *
     * @param job          the struggle-intervention job (ids + episodeId)
     * @param statusUpdate the Pyris response payload
     */
    public void handleStaleCheck(StruggleInterventionJob job, PyrisStruggleInterventionStatusUpdateDTO statusUpdate) {
        var user = userRepository.findByIdElseThrow(job.userId());
        String episodeId = job.episodeId();
        boolean ask = statusUpdate.ask() != null ? statusUpdate.ask() : false;

        if (!ask) {
            // ask=false: always noop, nothing persisted.
            irisChatWebsocketService.sendStruggleEvent(user, new StruggleInterventionEventDTO(job.exerciseId(), "stale_check", null, null, null, null, null, null, null, null,
                    episodeId, null, null, null, null, false, null));
            return;
        }

        // ask=true: terminal gate applies.
        if (episodeId != null && isEpisodeTerminal(episodeId)) {
            irisChatWebsocketService.sendStruggleEvent(user, new StruggleInterventionEventDTO(job.exerciseId(), "stale_check", null, null, null, null, null, null, null, null,
                    episodeId, null, null, null, null, true, null));
            return;
        }

        String question = statusUpdate.question();
        var persisted = persistProactiveMessage(user, job.exerciseId(), question, episodeId);
        Long messageId = null;
        if (persisted != null) {
            irisChatWebsocketService.sendMessage(persisted.session(), persisted.saved(), statusUpdate.stages());
            messageId = persisted.saved().getId();
        }
        irisChatWebsocketService.sendStruggleEvent(user, new StruggleInterventionEventDTO(job.exerciseId(), "stale_check", null, null, null, messageId, null, null, null, null,
                episodeId, null, null, null, null, true, question));
    }

    private record PersistedProactive(IrisChatSession session, IrisMessage saved) {
    }

    /**
     * Persist a previously-hidden ambient hint as a {@code PROACTIVE_STRUGGLE} message with a server-assigned
     * {@code sentAt} and an idempotency key on {@code proactiveClientMessageId}. Idempotent: a lost-response
     * retry with the same {@code clientMessageId} returns the same persisted row without inserting a duplicate.
     * Race-safe: if a concurrent retry wins the unique-index race, the {@code DataIntegrityViolationException}
     * is caught and the now-existing row is re-selected.
     *
     * <p>
     * Deliberately does NOT call {@code irisChatWebsocketService.sendMessage}: the client owns the single insert
     * (optimistic bubble), and broadcasting here would duplicate the bubble before the client can reconcile (C2).
     *
     * @param user            the student performing the reveal
     * @param exerciseId      the programming exercise id (session scope)
     * @param episodeId       the client-allocated episode UUID to stamp on the row
     * @param hintText        the hint text to persist as message content
     * @param level           the intervention level tag; accepted for future use, not stored as a separate column
     * @param clientMessageId the client-generated UUID that serves as the unique idempotency key
     * @return the persisted message as a DTO (id + proactiveEpisodeId visible to the client for reconciliation)
     */
    public IrisMessageResponseDTO revealAmbient(User user, long exerciseId, String episodeId, String hintText, String level, String clientMessageId) {
        // The clientMessageId is the idempotency key: it MUST be present and non-blank, otherwise the unique index
        // cannot dedupe (NULLs are not unique in SQL) and a lost-response retry would create duplicate rows.
        if (clientMessageId == null || clientMessageId.isBlank()) {
            throw new BadRequestException("A non-blank clientMessageId is required to reveal an ambient hint");
        }
        // Fast idempotency path: already persisted (normal case on lost-response retry).
        var existing = irisMessageRepository.findByProactiveClientMessageId(clientMessageId);
        if (existing.isPresent()) {
            return IrisMessageResponseDTO.of(existing.get());
        }
        var session = resolveProactiveSession(user, exerciseId);
        if (session == null) {
            throw new ConflictException("Cannot persist reveal: the exercise-chat session could not be resolved", "IrisMessage", "revealSessionConflict");
        }
        var message = new IrisMessage();
        message.addContent(new IrisTextMessageContent(hintText));
        message.setOrigin(IrisMessageOrigin.PROACTIVE_STRUGGLE);
        message.setProactiveEpisodeId(episodeId);
        message.setProactiveClientMessageId(clientMessageId);
        try {
            var saved = irisMessageService.saveMessage(message, session, IrisMessageSender.LLM);
            return IrisMessageResponseDTO.of(saved);
        }
        catch (DataIntegrityViolationException ex) {
            // Concurrent retry persisted first; re-select the now-existing row (race-safe upsert).
            return irisMessageRepository.findByProactiveClientMessageId(clientMessageId).map(IrisMessageResponseDTO::of)
                    .orElseThrow(() -> new IllegalStateException("Row vanished after unique-index violation on proactive_client_message_id=" + clientMessageId, ex));
        }
    }

    /**
     * Episode-wide first-terminal-wins outcome write. Writes {@code outcome} onto the episode's first-persisted
     * (smallest-id) row ONLY IF no row of the episode already carries a non-null outcome. Returns {@code true}
     * (applied) whenever a terminal outcome is established for the episode (whether THIS call wrote it or a prior one
     * did), and {@code false} only when no terminal outcome could be established because no row exists yet (deferred,
     * not an error - the client back-fills once the reveal/delivery row is persisted).
     *
     * <p>
     * Portable AND race-safe without a pessimistic lock or a same-table subquery (which would trip MySQL 1093):
     * <ul>
     * <li>The target row is the episode's SMALLEST-id row ({@link IrisMessageRepository#findFirstByProactiveEpisodeIdOrderByIdAsc}).
     * Ids are monotonic, so this target is stable under concurrent inserts (a delivery row that persists late gets a
     * larger id and can never become the target). Two concurrent writers therefore pick the SAME target row.</li>
     * <li>An episode-wide existence pre-check ({@link IrisMessageRepository#findEpisodeOutcomes}) makes first-terminal-wins
     * stable under out-of-order persistence: once ANY row of the episode holds an outcome, every later call is a no-op.</li>
     * <li>The row-scoped {@code WHERE id = ? AND proactive_outcome IS NULL} guard ({@link IrisMessageRepository#setProactiveOutcomeIfNull})
     * makes concurrent writes to that one stable target land at most once.</li>
     * <li>If the guarded update affects 0 rows (the target was concurrently given an outcome OR concurrently deleted),
     * a re-check decides the result: {@code true} if an outcome now exists episode-wide, else {@code false} (the row
     * vanished and nothing is established - defer so the client back-fills). This prevents a false {@code applied=true}.</li>
     * </ul>
     * Readers are episode-wide ({@code findEpisodeOutcomes}), so the physical row holding the outcome is immaterial.
     *
     * @param episodeId the client-allocated episode UUID
     * @param outcome   the terminal outcome to write
     * @return {@code true} if a terminal outcome is established for the episode; {@code false} if none could be
     *         established yet (no row persisted - the caller should back-fill once a row exists)
     */
    public boolean writeEpisodeOutcome(String episodeId, IrisProactiveOutcome outcome) {
        var target = irisMessageRepository.findFirstByProactiveEpisodeIdOrderByIdAsc(episodeId);
        if (target.isEmpty()) {
            return false;  // DEFERRED: no row persisted yet for this episode; client must back-fill
        }
        // Episode-wide first-terminal-wins: if any row already holds an outcome, this is a no-op (applied = true).
        if (!irisMessageRepository.findEpisodeOutcomes(episodeId).isEmpty()) {
            return true;
        }
        // Write to the episode's stable smallest-id row, guarded on that row still being null (row-scoped, MySQL-safe).
        int updated = irisMessageRepository.setProactiveOutcomeIfNull(target.get().getId(), outcome);
        if (updated == 0) {
            // The target was concurrently given an outcome or deleted: only report applied if an outcome now stands.
            return !irisMessageRepository.findEpisodeOutcomes(episodeId).isEmpty();
        }
        return true;
    }

    /**
     * Delete a superseded proactive message row, making stale-row suppression durable (not just live). The guards
     * (proactive-origin AND null outcome AND the row belongs to one of the user's sessions) and the delete run as ONE
     * atomic SQL statement ({@link IrisMessageRepository#deleteSupersededProactiveMessage}), so there is no
     * check-then-delete race: a concurrent outcome write can never cause a now-terminal row to be deleted. Missing or
     * already-deleted rows, non-proactive rows, other users' rows, and rows with a terminal outcome are all silent
     * noops (idempotent 204 semantics at the endpoint level).
     *
     * @param user      the requesting student
     * @param messageId the id of the message to delete
     */
    public void deleteSupersededProactiveMessage(User user, long messageId) {
        irisMessageRepository.deleteSupersededProactiveMessage(messageId, user.getId());
    }

    /**
     * Scoped cancel: remove the pending struggle job ONLY IF its stamped {@code requestToken} matches the
     * provided token, then release the single-flight marker. A non-matching token or no pending job is an
     * idempotent noop (204 at the endpoint level). This prevents {@code cancel(A)} from accidentally removing
     * a since-started run B that carries a different token.
     *
     * @param user         the requesting student (scopes the in-flight slot to this user)
     * @param exerciseId   the exercise id (scopes the in-flight slot)
     * @param requestToken the token that must match the pending job's stamped token
     */
    public void cancelOutstandingStruggleJob(User user, long exerciseId, String requestToken) {
        pyrisJobService.removeStruggleJobIfTokenMatches(user.getId(), exerciseId, requestToken);
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
    private IrisMessage saveProactiveMessage(IrisChatSession session, String result, @Nullable String episodeId) {
        var message = new IrisMessage();
        message.addContent(new IrisTextMessageContent(result));
        message.setOrigin(IrisMessageOrigin.PROACTIVE_STRUGGLE);
        if (episodeId != null) {
            message.setProactiveEpisodeId(episodeId);
        }
        return irisMessageService.saveMessage(message, session, IrisMessageSender.LLM);
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
    PersistedProactive persistProactiveMessage(User user, long exerciseId, String result, @Nullable String episodeId) {
        var session = resolveProactiveSession(user, exerciseId);
        if (session == null) {
            return null;
        }
        var saved = saveProactiveMessage(session, result, episodeId);
        return new PersistedProactive(session, saved);
    }

    /**
     * Returns true when the episode already has a terminal outcome persisted (DISMISSED, RECOVERED, or ABANDONED).
     * Used by the active branch to skip a late escalation that arrived after the student dismissed.
     *
     * <p>
     * Reads episode-wide: checks ALL rows tagged with the episodeId, not just the earliest, so the result is
     * stable under out-of-order persistence.
     *
     * @param episodeId the client-allocated episode UUID
     * @return true if a terminal outcome exists for this episode
     */
    boolean isEpisodeTerminal(String episodeId) {
        return !irisMessageRepository.findEpisodeOutcomes(episodeId).isEmpty();
    }

    /**
     * Latest submission for {@code (exercise, user)} - the same resolution the chat pipeline uses. Delegates to the
     * package-private {@code getLatestSubmissionIfExists} helper on {@link AbstractIrisChatSessionService} (callable
     * via the injected {@link IrisChatSessionService}, which lives in this package). Returns empty only when the
     * student genuinely has no submission yet (then no live code is shipped - accepted v1 limitation; do NOT forge a
     * submission).
     *
     * @param exercise the programming exercise (loaded with template/solution participations)
     * @param user     the student
     * @return the latest submission with eager results/feedback/build logs, or empty if none exists
     */
    private Optional<ProgrammingSubmission> latestSubmission(ProgrammingExercise exercise, User user) {
        return irisChatSessionService.getLatestSubmissionIfExists(exercise, user);
    }

    /**
     * Immutable snapshot of the synchronously-prepared trigger (ids + payload only - NO entity crosses threads).
     * The new episode/intent/confirmReason/requestToken fields are immutable value objects, safe to cross threads.
     */
    public record PreparedTrigger(long courseId, long exerciseId, long userId, String variant, String jobToken, @Nullable String intent, @Nullable StruggleEpisodeDTO episode,
            @Nullable String confirmReason, @Nullable String requestToken) {
    }

    /**
     * Why a trigger was (not) prepared, from a SINGLE settings read: a reserved trigger, or a rejection that is either
     * a deliberate course-off (Iris/proactive disabled, spec §13) or a transient in-flight skip (single-flight, §11).
     * Distinguishing the two lets the 202 carry an exact {@code courseDisabled} so a slow in-flight job is never
     * mis-read by the client as a course disable.
     */
    public record TriggerPreparation(@Nullable PreparedTrigger trigger, boolean courseDisabled) {

        public boolean accepted() {
            return trigger != null;
        }

        static TriggerPreparation triggered(PreparedTrigger trigger) {
            return new TriggerPreparation(trigger, false);
        }

        // NB: named courseOff() (not courseDisabled()) to avoid clashing with the auto-generated courseDisabled() accessor.
        static TriggerPreparation courseOff() {
            return new TriggerPreparation(null, true);
        }

        static TriggerPreparation inFlight() {
            return new TriggerPreparation(null, false);
        }
    }

    /** Outcome surfaced to the REST layer: accepted (with job token) or rejected, course-off carried for the 202. */
    public record StruggleTriggerOutcome(boolean accepted, boolean courseDisabled, @Nullable String jobToken) {
    }
}
