package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

import java.io.Serial;
import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import jakarta.annotation.PostConstruct;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import com.hazelcast.topic.ITopic;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.admin.domain.LLMServiceType;
import de.tum.cit.aet.artemis.admin.service.LLMTokenUsageService;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.core.exception.ServiceUnavailableAlertException;
import de.tum.cit.aet.artemis.hyperion.config.HyperionExerciseGenerationEnabled;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationEventDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationFileChangeDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationStateDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationStatusDTO;
import de.tum.cit.aet.artemis.hyperion.dto.GenerationMode;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

/**
 * Single-flight slot and cancellation registry for whole-exercise agentic generation jobs.
 * <p>
 * At most one generation runs per exercise at a time (claimed atomically in a Hazelcast map), and a separate cancellation set lets the REST layer request a cooperative abort
 * that the running loop polls between turns. This per-exercise single-flight is the primary concurrency bound.
 */
@Service
@Lazy
@Conditional(HyperionExerciseGenerationEnabled.class)
public class GenerationJobService {

    private static final Logger log = LoggerFactory.getLogger(GenerationJobService.class);

    private static final String JOB_MAP_NAME = "hyperion-exercise-generation-jobs";

    private static final String CANCEL_MAP_NAME = "hyperion-exercise-generation-cancellations";

    private static final String CANCEL_TOPIC_NAME = "hyperion-exercise-generation-cancel-requests";

    private static final String ENTITY_NAME = "hyperionExerciseGeneration";

    private static final String REVERT_JOB_PREFIX = "revert-";

    private static final String EXTERNAL_MUTATION_JOB_PREFIX = "external-mutation-";

    static final String GENERATION_PIPELINE_ID = "HYPERION_EXERCISE_GENERATION";

    private static final String USER_CANCELLATION_MESSAGE = "Generation was cancelled. Nothing was changed.";

    private static final String SYSTEM_CANCELLATION_MESSAGE = "Generation was cancelled by an administrator. Nothing was changed.";

    private final HazelcastInstance hazelcastInstance;

    // Run launched via an event so this service does not depend on the task service, which would close a construction cycle.
    private final ApplicationEventPublisher eventPublisher;

    private final LLMTokenUsageService llmTokenUsageService;

    private final HyperionGenerationBudgetService generationBudgetService;

    private final Duration staleJobTimeout;

    private final Duration maxJobDuration;

    private final Executor cancellationExecutor;

    private final int expectedDataMemberCount;

    private String localNodeId;

    private IMap<String, JobInfo> jobMap;

    private IMap<String, Boolean> cancellationMap;

    private GenerationJobReplayStore replayStore;

    // Node-local interrupts held in-process because the hook closes over a live sandbox reference that exists only on the node running the job; other nodes rely on the Hazelcast
    // flag.
    private final ConcurrentMap<String, Runnable> cancelHooks = new ConcurrentHashMap<>();

    @Autowired
    public GenerationJobService(@Qualifier("hazelcastInstance") HazelcastInstance hazelcastInstance, ApplicationEventPublisher eventPublisher,
            LLMTokenUsageService llmTokenUsageService, HyperionGenerationBudgetService generationBudgetService,
            @Value("${artemis.hyperion.agent.stale-job-timeout:PT35M}") Duration staleJobTimeout,
            @Value("${artemis.hyperion.agent.max-job-duration:PT30M}") Duration maxJobDuration, @Qualifier("taskExecutor") Executor cancellationExecutor,
            @Value("${jhipster.cache.hazelcast.expected-data-member-count:1}") int expectedDataMemberCount) {
        this.hazelcastInstance = hazelcastInstance;
        this.eventPublisher = eventPublisher;
        this.llmTokenUsageService = llmTokenUsageService;
        this.generationBudgetService = generationBudgetService;
        this.staleJobTimeout = staleJobTimeout;
        this.maxJobDuration = maxJobDuration;
        this.cancellationExecutor = cancellationExecutor;
        this.expectedDataMemberCount = expectedDataMemberCount;
    }

    public GenerationJobService(HazelcastInstance hazelcastInstance, ApplicationEventPublisher eventPublisher, LLMTokenUsageService llmTokenUsageService,
            @Nullable HyperionGenerationBudgetService generationBudgetService, Duration staleJobTimeout, Duration maxJobDuration, Executor cancellationExecutor) {
        this(hazelcastInstance, eventPublisher, llmTokenUsageService, generationBudgetService, staleJobTimeout, maxJobDuration, cancellationExecutor, 1);
    }

    GenerationJobService(HazelcastInstance hazelcastInstance, ApplicationEventPublisher eventPublisher, LLMTokenUsageService llmTokenUsageService) {
        this(hazelcastInstance, eventPublisher, llmTokenUsageService, null, Duration.ofMinutes(35), Duration.ofMinutes(30), Runnable::run);
    }

    GenerationJobService(HazelcastInstance hazelcastInstance, ApplicationEventPublisher eventPublisher, LLMTokenUsageService llmTokenUsageService, Duration staleJobTimeout,
            Duration maxJobDuration) {
        this(hazelcastInstance, eventPublisher, llmTokenUsageService, null, staleJobTimeout, maxJobDuration, Runnable::run);
    }

