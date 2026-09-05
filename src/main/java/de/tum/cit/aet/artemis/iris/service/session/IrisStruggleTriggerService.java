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
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.iris.config.IrisEnabled;
import de.tum.cit.aet.artemis.iris.domain.session.IrisChatMode;
import de.tum.cit.aet.artemis.iris.dto.StruggleEpisodeDTO;
import de.tum.cit.aet.artemis.iris.dto.StruggleInterventionEventDTO;
import de.tum.cit.aet.artemis.iris.repository.IrisChatSessionRepository;
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
 * The trigger phase of the proactive struggle intervention (spec §5.2): authorize the student, apply the course
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

    public IrisStruggleTriggerService(ProgrammingExerciseRepository programmingExerciseRepository, AuthorizationCheckService authCheckService,
            IrisSettingsService irisSettingsService, IrisChatSessionRepository irisChatSessionRepository, PyrisDTOService pyrisDTOService,
            PyrisPipelineService pyrisPipelineService, PyrisJobService pyrisJobService, UserRepository userRepository, IrisChatSessionService irisChatSessionService,
            IrisChatWebsocketService irisChatWebsocketService, UserAiPreferenceService userAiPreferenceService, IrisProactiveEpisodeService irisProactiveEpisodeService) {
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
        String registrableEpisodeId = StruggleEpisodeDTO.usableEpisodeId(episodeId);
        if (registrableEpisodeId != null) {
            try {
                irisProactiveEpisodeService.registerEpisode(user.getId(), exerciseId, registrableEpisodeId);
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
}
