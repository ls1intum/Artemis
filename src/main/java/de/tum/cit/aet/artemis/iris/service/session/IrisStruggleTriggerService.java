package de.tum.cit.aet.artemis.iris.service.session;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.account.service.UserAiPreferenceService;
import de.tum.cit.aet.artemis.core.exception.RateLimitExceededException;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.iris.config.IrisEnabled;
import de.tum.cit.aet.artemis.iris.domain.session.IrisChatMode;
import de.tum.cit.aet.artemis.iris.dto.StruggleEpisodeDTO;
import de.tum.cit.aet.artemis.iris.dto.StruggleInterventionEventDTO;
import de.tum.cit.aet.artemis.iris.repository.IrisChatSessionRepository;
import de.tum.cit.aet.artemis.iris.service.IrisRateLimitService;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisDTOService;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisJobService;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisPipelineService;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisCourseDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.struggle.PyrisStruggleSignalDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.job.StruggleInterventionJob;
import de.tum.cit.aet.artemis.iris.service.settings.IrisSettingsService;
import de.tum.cit.aet.artemis.iris.service.websocket.IrisChatWebsocketService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;

/**
 * The trigger phase of the proactive struggle intervention: authorize the student, apply the course
 * gate, reserve the single-flight slot, register the episode, and then hand the run to Pyris off the request thread.
 *
 * <p>
 * Deliberately separate from {@link IrisStruggleInterventionService}, which owns the other end of the run: what
 * happens when Pyris calls back. The two phases meet only in the job map and in {@link IrisProactiveEpisodeService},
 * and keeping them apart is what stops one class from holding both the Pyris dispatch and the chat persistence
 * dependencies.
 *
 * <p>
 * {@link #emitTerminalCompletion} lives here rather than with the callback handling because both of this class'
 * post-202 failure paths need it: once the endpoint has answered 202, the client waits for a terminal frame that no
 * callback will deliver, so a dispatch failure or a revoked consent has to send it instead.
 */
@Lazy
@Service
@Conditional(IrisEnabled.class)
public class IrisStruggleTriggerService {

    private static final Logger log = LoggerFactory.getLogger(IrisStruggleTriggerService.class);

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final AuthorizationCheckService authCheckService;

    private final IrisSettingsService irisSettingsService;

    private final IrisChatSessionRepository irisChatSessionRepository;

    private final PyrisDTOService pyrisDTOService;

    private final PyrisPipelineService pyrisPipelineService;

    private final PyrisJobService pyrisJobService;

    private final UserRepository userRepository;

    private final IrisChatSessionService irisChatSessionService;

    private final IrisChatWebsocketService irisChatWebsocketService;

    private final UserAiPreferenceService userAiPreferenceService;

    private final IrisProactiveEpisodeService irisProactiveEpisodeService;

    private final IrisRateLimitService irisRateLimitService;

    public IrisStruggleTriggerService(ProgrammingExerciseRepository programmingExerciseRepository, AuthorizationCheckService authCheckService,
            IrisSettingsService irisSettingsService, IrisChatSessionRepository irisChatSessionRepository, PyrisDTOService pyrisDTOService,
            PyrisPipelineService pyrisPipelineService, PyrisJobService pyrisJobService, UserRepository userRepository, IrisChatSessionService irisChatSessionService,
            IrisChatWebsocketService irisChatWebsocketService, UserAiPreferenceService userAiPreferenceService, IrisProactiveEpisodeService irisProactiveEpisodeService,
            IrisRateLimitService irisRateLimitService) {
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.authCheckService = authCheckService;
        this.irisSettingsService = irisSettingsService;
        this.irisChatSessionRepository = irisChatSessionRepository;
        this.pyrisDTOService = pyrisDTOService;
        this.pyrisPipelineService = pyrisPipelineService;
        this.pyrisJobService = pyrisJobService;
        this.userRepository = userRepository;
        this.irisChatSessionService = irisChatSessionService;
        this.irisChatWebsocketService = irisChatWebsocketService;
        this.userAiPreferenceService = userAiPreferenceService;
        this.irisProactiveEpisodeService = irisProactiveEpisodeService;
        this.irisRateLimitService = irisRateLimitService;
    }