    GenerationJobService(HazelcastInstance hazelcastInstance, ApplicationEventPublisher eventPublisher, LLMTokenUsageService llmTokenUsageService,
            @Nullable HyperionGenerationBudgetService generationBudgetService, Duration staleJobTimeout, Duration maxJobDuration) {
        this(hazelcastInstance, eventPublisher, llmTokenUsageService, generationBudgetService, staleJobTimeout, maxJobDuration, Runnable::run);
    }

    /**
     * The token-usage sink for a generation run's model calls: each {@link ChatResponse} is recorded against the run's course/exercise/user. It lives here — with the rest of the
     * run's bookkeeping — so the orchestrator and the independent examiner attribute their model calls through one shared path.
     *
     * @param courseId   the run's course id, or {@code null} if unavailable
     * @param exerciseId the run's exercise id, or {@code null} if unavailable
     * @param userId     the initiating user's id, or {@code null} if unavailable
     * @return a sink that records each model response's token usage
     */
    public Consumer<ChatResponse> tokenUsageSink(@Nullable Long courseId, @Nullable Long exerciseId, @Nullable Long userId) {
        return chatResponse -> {
            boolean recorded = llmTokenUsageService.trackChatResponseTokenUsage(chatResponse, LLMServiceType.HYPERION, GENERATION_PIPELINE_ID,
                    builder -> builder.withCourse(courseId).withExercise(exerciseId).withUser(userId));
            if (!recorded) {
                throw new TokenUsageAccountingException();
            }
        };
    }

    static final class TokenUsageAccountingException extends RuntimeException {

        @Serial
        private static final long serialVersionUID = 1L;
    }

    /** Initializes the distributed job state and local cancellation listener. */
    @PostConstruct
    public void init() {
        if (expectedDataMemberCount < 1) {
            throw new IllegalArgumentException("jhipster.cache.hazelcast.expected-data-member-count must be at least 1");
        }
        if (maxJobDuration == null || maxJobDuration.isZero() || maxJobDuration.isNegative()) {
            throw new IllegalArgumentException("artemis.hyperion.agent.max-job-duration must be positive");
        }
        if (staleJobTimeout == null || staleJobTimeout.compareTo(maxJobDuration) <= 0) {
            throw new IllegalArgumentException("artemis.hyperion.agent.stale-job-timeout must be greater than max-job-duration");
        }
        jobMap = hazelcastInstance.getMap(JOB_MAP_NAME);
        cancellationMap = hazelcastInstance.getMap(CANCEL_MAP_NAME);
        replayStore = new GenerationJobReplayStore(hazelcastInstance);
        ITopic<CancelRequest> cancelTopic = hazelcastInstance.getTopic(CANCEL_TOPIC_NAME);
        cancelTopic.addMessageListener(message -> runLocalCancelHook(message.getMessageObject().jobId()));
        localNodeId = hazelcastInstance.getCluster().getLocalMember().getUuid().toString();
    }

    public String startJob(User user, ProgrammingExercise exercise, String userPrompt, GenerationMode mode) {
        return startJob(user, exercise, userPrompt, mode, null);
    }

    public String startJob(User user, ProgrammingExercise exercise, String userPrompt, GenerationMode mode, @Nullable String budgetReservationId) {
        return startJob(user, exercise, userPrompt, mode, budgetReservationId, null);
    }

