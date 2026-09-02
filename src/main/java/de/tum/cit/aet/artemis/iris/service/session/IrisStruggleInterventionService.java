package de.tum.cit.aet.artemis.iris.service.session;

import java.time.ZonedDateTime;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.account.service.UserAiPreferenceService;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.iris.config.IrisEnabled;
import de.tum.cit.aet.artemis.iris.domain.message.IrisAmbientDecision;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessage;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessageOrigin;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessageSender;
import de.tum.cit.aet.artemis.iris.domain.message.IrisProactiveEpisode;
import de.tum.cit.aet.artemis.iris.domain.message.IrisProactiveOutcome;
import de.tum.cit.aet.artemis.iris.domain.message.IrisTextMessageContent;
import de.tum.cit.aet.artemis.iris.domain.session.IrisChatMode;
import de.tum.cit.aet.artemis.iris.domain.session.IrisChatSession;
import de.tum.cit.aet.artemis.iris.dto.IrisMessageResponseDTO;
import de.tum.cit.aet.artemis.iris.dto.StruggleEpisodeDTO;
import de.tum.cit.aet.artemis.iris.dto.StruggleInterventionEventDTO;
import de.tum.cit.aet.artemis.iris.repository.IrisAmbientDecisionRepository;
import de.tum.cit.aet.artemis.iris.repository.IrisChatSessionRepository;
import de.tum.cit.aet.artemis.iris.repository.IrisMessageRepository;
import de.tum.cit.aet.artemis.iris.repository.IrisProactiveEpisodeRepository;
import de.tum.cit.aet.artemis.iris.repository.IrisSessionRepository;
import de.tum.cit.aet.artemis.iris.service.IrisMessageService;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisDTOService;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisJobService;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisPipelineService;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisCourseDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisRunState;
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

    /**
     * Width of {@code iris_ambient_decision.episode_id} and {@code iris_message.proactive_episode_id}. Both columns
     * are varchar(64); an episode id is a client-generated UUID, so 64 is generous.
     */
    private static final int MAX_EPISODE_ID_LENGTH = 64;

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

    private final IrisAmbientDecisionRepository irisAmbientDecisionRepository;

    private final IrisSessionRepository irisSessionRepository;

    private final IrisProactiveEpisodeRepository irisProactiveEpisodeRepository;

    private final UserAiPreferenceService userAiPreferenceService;

    private final TransactionTemplate transactionTemplate;

    /**
     * For work that must commit or fail on its own, independently of whatever transaction the caller is in: the
     * episode registration at trigger time, and the ambient upsert. Both can hit the unique constraint, and a
     * constraint violation marks its transaction rollback-only, so catching it inside the caller's transaction would
     * turn a handled duplicate into an {@code UnexpectedRollbackException} at that transaction's commit.
     */
    private final TransactionTemplate requiresNewTransactionTemplate;

    @Value("${artemis.iris.proactive.struggle.confidence-threshold:0.6}")
    private double confidenceThreshold;

    public IrisStruggleInterventionService(ProgrammingExerciseRepository programmingExerciseRepository, AuthorizationCheckService authCheckService,
            IrisSettingsService irisSettingsService, IrisChatSessionRepository irisChatSessionRepository, PyrisDTOService pyrisDTOService,
            PyrisPipelineService pyrisPipelineService, PyrisJobService pyrisJobService, UserRepository userRepository, IrisChatSessionService irisChatSessionService,
            IrisMessageService irisMessageService, IrisChatWebsocketService irisChatWebsocketService, IrisMessageRepository irisMessageRepository,
            IrisAmbientDecisionRepository irisAmbientDecisionRepository, PlatformTransactionManager transactionManager, UserAiPreferenceService userAiPreferenceService,
            IrisSessionRepository irisSessionRepository, IrisProactiveEpisodeRepository irisProactiveEpisodeRepository) {
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
        this.irisAmbientDecisionRepository = irisAmbientDecisionRepository;
        this.irisSessionRepository = irisSessionRepository;
        this.irisProactiveEpisodeRepository = irisProactiveEpisodeRepository;
        this.userAiPreferenceService = userAiPreferenceService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.requiresNewTransactionTemplate = new TransactionTemplate(transactionManager);
        this.requiresNewTransactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
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
     * @param intent           the slot intent ({@code decide} | {@code confirm_close})
     * @param episode          the client-allocated episode block (null when not sent by an older client)
     * @param confirmReason    the close-mode discriminator (null unless intent is {@code confirm_close})
     * @param requestToken     the scoped-cancel identity (A10); null on older clients
     * @param proactivityMode  the presence level ({@code pull} | {@code push}); enforces Pull in the callback (spec §4/§10)
     * @param user             the requesting student
     * @return the trigger outcome (accepted + job token, or rejected with the course-off flag for the 202)
     */
    public StruggleTriggerOutcome requestStruggleIntervention(long exerciseId, PyrisStruggleSignalDTO signal, Map<String, String> uncommittedFiles, @Nullable String intent,
            @Nullable StruggleEpisodeDTO episode, @Nullable String confirmReason, @Nullable String requestToken, @Nullable String proactivityMode, User user) {
        var prepared = prepareTrigger(exerciseId, user, intent, episode, confirmReason, requestToken, proactivityMode);
        if (!prepared.accepted()) {
            return new StruggleTriggerOutcome(false, prepared.courseDisabled(), null);
        }
        var p = prepared.trigger();
        CompletableFuture.runAsync(() -> sendToPyris(p, signal, uncommittedFiles)).exceptionally(e -> {
            log.error("Error sending struggle intervention to Iris for exercise {} user {}", p.exerciseId(), p.userId(), e);
            // The endpoint already answered 202, so the client is waiting on a terminal frame that no callback will
            // ever deliver for this run. Notify BEFORE releasing, so the slot is still ours while the frame goes out.
            var reserved = pyrisJobService.getJob(p.jobToken());
            if (reserved instanceof StruggleInterventionJob struggleJob) {
                emitTerminalCompletion(struggleJob);
            }
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
     * @param exerciseId      the programming exercise id
     * @param user            the requesting student
     * @param intent          the slot intent; passed through to the job so async callbacks can route by intent
     * @param episode         the client episode; the episodeId is stamped on the job for correlation
     * @param confirmReason   the close-mode discriminator; stamped on the job for A11 routing
     * @param requestToken    the scoped-cancel UUID; stamped on the job for A10 cancel matching
     * @param proactivityMode the presence level ({@code pull} | {@code push}); stamped on the job and forwarded to Pyris for tone
     * @return a typed preparation: the reserved trigger, or a rejection tagged course-off vs in-flight
     */
    public TriggerPreparation prepareTrigger(long exerciseId, User user, @Nullable String intent, @Nullable StruggleEpisodeDTO episode, @Nullable String confirmReason,
            @Nullable String requestToken, @Nullable String proactivityMode) {
        var exercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
        var course = exercise.getCourseViaExerciseGroupOrCourseMember();
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.STUDENT, exercise, user);
        var settings = irisSettingsService.getSettingsForCourse(course);
        if (!settings.enabled() || !settings.proactiveStruggleEnabled()) {
            return TriggerPreparation.courseOff();
        }
        String episodeId = episode != null ? episode.episodeId() : null;
        var tokenOpt = pyrisJobService.addStruggleInterventionJobIfNonePending(course.getId(), user.getId(), exerciseId, intent, episodeId, confirmReason, requestToken,
                proactivityMode);
        if (tokenOpt.isEmpty()) {
            log.info("Struggle intervention already in flight for user {} exercise {}, skipping", user.getId(), exerciseId);
            return TriggerPreparation.inFlight();
        }
        // Register the episode BEFORE the pipeline is dispatched. The caller fires Pyris off-thread only after this
        // method returns, so no callback can arrive before the row exists, and every later path can therefore lock a
        // row that is already there. Registering after the reservation rather than before it keeps rejected and
        // in-flight triggers from leaving rows behind; a failure here releases the slot, so it is never leaked.
        String registrableEpisodeId = usableEpisodeId(episodeId);
        if (registrableEpisodeId != null) {
            try {
                registerEpisode(user.getId(), exerciseId, registrableEpisodeId);
            }
            catch (RuntimeException exception) {
                pyrisJobService.releaseStruggleInFlightJob(tokenOpt.get(), user.getId(), exerciseId);
                throw exception;
            }
        }
        return TriggerPreparation.triggered(new PreparedTrigger(course.getId(), exerciseId, user.getId(), settings.variant().jsonValue(), settings.supportLevel().jsonValue(),
                tokenOpt.get(), intent, episode, confirmReason, requestToken, proactivityMode));
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
        if (!userAiPreferenceService.hasOptedIntoLlmUsage(user.getId())) {
            log.info("Struggle intervention skipped: user {} is no longer opted into LLM usage", p.userId());
            // The endpoint already answered 202, so the client is waiting on a terminal frame that no callback will
            // ever deliver for this bailed run. Emit the intent-shaped completion BEFORE releasing (the same order as
            // the dispatch-failure path above), so the slot is still ours while the frame goes out and the client's
            // in-flight request clears instead of hanging until its own timeout.
            if (pyrisJobService.getJob(p.jobToken()) instanceof StruggleInterventionJob struggleJob) {
                emitTerminalCompletion(struggleJob);
            }
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
        pyrisPipelineService.executeStruggleInterventionPipeline(p.variant(), p.supportLevel(), p.jobToken(), user, signal, exerciseDTO, submissionDTO, courseDTO, chatHistory,
                p.exerciseId(), p.intent(), p.episode(), p.proactivityMode());
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

        String episodeId = usableEpisodeId(job.episodeId());
        // A job that carried an id we cannot use is NOT the same as one that carried none. Both stop the id from
        // reaching any lookup or column, but a legacy job without an episode still gets its ambient bookkeeping
        // pointer below, while an unusable id stays silent: it can never be revealed, so pointing the client at it
        // would only produce a 409.
        boolean carriedUnusableEpisodeId = job.episodeId() != null && episodeId == null;
        String result = statusUpdate.result();

        if (result == null || result.isEmpty()) {
            // Nothing to surface; always emit a completion frame so the client's in-flight decide clears. The
            // confidence still travels: the client logs it for the eval (spec §12) even when nothing is shown.
            irisChatWebsocketService.sendStruggleEvent(user, StruggleInterventionEventDTO.silentDecide(job.exerciseId(), confidence, episodeId, statusUpdate.rationale()));
            return;
        }

        switch (finalAction) {
            case "active" -> {
                // Skip if this episode is already terminal (late escalation arriving after the student dismissed).
                // A dismiss that commits between this check and the append below is not caught; see isEpisodeTerminal.
                if (episodeId != null && isEpisodeTerminal(episodeId, user.getId(), job.exerciseId())) {
                    irisChatWebsocketService.sendStruggleEvent(user, StruggleInterventionEventDTO.silentDecide(job.exerciseId(), confidence, episodeId, statusUpdate.rationale()));
                    break;
                }
                // Resolve the exercise-chat session; drop defensively if not exercise-bound.
                var session = resolveProactiveSession(user, job.exerciseId());
                if (session == null) {
                    // Structural mismatch: resolved session is not exercise-bound. Emit a silent completion frame
                    // so the client's in-flight decide always clears (finding 2 fix).
                    irisChatWebsocketService.sendStruggleEvent(user, StruggleInterventionEventDTO.silentDecide(job.exerciseId(), confidence, episodeId, statusUpdate.rationale()));
                    break;
                }
                // Persist the message with bounded retry on transient DB failures (spec §12). A null result means
                // the message was dropped; the active control event below is still emitted with messageId=null so
                // the client's in-flight decide always clears (finding 1 fix).
                var appended = saveProactiveMessageWithRetry(session, user, job.exerciseId(), result, episodeId, null);
                if (appended.terminal()) {
                    // The episode went terminal between the cheap pre-check above and the locked write. Nothing was
                    // persisted, so complete silently rather than announcing a hint the student already closed.
                    irisChatWebsocketService.sendStruggleEvent(user, StruggleInterventionEventDTO.silentDecide(job.exerciseId(), confidence, episodeId, statusUpdate.rationale()));
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
                if (episodeId != null && isEpisodeTerminal(episodeId, user.getId(), job.exerciseId())) {
                    irisChatWebsocketService.sendStruggleEvent(user, StruggleInterventionEventDTO.silentDecide(job.exerciseId(), confidence, episodeId, statusUpdate.rationale()));
                    break;
                }
                // Pull model (spec §5): do NOT persist. Resolve the session only to supply its id on the event
                // so the client knows which session to reveal into when the student clicks (A10/C2).
                var session = resolveProactiveSession(user, job.exerciseId());
                if (session == null) {
                    // Structural mismatch: resolved session is not exercise-bound. A null-session ambient
                    // pointer is unrevealable by the client; emit a silent completion frame instead (finding 3 fix).
                    irisChatWebsocketService.sendStruggleEvent(user, StruggleInterventionEventDTO.silentDecide(job.exerciseId(), confidence, episodeId, statusUpdate.rationale()));
                    break;
                }
                // Record what we are about to offer BEFORE telling the client about it, so a reveal that races the
                // event still finds the decision. With an episode id, announce the ambient pointer ONLY when recording
                // left a revealable decision behind it: an over-long id or an episode whose previous offer was already
                // revealed records nothing, and pointing the client at a reveal that would 409 helps no one, so
                // complete it silently instead. Without an episode id there is nothing a reveal could address; the
                // pointer is still emitted for the client's own bookkeeping, exactly as before.
                Boolean offered = episodeId == null ? !carriedUnusableEpisodeId
                        : transactionTemplate.execute(status -> isEpisodeTerminalUnderLock(episodeId, user.getId(), job.exerciseId()) ? null
                                : recordAmbientDecision(user.getId(), job.exerciseId(), episodeId, result));
                if (offered == null) {
                    // The episode went terminal between the fast path above and the locked check. Nothing was
                    // offered, so complete silently instead of pointing the client at a hint it has already closed.
                    irisChatWebsocketService.sendStruggleEvent(user, StruggleInterventionEventDTO.silentDecide(job.exerciseId(), confidence, episodeId, statusUpdate.rationale()));
                    break;
                }
                if (offered) {
                    irisChatWebsocketService.sendStruggleEvent(user, new StruggleInterventionEventDTO(job.exerciseId(), "decide", "ambient", result, session.getId(), null,
                            statusUpdate.anchorFile(), statusUpdate.anchorLine(), statusUpdate.inlineHint(), confidence, episodeId, null, null, null, statusUpdate.rationale()));
                }
                else {
                    irisChatWebsocketService.sendStruggleEvent(user, StruggleInterventionEventDTO.silentDecide(job.exerciseId(), confidence, episodeId, statusUpdate.rationale()));
                }
            }
            default -> {
                // silent (or downgraded): emit a noop completion frame so the client's in-flight decide always clears.
                irisChatWebsocketService.sendStruggleEvent(user, StruggleInterventionEventDTO.silentDecide(job.exerciseId(), confidence, episodeId, statusUpdate.rationale()));
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
     * Terminal gate (delivered reasons only): reads {@link #isEpisodeTerminal(String, long, long)} as a cheap fast
     * path, and the authoritative check runs again under the episode's registry lock in the same transaction as the
     * write. If terminal at either point, nothing is persisted and a noop event goes out. Otherwise the closing row
     * and its {@code RECOVERED} outcome commit together, and only then is anything broadcast. Outcome-last still
     * holds inside that transaction: the row is inserted before its outcome, so the close is never gated away by its
     * own outcome write.
     *
     * @param job          the struggle-intervention job (ids + episodeId + confirmReason)
     * @param statusUpdate the Pyris response payload
     */
    public void handleConfirmClose(StruggleInterventionJob job, PyrisStruggleInterventionStatusUpdateDTO statusUpdate) {
        var user = userRepository.findByIdElseThrow(job.userId());
        String episodeId = usableEpisodeId(job.episodeId());
        String confirmReason = job.confirmReason();
        boolean resolved = statusUpdate.resolved() != null ? statusUpdate.resolved() : false;

        // parked_progress (and null/unknown fail-closed): silent on both results.
        // Persist nothing, write no outcome. Emit bare completion event only.
        if (!"progress".equals(confirmReason)) {
            if (!"parked_progress".equals(confirmReason)) {
                log.warn("Unexpected confirmReason '{}' on confirm_close for episodeId={} exercise={} user={}, failing closed to parked_progress semantics", confirmReason,
                        episodeId, job.exerciseId(), job.userId());
            }
            irisChatWebsocketService.sendStruggleEvent(user, new StruggleInterventionEventDTO(job.exerciseId(), "confirm_close", null, null, null, null, null, null, null, null,
                    episodeId, resolved, null, null, statusUpdate.rationale()));
            return;
        }

        // Terminal gate (delivered reasons only): if the episode already has a terminal outcome (e.g. the
        // student DISMISSED mid-flight), skip persist and emit a noop event. Neither this check nor the
        // persist-then-outcome pair below is atomic against a concurrent dismiss; see isEpisodeTerminal.
        if (episodeId != null && isEpisodeTerminal(episodeId, user.getId(), job.exerciseId())) {
            irisChatWebsocketService.sendStruggleEvent(user, new StruggleInterventionEventDTO(job.exerciseId(), "confirm_close", null, null, null, null, null, null, null, null,
                    episodeId, resolved, null, null, statusUpdate.rationale()));
            return;
        }

        if (resolved) {
            // progress resolved=true: persist closing message (1), broadcast live (1b), write RECOVERED (2).
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
            if (persisted != null && persisted.terminal()) {
                // The episode went terminal between the gate above and the locked write, so nothing was persisted and
                // nothing was recovered. Reporting resolved=true here would tell the client a close succeeded that
                // never happened; emit the same noop the gate emits instead.
                irisChatWebsocketService.sendStruggleEvent(user, new StruggleInterventionEventDTO(job.exerciseId(), "confirm_close", null, null, null, null, null, null, null, null,
                        episodeId, resolved, null, null, statusUpdate.rationale()));
                return;
            }
            Long messageId = null;
            if (persisted != null) {
                // Broadcast the committed row so the webview receives it through the single chat-ws transport.
                irisChatWebsocketService.sendMessage(persisted.session(), persisted.saved(), terminalRunStateOf(statusUpdate), statusUpdate.error());
                messageId = persisted.saved().getId();
            }
            irisChatWebsocketService.sendStruggleEvent(user, new StruggleInterventionEventDTO(job.exerciseId(), "confirm_close", null, null, null, messageId, null, null, null,
                    null, episodeId, true, closingSentence, episodeLabel, statusUpdate.rationale()));
        }
        else {
            // progress resolved=false: quiet (slot stays TAKEN, no offer posted, no outcome).
            irisChatWebsocketService.sendStruggleEvent(user, new StruggleInterventionEventDTO(job.exerciseId(), "confirm_close", null, null, null, null, null, null, null, null,
                    episodeId, false, null, null, statusUpdate.rationale()));
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
        // The registry lock comes first, before the ambient row and before anything touches the session, and it is the
        // order every other path uses. Until it existed the reveal had no terminal check at all: a student could
        // dismiss an offer and still reveal it, because the ambient decision's own lock says nothing about the episode.
        if (isEpisodeTerminalUnderLock(episodeId, user.getId(), exerciseId)) {
            throw new ConflictException("The ambient hint for this episode can no longer be revealed", "IrisMessage", "revealEpisodeTerminal");
        }
        var decision = irisAmbientDecisionRepository.findForReveal(user.getId(), exerciseId, episodeId)
                .orElseThrow(() -> new ConflictException("No ambient hint was offered for this episode", "IrisMessage", "revealWithoutDecision"));
        if (decision.getConsumedAt() != null) {
            // Already revealed. Return that reveal's row so a replay is idempotent rather than a second insert;
            // if the row is gone (superseded and deleted), the offer is spent and there is nothing to surface.
            return irisMessageRepository.findById(decision.getConsumedMessageId() == null ? -1L : decision.getConsumedMessageId()).map(IrisMessageResponseDTO::of)
                    .orElseThrow(() -> new ConflictException("The ambient hint for this episode was already revealed", "IrisMessage", "revealAlreadyConsumed"));
        }

        // Append through the guarded helper, which re-checks the session's exercise binding under the session write
        // lock. The ambient-decision lock held here says nothing about the session: a run for a DIFFERENT exercise can
        // switch this same session between resolveProactiveSession above and the write, and the reveal would then
        // persist the hint into that other exercise's history.
        var saved = saveProactiveMessage(session, exerciseId, decision.getHintText(), episodeId);
        if (saved == null) {
            // Fail the whole reveal rather than consuming the offer: rolling back leaves the decision unconsumed, so
            // the student can reveal it again once the session is back on this exercise.
            throw new ConflictException("Cannot persist reveal: the chat session moved to another exercise", "IrisMessage", "revealSessionConflict");
        }

        // The locked entity is managed, so consuming it is part of this transaction's flush.
        decision.setConsumedAt(ZonedDateTime.now());
        decision.setConsumedMessageId(saved.getId());
        irisAmbientDecisionRepository.save(decision);
        return IrisMessageResponseDTO.of(saved);
    }

    /**
     * Record the ambient hint Artemis is about to offer, so the later reveal can persist the server's own text
     * instead of whatever the caller sends back.
     *
     * <p>
     * A repeated decision callback for the same episode must not create a second offer, and must not overwrite one
     * the student has already revealed. The unique constraint on (user, exercise, episode) enforces the first part;
     * an existing unconsumed row simply has its text refreshed to the newest decision, and a consumed one is left
     * alone because its message already exists.
     *
     * @param userId     the student the hint is offered to
     * @param exerciseId the exercise the hint belongs to
     * @param episodeId  the client-allocated episode id, never null here
     * @param hintText   the hint as authored by Pyris
     * @return {@code true} when the triple now carries a revealable (unconsumed) decision the client may be
     *         pointed at; {@code false} when nothing revealable was recorded (over-long id, or an episode whose
     *         previous offer the student already revealed). The caller announces an ambient pointer only on
     *         {@code true}, so it never sends the client to a reveal that would 409.
     */
    private boolean recordAmbientDecision(long userId, long exerciseId, String episodeId, String hintText) {
        // Reject a blank or over-long episode id FIRST, before any refresh or insert. A reveal rejects a blank id
        // outright, and an over-long id fails on the column width; either way the client would be told an offer exists
        // that a reveal can never materialise. This has to precede refreshIfUnconsumed: a legacy unconsumed row with a
        // blank id would otherwise be refreshed, return true, and get announced as an unrevealable offer. The trigger
        // already bounds the length via bean validation; this is the defence at the recording boundary for any path
        // that reaches here unvalidated, and it also covers the blank case bean validation cannot express.
        if (episodeId.isBlank() || episodeId.length() > MAX_EPISODE_ID_LENGTH) {
            log.warn("Refusing to record an ambient decision for exercise={} user={}: episode id is blank or exceeds the {}-character limit (length {})", exerciseId, userId,
                    MAX_EPISODE_ID_LENGTH, episodeId.length());
            return false;
        }
        // Its own transaction, always. The caller now holds the episode's registry row lock, and the duplicate insert
        // below marks its transaction rollback-only: catching it inside the caller's transaction would turn a handled
        // duplicate into an UnexpectedRollbackException when that transaction commits. The consequence is that the
        // offer commits even if the caller later rolls back, which is the same order as before the registry, where
        // the decision was already recorded before the event went out.
        try {
            return Boolean.TRUE.equals(requiresNewTransactionTemplate.execute(status -> {
                // Refresh in place without loading the row first. Saving a detached aggregate merges EVERY column: a
                // reveal committing between the read and the save would be overwritten, resetting consumedAt and
                // consumedMessageId to NULL and making an already-revealed offer revealable a second time.
                if (irisAmbientDecisionRepository.refreshIfUnconsumed(userId, exerciseId, episodeId, hintText) > 0) {
                    return true;
                }
                // Zero rows: either no offer exists for this episode yet, or the student already revealed the previous
                // one. Insert and let the unique constraint on (user, exercise, episode) decide between the two.
                var decision = new IrisAmbientDecision();
                decision.setUserId(userId);
                decision.setExerciseId(exerciseId);
                decision.setEpisodeId(episodeId);
                decision.setHintText(hintText);
                decision.setCreatedAt(ZonedDateTime.now());
                irisAmbientDecisionRepository.saveAndFlush(decision);
                return true;
            }));
        }
        catch (DataIntegrityViolationException ex) {
            // A consumed row already occupies this triple: the student already revealed the previous offer for this
            // episode, so there is nothing fresh to reveal and no ambient pointer should be announced. Caught outside
            // the template on purpose: inside, the failed insert has already made that transaction rollback-only.
            log.debug("Ambient decision for episode {} not recorded: already present", episodeId);
            return false;
        }
    }

    /**
     * Emit the terminal completion frame for a run that ended without a decision, so the client's in-flight
     * request always clears. Every other terminal path already guarantees this: {@code handleDecision} emits a
     * {@code silent} frame on each drop, and {@code handleConfirmClose} emits a bare completion on each early
     * return. Without this, a Pyris {@code FAILED} run or a post-202 dispatch failure leaves the client waiting
     * until its own timeout.
     *
     * <p>
     * The frame shape follows the intent, mirroring the two families above: {@code decide} (and the legacy null
     * intent) completes as {@code action="silent"}, while {@code confirm_close} completes as
     * {@code resolved=false} - a failed close must not read as "the episode is resolved".
     *
     * @param job the struggle-intervention job whose run ended without a decision
     */
    public void emitTerminalCompletion(StruggleInterventionJob job) {
        try {
            var user = userRepository.findByIdElseThrow(job.userId());
            String episodeId = usableEpisodeId(job.episodeId());
            if ("confirm_close".equals(job.intent())) {
                irisChatWebsocketService.sendStruggleEvent(user, StruggleInterventionEventDTO.unresolvedClose(job.exerciseId(), episodeId));
            }
            else {
                irisChatWebsocketService.sendStruggleEvent(user, StruggleInterventionEventDTO.silentDecide(job.exerciseId(), null, episodeId, null));
            }
        }
        catch (Exception e) {
            // Never let the completion frame break the caller's cleanup: the marker release in the finally block
            // matters more than the notification, and a missing frame degrades to the client's own timeout.
            log.warn("Could not emit terminal completion for struggle job {} exercise {} user {}", job.jobId(), job.exerciseId(), job.userId(), e);
        }
    }

    /**
     * The episode id a callback may actually use as an identity, or null when the job carries none it can.
     *
     * <p>
     * A blank id is not an identity: persisted on a message it would key every episode-scoped lookup, so the first
     * blank-id episode to end would make every later one read as that same finished episode. An over-long id does not
     * fit {@code proactive_episode_id} either. The trigger endpoint rejects both, but a job is not covered by that
     * validation: the job map keeps entries with a TTL, so a run minted before this validation existed can still have
     * its callback handled by this code after a deployment. Treating such an id as "no episode" degrades to the
     * well-defined legacy path instead of corrupting the episode keyspace.
     *
     * @param episodeId the id stamped on the job, possibly null
     * @return the id when it can serve as an identity, null otherwise
     */
    private static @Nullable String usableEpisodeId(@Nullable String episodeId) {
        if (episodeId == null || episodeId.isBlank() || episodeId.length() > MAX_EPISODE_ID_LENGTH) {
            return null;
        }
        return episodeId;
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
     * {@code sentAt}. Idempotency is scoped to {@code (user, exercise, episode)} and enforced by the ambient
     * decision record, not by any client-supplied key: a replay finds the decision already consumed and returns
     * the row that reveal created, rather than inserting a second one.
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
     * Episode-wide first-terminal-wins outcome write. Writes {@code outcome} onto the episode's first-persisted
     * (smallest-id) row ONLY IF no row of the episode already carries a non-null outcome. Returns {@code true}
     * (applied) whenever a terminal outcome is established for the episode (whether THIS call wrote it or a prior one
     * did), and {@code false} only when no terminal outcome could be established because no row exists yet (deferred,
     * not an error - the client back-fills once the reveal/delivery row is persisted).
     *
     * <p>
     * Portable AND race-safe without a pessimistic lock or a same-table subquery (which would trip MySQL 1093):
     * <ul>
     * <li>The target row is the episode's SMALLEST-id row ({@link IrisMessageRepository#findEpisodeRowsForUserOrderByIdAsc}).
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
     * <p>
     * SCOPED to the requesting user's own episode rows in the given exercise:
     * {@code episodeId} is a client-generated UUID, so an unscoped write would let any student write an outcome onto
     * another student's episode by guessing/replaying the id (IDOR). The {@code userId} scope closes that; the
     * {@code exerciseId} scope closes the same reuse INSIDE one student, whose client can send one id for two
     * exercises. Both the target-row lookup and the episode-wide outcome reads carry both predicates, so an episode
     * id that belongs elsewhere is indistinguishable from one that does not exist yet (deferred, never a foreign write).
     *
     * @param episodeId  the client-allocated episode UUID
     * @param outcome    the terminal outcome to write
     * @param userId     the requesting user; only this user's own episode rows are read or written
     * @param exerciseId the exercise the episode belongs to; only rows stamped with it are read or written
     * @return {@code true} if a terminal outcome is established for the episode; {@code false} if none could be
     *         established yet (no row persisted - the caller should back-fill once a row exists)
     */
    public boolean writeEpisodeOutcome(String episodeId, IrisProactiveOutcome outcome, long userId, long exerciseId) {
        if (episodeId == null || episodeId.isBlank()) {
            // A blank id is not an episode identity, and treating it as one is how distinct episodes end up sharing
            // an outcome. The trigger endpoint rejects blank ids outright, but this method is also reached from the
            // {episodeId} path variable, which validation does not cover.
            return false;
        }
        return Boolean.TRUE.equals(transactionTemplate.execute(status -> {
            var locked = irisProactiveEpisodeRepository.findForUpdate(userId, exerciseId, episodeId);
            if (locked.isEmpty()) {
                return writeLegacyEpisodeOutcome(episodeId, outcome, userId, exerciseId);
            }
            var episode = locked.get();
            // Under the write lock nothing else can establish an outcome between this read and the write below, so
            // the first terminal value is decided here rather than raced for.
            var standing = episode.getOutcome();
            if (standing == null) {
                irisProactiveEpisodeRepository.setOutcomeIfNull(episode.getId(), outcome);
                standing = outcome;
            }
            mirrorOutcomeOntoMessageRow(episodeId, userId, exerciseId, standing);
            // A registered episode can always record an outcome, even before its first message exists. That is the
            // whole point of the registry, and it is why this never defers.
            return true;
        }));
    }

    /**
     * The pre-registry outcome write, kept for episodes that have no registry row: those from before this feature
     * carried one, or a job still in flight across the deployment that introduced it. Behaviour is unchanged, down to
     * returning {@code false} when no message row exists yet so the client back-fills once one does.
     *
     * @param episodeId  the client-allocated episode UUID
     * @param outcome    the terminal outcome to write
     * @param userId     the requesting user
     * @param exerciseId the exercise the episode belongs to
     * @return whether a terminal outcome now stands for the episode
     */
    private boolean writeLegacyEpisodeOutcome(String episodeId, IrisProactiveOutcome outcome, long userId, long exerciseId) {
        var episodeRows = irisMessageRepository.findEpisodeRowsForUserOrderByIdAsc(episodeId, userId, exerciseId);
        if (episodeRows.isEmpty()) {
            return false;  // DEFERRED: no row persisted yet for this episode under this user's scope; client must back-fill
        }
        var target = episodeRows.get(0);
        // Episode-wide first-terminal-wins: if any row already holds an outcome, this is a no-op (applied = true).
        if (!irisMessageRepository.findEpisodeOutcomes(episodeId, userId, exerciseId).isEmpty()) {
            return true;
        }
        // Write to the episode's stable smallest-id row, guarded on that row still being null (row-scoped, MySQL-safe).
        int updated = irisMessageRepository.setProactiveOutcomeIfNull(target.getId(), outcome);
        if (updated == 0) {
            // The target was concurrently given an outcome or deleted: only report applied if an outcome now stands.
            return !irisMessageRepository.findEpisodeOutcomes(episodeId, userId, exerciseId).isEmpty();
        }
        return true;
    }

    /**
     * Copy the episode's standing outcome onto its first-persisted message row, so the history replayed to Pyris and
     * the message DTO keep carrying it. Subordinate to the registry: this is a projection, not the decision, and it
     * simply does nothing while the episode has no message row yet.
     *
     * @param episodeId  the client-allocated episode UUID
     * @param userId     the owning user
     * @param exerciseId the exercise the episode belongs to
     * @param outcome    the outcome that stands on the registry
     */
    private void mirrorOutcomeOntoMessageRow(String episodeId, long userId, long exerciseId, IrisProactiveOutcome outcome) {
        var episodeRows = irisMessageRepository.findEpisodeRowsForUserOrderByIdAsc(episodeId, userId, exerciseId);
        if (episodeRows.isEmpty()) {
            return;
        }
        irisMessageRepository.setProactiveOutcomeIfNull(episodeRows.get(0).getId(), outcome);
    }

    /**
     * Enforce that {@code user} holds at least the STUDENT role for the given exercise. Binds the {@code exerciseId}
     * path variable of the episode-outcome endpoint to a real authorization check, closing the IDOR where any
     * authenticated student could record (or probe) an outcome for an episode in an exercise they are not enrolled in.
     * This is a pure authorization gate: it does NOT touch the LLM opt-in (recording a reaction to an already
     * delivered hint must never be rejected on opt-in, spec §10), only course/exercise membership.
     *
     * @param exerciseId the programming exercise id from the request path
     * @param user       the requesting user (must carry groups + authorities)
     */
    public void checkAtLeastStudentForExercise(long exerciseId, User user) {
        var exercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.STUDENT, exercise, user);
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
     * This is also the compensation for the check-then-write window on {@link #isEpisodeTerminal}: a hint that was
     * persisted after its episode went terminal is removed here rather than left in the history, where it would keep
     * being replayed to Pyris as something the tutor said. The delete is therefore not merely a client convenience,
     * and a client that only hides such a message locally leaves the server's history wrong.
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
     * @param session    the already-resolved exercise-chat session to persist into
     * @param user       the student the proactive message belongs to (logging scope)
     * @param exerciseId the programming exercise id the message is bound to (logging scope)
     * @param result     the proactive message text returned by the gate
     * @param episodeId  the client-allocated episode UUID; stamped on the persisted message when non-null
     * @return the saved message, or null if it could not be persisted
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
                    if (episodeId != null && isEpisodeTerminalUnderLock(episodeId, user.getId(), exerciseId)) {
                        return ProactiveAppend.alreadyTerminal();
                    }
                    var saved = saveProactiveMessage(session, exerciseId, result, episodeId);
                    if (saved != null && episodeId != null && outcomeOnSuccess != null) {
                        // Same transaction as the append, still under the same lock. Splitting the two is what let a
                        // concurrent dismiss land between a committed close row and its own outcome.
                        recordOutcomeUnderLock(episodeId, user.getId(), exerciseId, outcomeOnSuccess);
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
     * Record the episode's terminal outcome while its registry row is already write-locked by the caller, and mirror
     * it onto the message row. An unregistered episode has no row to carry the outcome, so it falls back to the
     * pre-registry write, where the message row is the only record there is.
     *
     * @param episodeId  the client-allocated episode UUID
     * @param userId     the owning user
     * @param exerciseId the exercise the episode belongs to
     * @param outcome    the terminal outcome to record
     */
    private void recordOutcomeUnderLock(String episodeId, long userId, long exerciseId, IrisProactiveOutcome outcome) {
        var episode = irisProactiveEpisodeRepository.find(userId, exerciseId, episodeId);
        if (episode.isEmpty()) {
            // Unregistered: the outcome has nowhere to live but the message row, which is exactly where it lived
            // before the registry. Writing it there keeps such an episode behaving as it always did.
            writeLegacyEpisodeOutcome(episodeId, outcome, userId, exerciseId);
            return;
        }
        if (episode.get().getOutcome() == null) {
            irisProactiveEpisodeRepository.setOutcomeIfNull(episode.get().getId(), outcome);
        }
        mirrorOutcomeOntoMessageRow(episodeId, userId, exerciseId, outcome);
    }

    /**
     * Whether the episode is terminal, deciding it under the episode's registry write lock so the caller can write in
     * the same transaction without anything interleaving. Falls back to the message rows for an episode that has no
     * registry row, which behaves exactly as this feature did before the registry existed.
     *
     * @param episodeId  the client-allocated episode UUID
     * @param userId     the owning user
     * @param exerciseId the exercise the episode belongs to
     * @return true if a terminal outcome stands for this episode
     */
    private boolean isEpisodeTerminalUnderLock(String episodeId, long userId, long exerciseId) {
        var locked = irisProactiveEpisodeRepository.findForUpdate(userId, exerciseId, episodeId);
        if (locked.isPresent()) {
            return locked.get().getOutcome() != null;
        }
        return !irisMessageRepository.findEpisodeOutcomes(episodeId, userId, exerciseId).isEmpty();
    }

    /**
     * The result of an append attempt. {@code terminal} and a null {@code message} are different outcomes and the
     * caller has to tell them apart: a terminal episode completes silently, while a dropped message still emits its
     * control event with {@code messageId=null} so the client's in-flight request clears.
     *
     * @param terminal whether the episode was already terminal, so nothing was written on purpose
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
     * Returns true when the episode already has a terminal outcome persisted (DISMISSED, RECOVERED, or ABANDONED).
     * Used by the active branch to skip a late escalation that arrived after the student dismissed.
     *
     * <p>
     * Reads episode-wide: checks ALL rows tagged with the episodeId, not just the earliest, so the result is
     * stable under out-of-order persistence.
     *
     * <p>
     * KNOWN AND ACCEPTED: this is a check, not a claim. Every caller reads it and then writes in a separate
     * statement, so a terminal outcome that commits in between is not seen, and the callback persists its message
     * (or announces its ambient offer) after the episode is already closed. The same window exists between the
     * closing-message insert and the {@code RECOVERED} write in {@link #handleConfirmClose}.
     *
     * <p>
     * It cannot be closed by locking here. The obvious mutex, the chat session, is not one: sessions are resolved as
     * "the latest for (user, exercise)" with no uniqueness constraint, so an episode's existing row and the next
     * append can sit in different sessions and would take different locks. Nor can the episode lock itself: before
     * its first message row is written, an episode has no row to lock, which is also why an outcome arriving first
     * can only be deferred. Closing it needs the episode to become a row of its own, created when the trigger is
     * accepted (the episode id arrives synchronously in {@link #prepareTrigger}), holding the terminal outcome and
     * serving as the mutex for the append, the reveal and the outcome write alike.
     *
     * <p>
     * Until then the compensation is the client's: it drops a decide event for an episode it already closed, and
     * removes the row through {@link #deleteSupersededProactiveMessage}, which is what that endpoint exists for. The
     * proactive feature is off per course by default, so nothing reaches a student before this is decided.
     *
     * @param episodeId  the client-allocated episode UUID
     * @param userId     the job's owning user; only outcomes on rows in this user's sessions are considered
     * @param exerciseId the exercise the job ran for; an episode id reused for another exercise is not this episode
     * @return true if a terminal outcome exists for this episode
     */
    /**
     * Register the episode so it has a row to lock and a place to hold its terminal outcome, or return the row a
     * previous trigger already created for it.
     *
     * <p>
     * Repeating a trigger for one episode is normal rather than exceptional: a {@code decide} run and the
     * {@code confirm_close} run that follows it carry the same episode id. The unique key decides who wins a race,
     * and the loser rereads. The catch sits OUTSIDE the transaction that attempted the insert, because a constraint
     * violation marks its transaction rollback-only: catching it inside and carrying on would surface as an
     * {@code UnexpectedRollbackException} at commit rather than as the handled duplicate it is. The reread runs in a
     * second transaction for the same reason, and rethrows the original failure if it finds nothing, since not every
     * integrity violation is a duplicate key.
     *
     * @param userId     the struggling student
     * @param exerciseId the exercise the run belongs to
     * @param episodeId  the client-allocated episode id, already validated as usable
     * @return the registered episode, newly created or pre-existing
     */
    private IrisProactiveEpisode registerEpisode(long userId, long exerciseId, String episodeId) {
        try {
            return requiresNewTransactionTemplate.execute(status -> irisProactiveEpisodeRepository.find(userId, exerciseId, episodeId).orElseGet(() -> {
                var episode = new IrisProactiveEpisode();
                episode.setUserId(userId);
                episode.setExerciseId(exerciseId);
                episode.setEpisodeId(episodeId);
                episode.setCreatedAt(ZonedDateTime.now());
                // Carry over a terminal outcome this episode already reached before it had a registry row. A trigger
                // that reuses such an id across the deployment would otherwise get a fresh open row, and every later
                // check would trust it and let a late message through for an episode the student had closed.
                irisMessageRepository.findEpisodeOutcomes(episodeId, userId, exerciseId).stream().findFirst().ifPresent(episode::setOutcome);
                return irisProactiveEpisodeRepository.saveAndFlush(episode);
            }));
        }
        catch (DataIntegrityViolationException duplicate) {
            return requiresNewTransactionTemplate.execute(status -> irisProactiveEpisodeRepository.find(userId, exerciseId, episodeId).orElseThrow(() -> duplicate));
        }
    }

    boolean isEpisodeTerminal(String episodeId, long userId, long exerciseId) {
        var registered = irisProactiveEpisodeRepository.find(userId, exerciseId, episodeId);
        if (registered.isPresent()) {
            return registered.get().getOutcome() != null;
        }
        // Not registered: an episode from before this feature branch carried a registry, or a job still in flight
        // from a previous deployment. Fall back to the message rows, which is exactly what this method did before,
        // so such an episode keeps behaving as it always did instead of silently losing its terminal state.
        return !irisMessageRepository.findEpisodeOutcomes(episodeId, userId, exerciseId).isEmpty();
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
    public record PreparedTrigger(long courseId, long exerciseId, long userId, String variant, String supportLevel, String jobToken, @Nullable String intent,
            @Nullable StruggleEpisodeDTO episode, @Nullable String confirmReason, @Nullable String requestToken, @Nullable String proactivityMode) {
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