    /**
     * Trigger a proactive struggle intervention. Returns a typed outcome: accepted (with job token), or rejected
     * carrying whether proactive help is unavailable for this exercise or a run is already in flight for this
     * {@code (user, exercise)}. A spent Iris budget and an active admission cooldown do not appear here at all;
     * they throw, and the endpoint answers 429. The sync part runs on the request thread; only the heavy DTO build
     * and POST are off-thread.
     *
     * @param exerciseId       the programming exercise id
     * @param signal           the struggle signal from the client engine
     * @param uncommittedFiles the student's live (uncommitted) working copy, merged on top of the latest submission
     * @param intent           the slot intent ({@code decide} | {@code confirm_close} | {@code help_request})
     * @param episode          the client-allocated episode block (null when not sent by an older client)
     * @param confirmReason    the close-mode discriminator (null unless intent is {@code confirm_close})
     * @param requestToken     the scoped-cancel identity; null on older clients
     * @param proactivityMode  the presence level ({@code pull} | {@code push}); enforces Pull in the callback
     * @param user             the requesting student
     * @return the trigger outcome (accepted + job token, or rejected with the unavailability flag for the 202)
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
            //
            // The admission charge deliberately stays: what reaches here is anything sendToPyris threw, and once the
            // POST has been issued a failure on the way back cannot be told from one on the way out. Keeping the
            // charge is the same call the connector-failure path makes, and it errs towards charging for work that
            // may really have happened.
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
     * Undo an admission whose run provably never reached Pyris.
     *
     * <p>
     * The in-flight marker goes first: it is shared by every intent and it is what a concurrent trigger reads to
     * decide it may wait for someone else's terminal frame, so the shorter it lingers for a run that is not
     * happening, the smaller that window is. The charge follows, keyed per intent and read by nobody else.
     *
     * <p>
     * Each step is attempted whatever the other did, and its own failure is recorded rather than thrown: the caller
     * is unwinding an error that matters more, and a failed cleanup costs this student one cooldown window at worst.
     *
     * @param jobToken      the reserving job token
     * @param cooldownToken the token that paid the admission charge
     * @param userId        the struggling student
     * @param exerciseId    the exercise the student is struggling on
     * @param intent        the slot intent, canonicalised into the cooldown key
     * @param carrier       the failure being unwound, which collects any cleanup failure as a suppressed cause, or
     *                          {@code null} on a deliberate bail, where a cleanup failure is logged instead
     */
    private void undoAdmission(String jobToken, String cooldownToken, long userId, long exerciseId, @Nullable String intent, @Nullable RuntimeException carrier) {
        try {
            pyrisJobService.releaseStruggleInFlightJob(jobToken, userId, exerciseId);
        }
        catch (RuntimeException releaseFailure) {
            record(carrier, releaseFailure, "release the struggle in-flight slot", userId, exerciseId);
        }
        refundCooldown(cooldownToken, userId, exerciseId, intent, carrier);
    }

    /**
     * Hand back an admission charge, recording rather than throwing its own failure. See
     * {@link #undoAdmission(String, String, long, long, String, RuntimeException)} for why cleanup failures do not
     * propagate.
     */
    private void refundCooldown(String cooldownToken, long userId, long exerciseId, @Nullable String intent, @Nullable RuntimeException carrier) {
        try {
            pyrisJobService.refundStruggleCooldown(cooldownToken, userId, exerciseId, intent);
        }
        catch (RuntimeException refundFailure) {
            record(carrier, refundFailure, "refund the struggle admission charge", userId, exerciseId);
        }
    }

    private void record(@Nullable RuntimeException carrier, RuntimeException failure, String what, long userId, long exerciseId) {
        if (carrier != null) {
            carrier.addSuppressed(failure);
        }
        else {
            log.warn("Could not {} for user {} exercise {}", what, userId, exerciseId, failure);
        }
    }