    /**
     * Starts a job while preserving the original instructor brief separately from the rendered authoring instruction.
     *
     * @param user                the requesting instructor
     * @param exercise            the target exercise
     * @param userPrompt          the rendered instruction for the generation agent
     * @param mode                the explicit run intent
     * @param budgetReservationId the optional token-budget reservation id
     * @param sourceBrief         the authoritative instructor brief for a from-scratch generation, or {@code null} for a statement-driven run
     * @return the started job id
     */
    public String startJob(User user, ProgrammingExercise exercise, String userPrompt, GenerationMode mode, @Nullable String budgetReservationId, @Nullable String sourceBrief) {
        String jobId = UUID.randomUUID().toString();
        String key = key(exercise.getId());
        Instant startedAt = Instant.now();
        Instant deadlineAt = deadlineAt(startedAt);
        JobInfo newJob = new JobInfo(jobId, user.getLogin(), exercise.getId(), startedAt, deadlineAt, localNodeId, startedAt, true, budgetReservationId);
        claimSlot(key, newJob, "Exercise generation is already running for this exercise", "exerciseGenerationRunning");
        GenerationJobReplayStore.StartedReplay startedReplay = null;
        boolean publicStatePublished = false;
        // Fresh transcript and fileChange store for this run. Keep the previous replay state until the async event is accepted; if publishing fails synchronously, rollback
        // restores the prior terminal replay instead of leaving the status endpoint empty. State initialization is inside the rollback boundary as well: once the slot is claimed,
        // no distributed-map failure may leave it wedged.
        try {
            startedReplay = replayStore.initializeStart(exercise.getId(), jobId, user.getLogin(), mode);
            publishExerciseState(exercise.getId(), jobId, true);
            publicStatePublished = true;
            eventPublisher.publishEvent(new GenerationStartedEvent(jobId, user, exercise, userPrompt, mode, exercise.getProblemStatement(), exercise.getTitle(), deadlineAt,
                    budgetReservationId, sourceBrief));
        }
        catch (RejectedExecutionException e) {
            // The generation executor is saturated (AbortPolicy), so the @Async listener never ran and no terminal event will ever fire. Roll the claimed slot and its retained
            // state back — value-guarded, so a later run for this exercise is never clobbered — rather than leave the exercise wedged as "running". This also catches
            // ThreadPoolTaskExecutor's TaskRejectedException, a RejectedExecutionException subclass.
            rollbackUnpublishedStart(exercise.getId(), key, newJob, startedReplay);
            if (publicStatePublished) {
                publishExerciseState(exercise.getId(), jobId, false);
            }
            log.warn("Exercise generation executor rejected job {} for exercise {}; released the slot", jobId, exercise.getId());
            throw new ServiceUnavailableAlertException("The system is currently busy with too many exercise generations. Please try again in a few minutes.", ENTITY_NAME,
                    "exerciseGenerationCapacityExceeded");
        }
        catch (RuntimeException e) {
            rollbackUnpublishedStart(exercise.getId(), key, newJob, startedReplay);
            if (publicStatePublished) {
                publishExerciseState(exercise.getId(), jobId, false);
            }
            throw e;
        }
        return jobId;
    }

    /**
     * Fails with the same conflict as {@link #startJob(User, ProgrammingExercise, String, GenerationMode, String)} when a live slot exists, but first reclaims an abandoned or
     * stale cancellable slot. The REST resource uses this before checking sandbox capacity so duplicate starts report the active job, not transient capacity exhaustion.
     *
     * @param exerciseId the exercise id whose slot should be checked
     */
    public void rejectIfActiveJobCannotBeReclaimed(long exerciseId) {
        String key = key(exerciseId);
        jobMap.lock(key);
        try {
            verifyExpectedDataMemberTopology();
            JobInfo existing = jobMap.get(key);
            if (existing == null) {
                return;
            }
            Instant now = Instant.now();
            if (shouldClearAsStale(existing, staleBefore(now))) {
                if (reclaimStaleJob(key, existing, now)) {
                    return;
                }
            }
            throw new ConflictException("Exercise generation is already running for this exercise", ENTITY_NAME, "exerciseGenerationRunning");
        }
        finally {
            jobMap.unlock(key);
        }
    }

    private void rollbackUnpublishedStart(long exerciseId, String key, JobInfo newJob, GenerationJobReplayStore.@Nullable StartedReplay startedReplay) {
        jobMap.lock(key);
        try {
            jobMap.remove(key, newJob);
            if (startedReplay != null) {
                replayStore.restoreUnpublishedStart(exerciseId, startedReplay);
            }
        }
        finally {
            jobMap.unlock(key);
        }
    }

    private void claimSlot(String key, JobInfo newJob, String conflictMessage, String errorKey) {
        jobMap.lock(key);
        try {
            verifyExpectedDataMemberTopology();
            JobInfo existing = jobMap.get(key);
            if (existing != null) {
                Instant now = Instant.now();
                if (shouldClearAsStale(existing, staleBefore(now))) {
                    if (!reclaimStaleJob(key, existing, now)) {
                        throw new ConflictException(conflictMessage, ENTITY_NAME, errorKey);
                    }
                }
                else {
                    throw new ConflictException(conflictMessage, ENTITY_NAME, errorKey);
                }
            }
            jobMap.set(key, newJob);
        }
        finally {
            jobMap.unlock(key);
        }
    }

    private void verifyExpectedDataMemberTopology() {
        final long actualDataMemberCount;
        try {
            actualDataMemberCount = hazelcastInstance.getCluster().getMembers().stream().filter(member -> !member.isLiteMember()).count();
        }
        catch (RuntimeException e) {
            throw new ServiceUnavailableAlertException(
                    "Hyperion coordination cannot verify the Hazelcast data-member topology. Check jhipster.cache.hazelcast.expected-data-member-count.", ENTITY_NAME,
                    "hyperionDataMemberTopologyUnavailable");
        }
        if (actualDataMemberCount != expectedDataMemberCount) {
            throw new ServiceUnavailableAlertException(
                    "Hyperion coordination is configured for expected " + expectedDataMemberCount + " Hazelcast data members, but observed " + actualDataMemberCount
                            + ". Set jhipster.cache.hazelcast.expected-data-member-count to the number of core/data members on every node.",
                    ENTITY_NAME, "hyperionDataMemberTopologyMismatch");
        }
    }

    public boolean recordEvent(long exerciseId, String jobId, ExerciseGenerationEventDTO event, boolean terminal) {
        return replayStore.recordEvent(exerciseId, jobId, event, terminal);
    }

