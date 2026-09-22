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
 * The trigger phase of the proactive struggle intervention: authorize the student, apply the course gate, reserve
 * the single-flight slot, register the episode, and hand the run to Pyris off the request thread. The other end of
 * the run belongs to {@link IrisStruggleInterventionService}, and the two phases meet only in the job map and in
 * {@link IrisProactiveEpisodeService}.
 *
 * <p>
 * {@link #emitTerminalCompletion} lives here because both of this class' post-202 failure paths need it: once the
 * endpoint has answered 202 the client waits for a terminal frame that no callback will deliver.
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
     * Trigger a proactive struggle intervention. Returns accepted with a job token, or rejected carrying whether
     * proactive help is unavailable for this exercise or a run is already in flight. A spent Iris budget and an
     * active admission cooldown throw instead, and the endpoint answers 429. Only the heavy DTO build and the POST
     * run off the request thread.
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
            // The 202 is already out, so notify before releasing and the slot is still ours while the frame goes
            // out. The admission charge stays: once the POST has been issued a failure on the way back cannot be
            // told from one on the way out. The release sits in a finally because nothing observes this handler, so
            // a throw above would leave the slot reserved for the whole job timeout.
            try {
                var reserved = pyrisJobService.getJob(p.jobToken());
                if (reserved instanceof StruggleInterventionJob struggleJob) {
                    emitTerminalCompletion(struggleJob);
                }
            }
            finally {
                pyrisJobService.releaseStruggleInFlightJob(p.jobToken(), p.userId(), p.exerciseId());
            }
            return null;
        });
        return new StruggleTriggerOutcome(true, false, p.jobToken());
    }

    // Undo an admission whose run provably never reached Pyris. The in-flight marker goes first, because it is what a concurrent trigger
    // reads to decide it may wait for someone else's terminal frame. Each step is attempted whatever the other did and records rather
    // than throws its own failure, since the caller is unwinding an error that matters more. null on a deliberate bail, where a cleanup
    // failure is logged instead
    private void undoAdmission(String jobToken, String cooldownToken, long userId, long exerciseId, @Nullable String intent, @Nullable RuntimeException carrier) {
        try {
            pyrisJobService.releaseStruggleInFlightJob(jobToken, userId, exerciseId);
        }
        catch (RuntimeException releaseFailure) {
            record(carrier, releaseFailure, "release the struggle in-flight slot", userId, exerciseId);
        }
        refundCooldown(cooldownToken, userId, exerciseId, intent, carrier);
    }

    // Hand back an admission charge, recording rather than throwing its own failure. See #undoAdmission(String, String, long, long,
    // String, RuntimeException) for why cleanup failures do not propagate.
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
     * Synchronous core: deployment gate, light exercise load, role gate, exam gate, the iris-enabled and proactive
     * gate, then reserve the single-flight slot by minting the job. A single settings read distinguishes a lasting
     * unavailability from a transient in-flight skip, both of which reject.
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
        // Iris serves no exam exercise. Answered here rather than in the callback, which would reject it only after
        // the student's uncommitted files had already gone to Pyris. After the role check, so an outsider learns
        // nothing beyond the 403 they already get.
        if (exercise.isExamExercise()) {
            return TriggerPreparation.courseOff();
        }
        var course = exercise.getCourseViaExerciseGroupOrCourseMember();
        var settings = irisSettingsService.getSettingsForCourse(course);
        if (!settings.enabled() || !settings.proactiveStruggleEffective()) {
            return TriggerPreparation.courseOff();
        }
        // The same per-user Iris budget every other Pyris-dispatching path checks, resolved against this course.
        // Ahead of the reservation, so a rejection leaves no marker, job entry or episode row behind. By course id
        // rather than by session, because the struggle path has no session yet. Not caught: the exception carries
        // 429, and an unaccepted 202 would tell the client to await a frame from a run that does not exist.
        irisRateLimitService.checkRateLimitElseThrow(course.getId(), user);
        // The charge the Iris budget cannot make: that budget counts persisted messages, and a run ending silent,
        // ambient-unrevealed or in a quiet close persists none. One atomic putIfAbsent, so two triggers racing on
        // the same key cannot both be admitted. Ahead of the reservation, so the marker only becomes visible for a
        // run that is going ahead.
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
            // Nothing was reserved and nothing will be dispatched, so the charge must not sit out its window.
            refundCooldown(cooldownToken, user.getId(), exerciseId, intent, exception);
            throw exception;
        }
        if (tokenOpt.isEmpty()) {
            // Someone else's run is already going, so this trigger dispatches nothing and its charge is refunded.
            refundCooldown(cooldownToken, user.getId(), exerciseId, intent, null);
            log.info("Struggle intervention already in flight for user {} exercise {}, skipping", user.getId(), exerciseId);
            return TriggerPreparation.inFlight();
        }
        String jobToken = tokenOpt.get();
        // Register before the dispatch, so no callback can arrive before the row exists and every later path can
        // lock a row that is already there. After the reservation, so rejected and in-flight triggers leave no rows.
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
     * Heavy off-thread work: re-load everything by id, build the data DTOs, fire and forget to Pyris.
     *
     * <p>
     * Runs off the request thread with no surrounding {@code @Transactional} or open Hibernate session, mirroring
     * {@code IrisChatPipelineExecutionService.execute}. It is lazy-init safe because every load uses a fetch-join
     * query covering exactly what the DTO conversion touches, and it captures only ids and the immutable payload. Do
     * not wrap it in {@code @Transactional} (a self-invoked call would not be proxied anyway) or pass a
     * request-thread entity across the boundary. The gates that decide whether this exercise may reach Pyris at all
     * are enforced in {@link #prepareTrigger} and not repeated here.
     *
     * <p>
     * The final dispatch is serialized against a scoped cancel by the job lock. The opt-in bail-out stays outside
     * it: a cancel that won the race already removed the job, so no completion frame is owed.
     *
     * @param p                the immutable trigger snapshot (ids + payload)
     * @param signal           the struggle signal from the client engine
     * @param uncommittedFiles the student's live (uncommitted) working copy
     */
    public void sendToPyris(PreparedTrigger p, PyrisStruggleSignalDTO signal, Map<String, String> uncommittedFiles) {
        var user = userRepository.findByIdElseThrow(p.userId());
        // The student may have revoked their opt-in between the 202 and now, so bail before any egress to Pyris.
        if (!userAiPreferenceService.hasOptedIntoLlmUsage(user.getId())) {
            log.info("Struggle intervention skipped: user {} is no longer opted into LLM usage", p.userId());
            // Emit the completion before releasing, the same order as the dispatch-failure path above.
            if (pyrisJobService.getJob(p.jobToken()) instanceof StruggleInterventionJob struggleJob) {
                emitTerminalCompletion(struggleJob);
            }
            // Bailed before any egress, so the whole admission is undone.
            undoAdmission(p.jobToken(), p.cooldownToken(), p.userId(), p.exerciseId(), p.intent(), null);
            return;
        }
        var exercise = programmingExerciseRepository.findByIdElseThrow(p.exerciseId());
        var exerciseDTO = pyrisDTOService.toPyrisProgrammingExerciseMetadataDTO(exercise);
        // Normalized, not raw: an id the endpoint would refuse must not decide which history hints count as
        // belonging to another episode.
        String currentEpisodeId = p.episode() == null ? null : StruggleEpisodeDTO.usableEpisodeId(p.episode().episodeId());
        var submissionDTO = latestSubmission(exercise, user).map(s -> pyrisDTOService.toPyrisSubmissionDTO(s, uncommittedFiles)).orElse(null);
        var courseDTO = new PyrisCourseDTO(exercise.getCourseViaExerciseGroupOrCourseMember());
        var chatHistory = irisChatSessionRepository
                .findLatestByEntityIdAndChatModeAndUserIdWithMessages(p.exerciseId(), IrisChatMode.PROGRAMMING_EXERCISE_CHAT, p.userId(), Pageable.ofSize(1)).stream().findFirst()
                .map(s -> pyrisDTOService.toPyrisMessageDTOListForStruggle(s.getMessages(), currentEpisodeId)).orElse(List.of());
        // The dispatch runs under the job lock, on a job re-read inside it. Without the lock a cancelled run would
        // still POST the student's code and chat history, and the freed slot would let a second run start alongside
        // it. Under it, cancel either completes before the re-read, which then finds no job, or waits until the
        // request has gone out. The lock also covers the connector-failure cleanup that releases the slot.
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
     * Emit the terminal completion frame for a run that ended without a decision. Without it, a Pyris
     * {@code FAILED} run or a post-202 dispatch failure leaves the client waiting until its own timeout. The frame
     * shape follows the intent: {@code decide} completes as {@code action="silent"}, {@code confirm_close} as
     * {@code resolved=false}, because a failed close must not read as resolved.
     *
     * @param job the struggle-intervention job whose run ended without a decision
     */
    public void emitTerminalCompletion(StruggleInterventionJob job) {
        try {
            var user = userRepository.findByIdElseThrow(job.userId());
            irisChatWebsocketService.sendStruggleEvent(user, StruggleInterventionEventDTO.terminalCompletion(job.intent(), job.exerciseId(), job.episodeId()));
        }
        catch (Exception e) {
            // The marker release matters more than the frame, and a missing frame degrades to a client timeout.
            log.warn("Could not emit terminal completion for struggle job {} exercise {} user {}", job.jobId(), job.exerciseId(), job.userId(), e);
        }
    }

    /**
     * Enforce that {@code user} holds at least the STUDENT role for the given exercise, binding the
     * {@code exerciseId} path variable of the episode-outcome endpoint to a real authorization check. A pure
     * authorization gate: it does not touch the LLM opt-in, because recording a reaction to an already delivered
     * hint must never be rejected on opt-in.
     *
     * @param exerciseId the programming exercise id from the request path
     * @param user       the requesting user (must carry groups + authorities)
     */
    public void checkAtLeastStudentForExercise(long exerciseId, User user) {
        var exercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.STUDENT, exercise, user);
    }

    /**
     * Scoped cancel: remove the pending struggle job only if its stamped {@code requestToken} matches, then release
     * the single-flight marker. A non-matching token or no pending job is an idempotent noop, which is what stops
     * {@code cancel(A)} from removing a since-started run B.
     *
     * @param user         the requesting student (scopes the in-flight slot to this user)
     * @param exerciseId   the exercise id (scopes the in-flight slot)
     * @param requestToken the token that must match the pending job's stamped token
     */
    public void cancelOutstandingStruggleJob(User user, long exerciseId, String requestToken) {
        pyrisJobService.removeStruggleJobIfTokenMatches(user.getId(), exerciseId, requestToken);
    }

    // Latest submission for (exercise, user), the same resolution the chat pipeline uses. Returns empty only when the student genuinely
    // has no submission yet, and then no live code is shipped.
    private Optional<ProgrammingSubmission> latestSubmission(ProgrammingExercise exercise, User user) {
        return irisChatSessionService.getLatestSubmissionIfExists(exercise, user);
    }

    /** Ids and immutable values only, so no entity crosses threads and the snapshot is safe for the async dispatch. */
    public record PreparedTrigger(long courseId, long exerciseId, long userId, String variant, String supportLevel, String jobToken, String cooldownToken, @Nullable String intent,
            @Nullable StruggleEpisodeDTO episode, @Nullable String confirmReason, @Nullable String requestToken, @Nullable String proactivityMode) {
    }

    /**
     * Why a trigger was or was not prepared, from a single settings read: a reserved trigger, a lasting rejection, or
     * a run already in flight. Distinguishing the two lets the 202 carry an exact {@code courseDisabled}, so a slow
     * in-flight job is never mis-read as "stop asking". Budget and cooldown rejections answer 429 instead, because
     * neither leaves a run behind that could deliver the frame an unaccepted 202 makes the client await.
     */
    public record TriggerPreparation(@Nullable PreparedTrigger trigger, boolean courseDisabled) {

        public boolean accepted() {
            return trigger != null;
        }

        static TriggerPreparation triggered(PreparedTrigger trigger) {
            return new TriggerPreparation(trigger, false);
        }

        // Named courseOff() to avoid clashing with the generated courseDisabled() accessor.
        static TriggerPreparation courseOff() {
            return new TriggerPreparation(null, true);
        }

        // The only unaccepted 202 that is not a course-off: a run is already going and owes the client its frame.
        static TriggerPreparation inFlight() {
            return new TriggerPreparation(null, false);
        }
    }

    /** Outcome surfaced to the REST layer: accepted (with job token) or rejected, unavailability carried for the 202. */
    public record StruggleTriggerOutcome(boolean accepted, boolean courseDisabled, @Nullable String jobToken) {
    }
}