    /**
     * Synchronous core: deployment gate, light exercise load (id only), STUDENT-role gate, exam gate, then the
     * iris-enabled + proactive gate, then reserve the single-flight slot by minting the job. A SINGLE settings read
     * distinguishes a lasting unavailability from a transient in-flight skip, both of which reject.
     *
     * @param exerciseId      the programming exercise id
     * @param user            the requesting student
     * @param intent          the slot intent ({@code decide} | {@code confirm_close} | {@code help_request}); passed
     *                            through to the job so async callbacks can route by intent
     * @param episode         the client episode; the episodeId is stamped on the job for correlation
     * @param confirmReason   the close-mode discriminator; stamped on the job for close routing
     * @param requestToken    the scoped-cancel UUID; stamped on the job for cancel matching
     * @param proactivityMode the presence level ({@code pull} | {@code push}); stamped on the job and forwarded to Pyris for tone
     * @return a typed preparation: the reserved trigger, or a rejection tagged unavailable vs in-flight
     */
    public TriggerPreparation prepareTrigger(long exerciseId, User user, @Nullable String intent, @Nullable StruggleEpisodeDTO episode, @Nullable String confirmReason,
            @Nullable String requestToken, @Nullable String proactivityMode) {
        // Ahead of every lookup, and answered like the course-off case: for the client both mean "stop asking".
        if (!irisSettingsService.isGlobalStruggleEnabled()) {
            return TriggerPreparation.courseOff();
        }
        var exercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.STUDENT, exercise, user);
        // Iris serves no exam exercise, the same rule the two chat paths enforce. It has to be answered HERE, ahead
        // of the dispatch: the callback would reject the exam too, but only after the exercise, the submission and
        // the student's uncommitted files had already gone to Pyris. After the role check, so an outsider still
        // learns nothing beyond the 403 they already get.
        if (exercise.isExamExercise()) {
            return TriggerPreparation.courseOff();
        }
        var course = exercise.getCourseViaExerciseGroupOrCourseMember();
        var settings = irisSettingsService.getSettingsForCourse(course);
        if (!settings.enabled() || !settings.proactiveStruggleEffective()) {
            return TriggerPreparation.courseOff();
        }
        // The same per-user Iris budget every other Pyris-dispatching path checks, resolved against THIS course so a
        // course-level override applies here too. It sits ahead of the reservation on purpose: a rejection must not
        // leave an in-flight marker, a job entry or an episode row behind. Checked by course id rather than by
        // session because the struggle path has no session yet - one is materialized later, only if a message is
        // actually persisted.
        //
        // Deliberately not caught: the exception already carries 429, and an unaccepted 202 would tell the client to
        // await a frame from a run that does not exist. Every other Iris path lets it fly for the same reason.
        irisRateLimitService.checkRateLimitElseThrow(course.getId(), user);
        // The charge the Iris budget cannot make: that budget counts persisted messages, and a run ending silent,
        // ambient-unrevealed or in a quiet close persists none. One atomic putIfAbsent, so two triggers racing on
        // the same key cannot both be admitted.
        //
        // Ahead of the reservation, not after it: the in-flight marker is what a concurrent trigger reads to decide
        // it may wait for someone else's terminal frame, so it must only ever become visible for a run that is
        // going ahead. Charging first also means a reservation this method takes is never handed back for a reason
        // it could have known beforehand.
        var cooldownTokenOpt = pyrisJobService.chargeStruggleCooldown(user.getId(), exerciseId, intent);
        if (cooldownTokenOpt.isEmpty()) {
            log.info("Struggle intervention cooling down for user {} exercise {} intent {}, rejecting", user.getId(), exerciseId, intent);
            // 429 rather than an unaccepted 202, for the reason given at the budget check above.
            throw new RateLimitExceededException(pyrisJobService.getStruggleCooldownSeconds());
        }
        String cooldownToken = cooldownTokenOpt.get();
        String episodeId = episode != null ? episode.episodeId() : null;
        Optional<String> tokenOpt;
        try {
            tokenOpt = pyrisJobService.addStruggleInterventionJobIfNonePending(course.getId(), user.getId(), exerciseId, intent, episodeId, confirmReason, requestToken,
                    proactivityMode);
        }
        catch (RuntimeException exception) {
            // The reservation itself failed, so nothing was reserved and nothing will be dispatched. Without this
            // the charge would sit out its whole window for a run that never existed.
            refundCooldown(cooldownToken, user.getId(), exerciseId, intent, exception);
            throw exception;
        }
        if (tokenOpt.isEmpty()) {
            // Someone else's run is already going, so this trigger dispatches nothing and its charge is refunded.
            // The student is not spending a second slot here; the run they are waiting for paid for itself.
            refundCooldown(cooldownToken, user.getId(), exerciseId, intent, null);
            log.info("Struggle intervention already in flight for user {} exercise {}, skipping", user.getId(), exerciseId);
            return TriggerPreparation.inFlight();
        }
        String jobToken = tokenOpt.get();
        // Register the episode BEFORE the pipeline is dispatched. The caller fires Pyris off-thread only after this
        // method returns, so no callback can arrive before the row exists, and every later path can therefore lock a
        // row that is already there. Registering after the reservation rather than before it keeps rejected and
        // in-flight triggers from leaving rows behind; a failure here undoes the admission, so nothing is leaked.
        String registrableEpisodeId = StruggleEpisodeDTO.usableEpisodeId(episodeId);
        if (registrableEpisodeId != null) {
            try {
                irisProactiveEpisodeService.registerEpisode(user.getId(), exerciseId, registrableEpisodeId);
            }
            catch (RuntimeException exception) {
                undoAdmission(jobToken, cooldownToken, user.getId(), exerciseId, intent, exception);
                throw exception;
            }
        }
        return TriggerPreparation.triggered(new PreparedTrigger(course.getId(), exerciseId, user.getId(), settings.variant().jsonValue(), settings.supportLevel().jsonValue(),
                jobToken, cooldownToken, intent, episode, confirmReason, requestToken, proactivityMode));
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
     * Takes a snapshot that {@link #prepareTrigger} admitted, and relies on it: the gates that decide whether this
     * exercise may reach Pyris at all, the exam gate among them, are enforced there and not repeated here.
     *
     * <p>
     * The final dispatch is serialized against a scoped cancel by the job lock; see the comment at that call. The
     * opt-in bail-out above deliberately stays outside it: a cancel that won the race already removed the job, so
     * {@code getJob} returns nothing, no completion frame is emitted, and none is owed - the client stopped waiting
     * when it cancelled. Its cleanup is token-conditional throughout and can therefore not touch a newer run.
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
            // Bailed before any egress, so the run costs nothing upstream and the whole admission is undone.
            undoAdmission(p.jobToken(), p.cooldownToken(), p.userId(), p.exerciseId(), p.intent(), null);
            return;
        }
        var exercise = programmingExerciseRepository.findByIdElseThrow(p.exerciseId());
        var exerciseDTO = pyrisDTOService.toPyrisProgrammingExerciseMetadataDTO(exercise);
        var submissionDTO = latestSubmission(exercise, user).map(s -> pyrisDTOService.toPyrisSubmissionDTO(s, uncommittedFiles)).orElse(null);
        var courseDTO = new PyrisCourseDTO(exercise.getCourseViaExerciseGroupOrCourseMember());
        var chatHistory = irisChatSessionRepository
                .findLatestByEntityIdAndChatModeAndUserIdWithMessages(p.exerciseId(), IrisChatMode.PROGRAMMING_EXERCISE_CHAT, p.userId(), Pageable.ofSize(1)).stream().findFirst()
                .map(s -> pyrisDTOService.toPyrisMessageDTOListForStruggle(s.getMessages())).orElse(List.of());
        // The dispatch itself runs under the job lock, on a job re-read inside it. Everything above only reads: the
        // student can cancel this very request between the 202 and this line, and scoped cancel removes the job and
        // frees the (user, exercise) slot under that same lock. Without the lock a cancelled run would still POST the
        // student's code and chat history, and the freed slot would let a second run start alongside it. Under it,
        // cancel either completes before the re-read (which then finds no job and sends nothing) or waits until the
        // request has gone out. The lock therefore also covers what executePipeline does around the POST, including
        // the connector-failure cleanup that releases the slot, so cancel never observes a half-torn-down dispatch.
        pyrisJobService.runWithJobLock(p.jobToken(), () -> {
            if (!(pyrisJobService.getJob(p.jobToken()) instanceof StruggleInterventionJob)) {
                log.info("Struggle intervention for user {} exercise {} was cancelled before dispatch, sending nothing", p.userId(), p.exerciseId());
                return null;
            }
            pyrisPipelineService.executeStruggleInterventionPipeline(p.variant(), p.supportLevel(), p.jobToken(), user, signal, exerciseDTO, submissionDTO, courseDTO, chatHistory,
                    p.exerciseId(), p.intent(), p.episode(), p.proactivityMode());
            return null;
        });
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
            irisChatWebsocketService.sendStruggleEvent(user, StruggleInterventionEventDTO.terminalCompletion(job.intent(), job.exerciseId(), job.episodeId()));
        }
        catch (Exception e) {
            // Never let the completion frame break the caller's cleanup: the marker release in the finally block
            // matters more than the notification, and a missing frame degrades to the client's own timeout.
            log.warn("Could not emit terminal completion for struggle job {} exercise {} user {}", job.jobId(), job.exerciseId(), job.userId(), e);
        }
    }

    /**
     * Enforce that {@code user} holds at least the STUDENT role for the given exercise. Binds the {@code exerciseId}
     * path variable of the episode-outcome endpoint to a real authorization check, closing the IDOR where any
     * authenticated student could record (or probe) an outcome for an episode in an exercise they are not enrolled in.
     * This is a pure authorization gate: it does NOT touch the LLM opt-in (recording a reaction to an already
     * delivered hint must never be rejected on opt-in), only course/exercise membership.
     *
     * @param exerciseId the programming exercise id from the request path
     * @param user       the requesting user (must carry groups + authorities)
     */
    public void checkAtLeastStudentForExercise(long exerciseId, User user) {
        var exercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.STUDENT, exercise, user);
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
     * Immutable snapshot of the synchronously-prepared trigger (ids + payload only, NO entity crosses threads).
     * Every field is an id or an immutable value object, so the snapshot is safe to hand to the async dispatch.
     */
    public record PreparedTrigger(long courseId, long exerciseId, long userId, String variant, String supportLevel, String jobToken, String cooldownToken, @Nullable String intent,
            @Nullable StruggleEpisodeDTO episode, @Nullable String confirmReason, @Nullable String requestToken, @Nullable String proactivityMode) {
    }

    /**
     * Why a trigger was (not) prepared, from a SINGLE settings read: a reserved trigger, or a rejection that is either
     * lasting (Iris or proactive off for the course, or an exercise Iris does not serve, currently an exam exercise)
     * or a run already in flight for this {@code (user, exercise)}. Distinguishing the two lets the 202 carry an exact
     * {@code courseDisabled} so a slow in-flight job is never mis-read as "stop asking". The budget and cooldown rejections are not in here: they answer
     * 429, because neither leaves a run behind that could deliver the frame an unaccepted 202 makes the client await.
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

        // The only unaccepted 202 that is not a course-off: a run is already going and owes the client its terminal
        // frame, which is exactly what the client then waits for. Rejections with no run behind them answer 429.
        static TriggerPreparation inFlight() {
            return new TriggerPreparation(null, false);
        }
    }

    /** Outcome surfaced to the REST layer: accepted (with job token) or rejected, unavailability carried for the 202. */
    public record StruggleTriggerOutcome(boolean accepted, boolean courseDisabled, @Nullable String jobToken) {
    }
}