    public boolean recordFileChange(long exerciseId, String jobId, ExerciseGenerationFileChangeDTO fileChange) {
        return replayStore.recordFileChange(exerciseId, jobId, fileChange);
    }

    public boolean recordSpecDocument(long exerciseId, String jobId, String specDocument) {
        return replayStore.recordSpecDocument(exerciseId, jobId, specDocument);
    }

    public Optional<ExerciseGenerationStatusDTO> getStatus(User user, ProgrammingExercise exercise) {
        return replayStore.getStatus(user, exercise);
    }

    public void discardRetainedRun(long exerciseId, String jobId) {
        replayStore.discardRetainedRun(exerciseId, jobId);
    }

    /**
     * Cancels the running job — but only for the instructor who started it. Cancellation closes the transcript before interrupting the disposable sandbox, so clients receive a
     * terminal result even when a synchronous provider request cannot be aborted at the transport layer. The live slot remains claimed until the worker actually returns; this
     * prevents a late provider response from overlapping a replacement job or escaping in-flight admission accounting. The owner check matters because the job id is observable
     * in the client and websocket topic.
     *
     * Also refuses to record a cancellation once {@link #enterNonCancellablePhase(long, String)} has already flipped the job to persisting: at that point durable Git/DB
     * mutations may already be underway and cannot be safely interrupted, so this returns {@code false} (surfaced by the REST layer as "cannot cancel") rather than reporting
     * the dishonest "cancelled, nothing changed" outcome.
     *
     * @param exerciseId the exercise id
     * @param jobId      the job id to cancel
     * @param user       the requesting user; must be the instructor who started the job
     * @return {@code true} if a matching active, still-cancellable job owned by {@code user} was found and is cancelled, including an idempotent retry
     */
    public boolean requestCancellation(long exerciseId, String jobId, User user) {
        String key = key(exerciseId);
        ExerciseGenerationEventDTO cancellationEvent;
        jobMap.lock(key);
        try {
            JobInfo job = jobMap.get(key);
            if (job == null || !job.jobId().equals(jobId)) {
                return false;
            }
            if (!job.cancellable()) {
                log.debug("Ignored cancellation request for job {}: it already entered the non-cancellable persistence phase", jobId);
                return false;
            }
            GenerationJobReplayStore.CancellationReplayState replayState = replayStore.cancellationReplayState(job);
            if (replayState == null || !replayState.userLogin().equals(user.getLogin())) {
                return false;
            }
            if (replayState.done()) {
                return isCancelled(jobId);
            }
            cancellationMap.set(job.jobId(), Boolean.TRUE);
            cancellationEvent = replayStore.appendCancellation(job, USER_CANCELLATION_MESSAGE);
        }
        finally {
            jobMap.unlock(key);
        }
        if (cancellationEvent == null) {
            return false;
        }
        publishCancellation(user.getLogin(), jobId, cancellationEvent);
        interruptCluster(jobId);
        return true;
    }

    public boolean requestSystemCancellation(long exerciseId, String jobId) {
        return requestSystemCancellation(exerciseId, jobId, SYSTEM_CANCELLATION_MESSAGE);
    }

    /**
     * Cancels a job for server-side safety controls such as deadlines and token budgets, using the same atomic terminalization and late-response fence as user cancellation. The
     * caller already owns the job id from the running task, so no user ownership check is required.
     * <p>
     * Like {@link #requestCancellation(long, String, User)}, this refuses once the job already entered the non-cancellable persistence phase, so a deadline or budget trip that
     * loses the race against {@link #enterNonCancellablePhase(long, String)} never claims to have stopped a save that is already underway.
     */
    boolean requestSystemCancellation(long exerciseId, String jobId, String message) {
        String key = key(exerciseId);
        ExerciseGenerationEventDTO cancellationEvent;
        String userLogin;
        jobMap.lock(key);
        try {
            JobInfo job = jobMap.get(key);
            if (job == null || !job.jobId().equals(jobId)) {
                return false;
            }
            if (!job.cancellable()) {
                log.debug("Ignored system cancellation request for job {}: it already entered the non-cancellable persistence phase", jobId);
                return false;
            }
            GenerationJobReplayStore.CancellationReplayState replayState = replayStore.cancellationReplayState(job);
            if (replayState == null || replayState.done()) {
                return false;
            }
            cancellationMap.set(job.jobId(), Boolean.TRUE);
            cancellationEvent = replayStore.appendCancellation(job, message);
            userLogin = replayState.userLogin();
        }
        finally {
            jobMap.unlock(key);
        }
        if (cancellationEvent == null) {
            return false;
        }
        publishCancellation(userLogin, jobId, cancellationEvent);
        interruptCluster(jobId);
        return true;
    }

    private void publishCancellation(String userLogin, String jobId, ExerciseGenerationEventDTO cancellationEvent) {
        try {
            eventPublisher.publishEvent(new GenerationCancellationEvent(userLogin, jobId, cancellationEvent));
        }
        catch (RuntimeException e) {
            log.warn("Could not publish the live cancellation event for generation job {}", jobId, e);
        }
    }

    private void interruptCluster(String jobId) {
        // Run the node-local interrupt once on this node and publish a cluster-wide interrupt so cancellation is prompt even when the request hits a different core node than the
        // one running the sandbox. The hook remains node-local because it closes over live sandbox objects; every node simply tries remove-and-run for the job id.
        runLocalCancelHook(jobId);
        try {
            hazelcastInstance.<CancelRequest>getTopic(CANCEL_TOPIC_NAME).publish(new CancelRequest(jobId));
        }
        catch (RuntimeException e) {
            log.warn("Could not publish the cluster interrupt for cancelled generation job {}; workers will still observe the authoritative cancellation", jobId, e);
        }
    }

    private void runLocalCancelHook(String jobId) {
        Runnable hook = cancelHooks.remove(jobId);
        if (hook != null) {
            dispatchCancelHook(jobId, hook);
        }
    }

    private void dispatchCancelHook(String jobId, Runnable hook) {
        try {
            cancellationExecutor.execute(() -> runCancelHook(jobId, hook));
        }
        catch (RejectedExecutionException e) {
            log.error("Cancel hook dispatch for job {} was rejected; the generation worker will still observe the authoritative cancellation", jobId, e);
        }
    }

    private void runCancelHook(String jobId, Runnable hook) {
        try {
            hook.run();
        }
        catch (RuntimeException e) {
            log.warn("Cancel hook for job {} failed", jobId, e);
        }
    }

    /**
     * Marks the job as past the cancellation point and returns whether it may continue into durable persistence.
     * <p>
     * Cancellation is meaningful while the agent is still in the disposable sandbox: the cancel hook can destroy the session and no live repository has been touched. Once the
     * task starts saving verified output, accepting a new cancellation would be misleading because the repository operation cannot be safely interrupted. This transition and
     * {@link #requestCancellation(long, String, User)} (and {@link #requestSystemCancellation(long, String, String)}) both hold the same distributed job-map lock for the same
     * key, so exactly one of the two ever wins for a given job: if a cancellation was already recorded for {@code jobId} when this method acquires the lock, it returns
     * {@code false} here so the caller never persists a run the user (or the system) was already told was cancelled; conversely, once this method has flipped the job to
     * non-cancellable, a later {@link #requestCancellation(long, String, User)} observes {@code cancellable() == false} and refuses the cancel instead of falsely reporting
     * that nothing was changed.
     *
     * @param exerciseId the exercise id
     * @param jobId      the job id
     * @return {@code true} when this node still owns the job, no cancellation has been recorded for it, and it may proceed into persistence; {@code false} when ownership was
     *         lost or cancellation already won the race
     */
    public boolean enterNonCancellablePhase(long exerciseId, String jobId) {
        String key = key(exerciseId);
        jobMap.lock(key);
        try {
            JobInfo job = jobMap.get(key);
            if (job == null || !job.jobId().equals(jobId) || localNodeId == null || (job.ownerNodeId() != null && !job.ownerNodeId().equals(localNodeId))) {
                return false;
            }
            if (isCancelled(jobId)) {
                // Cancellation already won this job under the same lock (recorded by requestCancellation/requestSystemCancellation): the run is terminal as CANCELLED, so this
                // must not flip the job non-cancellable or allow the caller to persist.
                return false;
            }
            jobMap.set(key, job.withHeartbeat(Instant.now()).withCancellable(false));
        }
        finally {
            jobMap.unlock(key);
        }
        // The sandbox phase is over; there is no longer an in-flight tool/build operation that a cancel hook may safely interrupt.
        cancelHooks.remove(jobId);
        return true;
    }

    public boolean hasActiveJob(long exerciseId) {
        return jobMap.get(key(exerciseId)) != null;
    }

    public boolean isActiveJob(long exerciseId, String jobId) {
        JobInfo job = jobMap.get(key(exerciseId));
        return job != null && job.jobId().equals(jobId);
    }

    /**
     * Checks whether this JVM still owns the active exercise mutation slot. This is a best-effort stale-writer guard before durable Git/DB writes; repository head checks and DB
     * compare-and-set guards remain the authoritative clobber protection for external resources.
     *
     * @param exerciseId the exercise id whose job should be checked
     * @param jobId      the expected active job id
     * @return true if this JVM still owns the active job
     */
    public boolean isOwnedActiveJob(long exerciseId, String jobId) {
        String key = key(exerciseId);
        jobMap.lock(key);
        try {
            JobInfo job = jobMap.get(key);
            return job != null && job.jobId().equals(jobId) && localNodeId != null && (job.ownerNodeId() == null || job.ownerNodeId().equals(localNodeId));
        }
        finally {
            jobMap.unlock(key);
        }
    }

    /**
     * Refreshes the owning worker's liveness independently of progress events. A false return means this process no longer owns the job and must stop before durable mutations.
     *
     * @param exerciseId the exercise id whose job should be refreshed
     * @param jobId      the expected active job id
     * @return true if the heartbeat was recorded
     */
    public boolean heartbeat(long exerciseId, String jobId) {
        String key = key(exerciseId);
        jobMap.lock(key);
        try {
            JobInfo job = jobMap.get(key);
            if (job == null || !job.jobId().equals(jobId) || localNodeId == null || (job.ownerNodeId() != null && !job.ownerNodeId().equals(localNodeId))) {
                return false;
            }
            if (generationBudgetService != null && !generationBudgetService.refreshReservation(job.budgetReservationId())) {
                return false;
            }
            jobMap.set(key, job.withHeartbeat(Instant.now()));
            return true;
        }
        finally {
            jobMap.unlock(key);
        }
    }

    /**
     * Atomically claims the same per-exercise mutation slot used by generation/adaptation for a destructive adaptation revert. This closes the check-then-act race where a revert
     * could observe "no active job" and a generation could start before the repositories are reset.
     *
     * @param user       the requesting instructor
     * @param exerciseId the exercise id
     * @return an opaque slot token that must be passed to {@link #clearRevertSlot(long, String)}
     */
    public String claimRevertSlot(User user, long exerciseId) {
        String token = REVERT_JOB_PREFIX + UUID.randomUUID();
        Instant startedAt = Instant.now();
        JobInfo newJob = new JobInfo(token, user.getLogin(), exerciseId, startedAt, null, localNodeId, startedAt, false, null);
        claimSlot(key(exerciseId), newJob, "Exercise generation is already running for this exercise; wait for it to finish before reverting an adaptation.",
                "exerciseGenerationRunning");
        return token;
    }

    /**
     * Claims the same distributed per-exercise slot as generation for one authorized external REST mutation. Holding a real slot, rather than performing a check only, prevents a
     * generation from starting between the guard and the mutation.
     *
     * @param exerciseId the exercise being mutated
     * @return an opaque token that must be released with {@link #clearExternalMutationSlot(long, String)}
     */
    public String claimExternalMutationSlot(long exerciseId) {
        String token = EXTERNAL_MUTATION_JOB_PREFIX + UUID.randomUUID();
        Instant startedAt = Instant.now();
        JobInfo mutation = new JobInfo(token, "external", exerciseId, startedAt, null, localNodeId, startedAt, false, null);
        claimSlot(key(exerciseId), mutation, "Exercise generation is running for this exercise; wait for it to finish before making changes.", "exerciseGenerationRunning");
        return token;
    }

    /**
     * Releases an external mutation slot without ever clearing a newer owner.
     *
     * @param exerciseId the exercise id
     * @param token      the token returned from {@link #claimExternalMutationSlot(long)}
     */
    public void clearExternalMutationSlot(long exerciseId, String token) {
        if (token.startsWith(EXTERNAL_MUTATION_JOB_PREFIX)) {
            clearClaimedSlot(exerciseId, token);
        }
    }

    /**
     * Returns the external mutation currently blocking an exercise, if any.
     *
     * @param exerciseId the exercise id
     * @return the active external mutation, if present
     */
    public Optional<ExternalMutationInfo> getExternalMutationInfo(long exerciseId) {
        String key = key(exerciseId);
        jobMap.lock(key);
        try {
            JobInfo job = jobMap.get(key);
            if (job == null || !isExternalMutationJob(job)) {
                return Optional.empty();
            }
            return Optional.of(new ExternalMutationInfo(exerciseId, job.jobId(), job.ownerNodeId(), job.startedAt()));
        }
        finally {
            jobMap.unlock(key);
        }
    }

    /**
     * Value-guarded recovery for an external mutation whose owning JVM has been confirmed terminated. Cluster departure alone is insufficient because a partitioned request may
     * still be writing.
     *
     * @param exerciseId the exercise id
     * @param token      the exact external mutation token to recover
     * @return whether a departed owner's matching slot was recovered
     */
    public boolean recoverExternalMutationSlot(long exerciseId, String token) {
        if (!token.startsWith(EXTERNAL_MUTATION_JOB_PREFIX)) {
            return false;
        }
        String key = key(exerciseId);
        jobMap.lock(key);
        try {
            verifyExpectedDataMemberTopology();
            JobInfo job = jobMap.get(key);
            return job != null && job.jobId().equals(token) && isExternalMutationJob(job) && !ownerMemberIsPresent(job) && jobMap.remove(key, job);
        }
        finally {
            jobMap.unlock(key);
        }
    }

    /**
     * Releases a revert slot claimed with {@link #claimRevertSlot(User, long)}. Value-guarded so a delayed cleanup cannot clear a newer generation job.
     *
     * @param exerciseId the exercise id
     * @param token      the token returned from {@link #claimRevertSlot(User, long)}
     */
    public void clearRevertSlot(long exerciseId, String token) {
        clearClaimedSlot(exerciseId, token);
    }

    private void clearClaimedSlot(long exerciseId, String token) {
        String key = key(exerciseId);
        jobMap.lock(key);
        try {
            JobInfo job = jobMap.get(key);
            if (job != null && job.jobId().equals(token)) {
                jobMap.remove(key, job);
            }
        }
        finally {
            jobMap.unlock(key);
        }
    }

    public void registerCancelHook(String jobId, Runnable hook) {
        cancelHooks.put(jobId, hook);
        if (isCancelled(jobId) && cancelHooks.remove(jobId, hook)) {
            dispatchCancelHook(jobId, hook);
        }
    }

    public void deregisterCancelHook(String jobId) {
        cancelHooks.remove(jobId);
    }

    public boolean isCancelled(String jobId) {
        return Boolean.TRUE.equals(cancellationMap.get(jobId));
    }

    /**
     * Releases a completed job while retaining its transcript and fileChanges for reconnect replay.
     *
     * @param exerciseId the exercise whose job completed
     * @param jobId      the completed job identifier
     */
    public void clearJob(long exerciseId, String jobId) {
        String key = key(exerciseId);
        boolean released = false;
        jobMap.lock(key);
        try {
            JobInfo job = jobMap.get(key);
            if (job != null && job.jobId().equals(jobId)) {
                jobMap.remove(key, job);
                released = true;
            }
            replayStore.retainAfterJobCleared(exerciseId, jobId);
        }
        finally {
            jobMap.unlock(key);
        }
        cancellationMap.remove(jobId);
        if (released) {
            publishExerciseState(exerciseId, jobId, false);
        }
    }

    /** Cancels stale jobs and terminalizes jobs whose owner has left the Hazelcast cluster. */
    @Scheduled(fixedDelayString = "${artemis.hyperion.agent.stale-job-scan-ms:60000}")
    public void clearStaleJobs() {
        Instant now = Instant.now();
        Instant staleBefore = staleBefore(now);
        for (Map.Entry<String, JobInfo> entry : jobMap.entrySet()) {
            JobInfo job = entry.getValue();
            if (job == null || !shouldClearAsStale(job, staleBefore)) {
                continue;
            }
            String key = entry.getKey();
            jobMap.lock(key);
            try {
                JobInfo current = jobMap.get(key);
                if (current == null || !current.jobId().equals(job.jobId()) || !shouldClearAsStale(current, staleBefore)) {
                    continue;
                }
                reclaimStaleJob(key, current, Instant.now());
            }
            finally {
                jobMap.unlock(key);
            }
        }
    }

    /**
     * Reclaims a stale slot only after Hazelcast confirms that its owner left the cluster, and only while the job is still cancellable. A stale heartbeat from a live member can
     * be a scheduler pause, so a cancellable job is asked to stop while it keeps its slot until the worker actually drains. Once a <em>cancellable</em> generation owner has
     * left, the worker can no longer pass its ownership checks; the job becomes terminal and its slot is released. Its worst-case budget reservation remains until the rolling
     * budget window expires because provider usage may have become billable before the owner disappeared.
     * <p>
     * A <em>non-cancellable</em> job (generation past {@link #enterNonCancellablePhase(long, String)}, or a {@code revert-*} slot) is never automatically removed, regardless of
     * heartbeat age or owner membership: absence from the local Hazelcast membership view is a failure-detector result (GC pause, network partition, false-positive detection),
     * not proof that the owning JVM — and any Git/DB request it already issued before durable persistence or a revert reset — has actually stopped. Reclaiming the slot here
     * could let a replacement generation start and interleave with an un-fenced writer still completing a mutation. This generalizes the same fail-closed rule already applied
     * to {@code external-mutation-*} slots below. Availability is sacrificed only in this precisely unknowable state; the slot requires the same kind of audited, exact-token
     * recovery as an external mutation to be released (see {@link #recoverExternalMutationSlot(long, String)}).
     *
     * @return {@code true} when the slot was reclaimed
     */
    private boolean reclaimStaleJob(String key, JobInfo current, Instant now) {
        // An external request is not fenced or hard-cancelled by Hazelcast membership loss. Retain its no-TTL slot until normal completion or explicit recovery after the JVM is
        // terminated; clearing it here could overlap a partitioned writer with a new generation.
        if (isExternalMutationJob(current)) {
            return false;
        }
        if (!current.cancellable()) {
            // Durable persistence/revert mutation may already be in flight. Fail closed: retain the slot so a replacement claim conflicts instead of possibly overlapping the
            // old owner's un-fenced Git/DB writes. Only an absent owner is logged; a live owner with a merely stale heartbeat (a scheduler pause, say) is not actionable.
            if (!ownerMemberIsPresent(current)) {
                log.warn(
                        "Retaining non-cancellable generation slot for job {} (exercise {}) after its owner {} left the Hazelcast cluster: cluster departure does not prove that "
                                + "the in-flight persistence/revert mutation has stopped. The slot stays claimed to block a replacement job; release it only via audited, "
                                + "exact-token manual recovery once the old owner and its Git/DB requests are confirmed quiescent.",
                        current.jobId(), current.exerciseId(), current.ownerNodeId());
            }
            return false;
        }
        if (ownerMemberIsPresent(current)) {
            signalStaleLiveOwner(current, now);
            return false;
        }
        stopActiveJob(key, current, now);
        return true;
    }

    private boolean ownerMemberIsPresent(JobInfo job) {
        if (job.ownerNodeId() == null) {
            return true;
        }
        try {
            return hazelcastInstance.getCluster().getMembers().stream().anyMatch(member -> member.getUuid().toString().equals(job.ownerNodeId()));
        }
        catch (RuntimeException e) {
            log.warn("Could not determine whether the owner of stale generation job {} is still a cluster member; retaining its slot", job.jobId(), e);
            return true;
        }
    }

    private void signalStaleLiveOwner(JobInfo current, Instant now) {
        boolean alreadyCancelled = Boolean.TRUE.equals(cancellationMap.get(current.jobId()));
        cancellationMap.set(current.jobId(), Boolean.TRUE);
        replayStore.terminalizeStoppedJob(current, stoppedMessage(current, now));
        if (!alreadyCancelled) {
            interruptCluster(current.jobId());
        }
    }

    private void stopActiveJob(String key, JobInfo current, Instant now) {
        cancellationMap.set(current.jobId(), Boolean.TRUE, Math.max(1, maxJobDuration.toSeconds()), TimeUnit.SECONDS);
        replayStore.terminalizeStoppedJob(current, stoppedMessage(current, now));
        if (jobMap.remove(key, current)) {
            replayStore.retainAfterJobCleared(current.exerciseId(), current.jobId());
        }
        retainUncertainBudgetReservation(current);
        interruptCluster(current.jobId());
        if (isGenerationJob(current)) {
            publishExerciseState(current.exerciseId(), current.jobId(), false);
        }
    }

    static boolean isGenerationJob(JobInfo job) {
        return !job.jobId().startsWith(REVERT_JOB_PREFIX) && !job.jobId().startsWith(EXTERNAL_MUTATION_JOB_PREFIX);
    }

    private boolean isExternalMutationJob(JobInfo job) {
        return job.jobId().startsWith(EXTERNAL_MUTATION_JOB_PREFIX);
    }

    private void publishExerciseState(long exerciseId, String jobId, boolean running) {
        try {
            eventPublisher.publishEvent(new ExerciseGenerationStateChangedEvent(new ExerciseGenerationStateDTO(exerciseId, jobId, running)));
        }
        catch (RuntimeException e) {
            log.warn("Could not publish shared generation state for exercise {} and job {}", exerciseId, jobId, e);
        }
    }

    private void retainUncertainBudgetReservation(JobInfo job) {
        if (isGenerationJob(job) && generationBudgetService != null) {
            generationBudgetService.retainReservationForBudgetWindow(job.budgetReservationId());
        }
    }

    @Nullable
    private Instant staleBefore(Instant now) {
        return staleJobTimeout == null || staleJobTimeout.isZero() || staleJobTimeout.isNegative() ? null : now.minus(staleJobTimeout);
    }

    private boolean shouldClearAsStale(JobInfo job, @Nullable Instant staleBefore) {
        return (staleBefore != null && !job.lastHeartbeatOrStartedAt().isAfter(staleBefore)) || !ownerMemberIsPresent(job);
    }

    private String stoppedMessage(JobInfo job, Instant now) {
        if (job.deadlineAt() != null && !job.deadlineAt().isAfter(now)) {
            return "Generation stopped because it exceeded the configured time limit. Nothing was changed.";
        }
        return "Generation stopped because the owning node stopped sending heartbeats. Review the exercise and repositories before use if this happened while saving.";
    }

    private static String key(long exerciseId) {
        return String.valueOf(exerciseId);
    }

    private Instant deadlineAt(Instant startedAt) {
        return startedAt.plus(maxJobDuration);
    }

    public record ExternalMutationInfo(long exerciseId, String token, @Nullable String ownerNodeId, Instant startedAt) {
    }

    public record JobInfo(String jobId, String userLogin, long exerciseId, Instant startedAt, @Nullable Instant deadlineAt, @Nullable String ownerNodeId,
            @Nullable Instant lastHeartbeatAt, boolean cancellable, @Nullable String budgetReservationId) implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        Instant lastHeartbeatOrStartedAt() {
            return lastHeartbeatAt == null ? startedAt : lastHeartbeatAt;
        }

        JobInfo withHeartbeat(Instant heartbeatAt) {
            return new JobInfo(jobId, userLogin, exerciseId, startedAt, deadlineAt, ownerNodeId, heartbeatAt, cancellable, budgetReservationId);
        }

        JobInfo withCancellable(boolean newCancellable) {
            return new JobInfo(jobId, userLogin, exerciseId, startedAt, deadlineAt, ownerNodeId, lastHeartbeatAt, newCancellable, budgetReservationId);
        }
    }

    private record CancelRequest(String jobId) implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;
    }

    public record JobTranscript(String jobId, String userLogin, long exerciseId, GenerationMode mode, List<ExerciseGenerationEventDTO> events, boolean done,
            @Nullable String specDocument) implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;
    }

    public record JobFileChangeIndex(String jobId, String userLogin, List<ExerciseGenerationFileChangeDTO> changes) implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;
    }
}
