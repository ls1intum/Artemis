package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

import java.io.Serial;
import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
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
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationStatusDTO;
import de.tum.cit.aet.artemis.hyperion.dto.GenerationMode;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

/**
 * Single-flight slot and cancellation registry for whole-exercise agentic generation jobs.
 * <p>
 * At most one generation runs per exercise at a time (claimed atomically in a Hazelcast map with a TTL safety net), and a separate cancellation set lets the REST layer request
 * a cooperative abort that the running loop polls between turns. This per-exercise single-flight is the primary concurrency bound.
 */
@Service
@Lazy
@Conditional(HyperionExerciseGenerationEnabled.class)
public class GenerationJobService {

    private static final Logger log = LoggerFactory.getLogger(GenerationJobService.class);

    private static final String JOB_MAP_NAME = "hyperion-exercise-generation-jobs";

    private static final String CANCEL_MAP_NAME = "hyperion-exercise-generation-cancellations";

    private static final String CANCEL_TOPIC_NAME = "hyperion-exercise-generation-cancel-requests";

    private static final String TRANSCRIPT_MAP_NAME = "hyperion-exercise-generation-transcripts";

    static final String FILE_CHANGE_MAP_NAME = "hyperion-exercise-generation-file-changes";

    private static final String ENTITY_NAME = "hyperionExerciseGeneration";

    private static final String REVERT_JOB_PREFIX = "revert-";

    static final String GENERATION_PIPELINE_ID = "HYPERION_EXERCISE_GENERATION";

    private static final int JOB_TTL_SECONDS = 7200;

    private static final int TRANSCRIPT_TTL_SECONDS = 900;

    private static final int MAX_RETAINED_EVENTS = 500;

    private static final String USER_CANCELLATION_MESSAGE = "Generation was cancelled. Nothing was changed.";

    private static final String SYSTEM_CANCELLATION_MESSAGE = "Generation was cancelled by an administrator. Nothing was changed.";

    static final int MAX_RETAINED_FILE_CHANGES = 300;

    private final HazelcastInstance hazelcastInstance;

    // Run launched via an event so this service does not depend on the task service, which would close a construction cycle.
    private final ApplicationEventPublisher eventPublisher;

    private final LLMTokenUsageService llmTokenUsageService;

    private final HyperionGenerationBudgetService generationBudgetService;

    private final Duration staleJobTimeout;

    private final Duration maxJobDuration;

    private String localNodeId;

    private IMap<String, JobInfo> jobMap;

    private IMap<String, Boolean> cancellationMap;

    private IMap<String, JobTranscript> transcriptMap;

    private IMap<String, JobFileChangeIndex> fileChangeMap;

    // Node-local interrupts held in-process because the hook closes over a live sandbox reference that exists only on the node running the job; other nodes rely on the Hazelcast
    // flag.
    private final ConcurrentMap<String, Runnable> cancelHooks = new ConcurrentHashMap<>();

    @Autowired
    public GenerationJobService(@Qualifier("hazelcastInstance") HazelcastInstance hazelcastInstance, ApplicationEventPublisher eventPublisher,
            LLMTokenUsageService llmTokenUsageService, HyperionGenerationBudgetService generationBudgetService,
            @Value("${artemis.hyperion.agent.stale-job-timeout:PT35M}") Duration staleJobTimeout,
            @Value("${artemis.hyperion.agent.max-job-duration:PT30M}") Duration maxJobDuration) {
        this.hazelcastInstance = hazelcastInstance;
        this.eventPublisher = eventPublisher;
        this.llmTokenUsageService = llmTokenUsageService;
        this.generationBudgetService = generationBudgetService;
        this.staleJobTimeout = staleJobTimeout;
        this.maxJobDuration = maxJobDuration;
    }

    GenerationJobService(HazelcastInstance hazelcastInstance, ApplicationEventPublisher eventPublisher, LLMTokenUsageService llmTokenUsageService) {
        this(hazelcastInstance, eventPublisher, llmTokenUsageService, null, Duration.ofMinutes(35), Duration.ofMinutes(30));
    }

    GenerationJobService(HazelcastInstance hazelcastInstance, ApplicationEventPublisher eventPublisher, LLMTokenUsageService llmTokenUsageService, Duration staleJobTimeout,
            Duration maxJobDuration) {
        this(hazelcastInstance, eventPublisher, llmTokenUsageService, null, staleJobTimeout, maxJobDuration);
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
        return chatResponse -> llmTokenUsageService.trackChatResponseTokenUsage(chatResponse, LLMServiceType.HYPERION, GENERATION_PIPELINE_ID,
                builder -> builder.withCourse(courseId).withExercise(exerciseId).withUser(userId));
    }

    /** Initializes the distributed job state and local cancellation listener. */
    @PostConstruct
    public void init() {
        if (maxJobDuration == null || maxJobDuration.isZero() || maxJobDuration.isNegative()) {
            throw new IllegalArgumentException("artemis.hyperion.agent.max-job-duration must be positive");
        }
        if (staleJobTimeout == null || staleJobTimeout.compareTo(maxJobDuration) <= 0) {
            throw new IllegalArgumentException("artemis.hyperion.agent.stale-job-timeout must be greater than max-job-duration");
        }
        jobMap = hazelcastInstance.getMap(JOB_MAP_NAME);
        cancellationMap = hazelcastInstance.getMap(CANCEL_MAP_NAME);
        transcriptMap = hazelcastInstance.getMap(TRANSCRIPT_MAP_NAME);
        fileChangeMap = hazelcastInstance.getMap(FILE_CHANGE_MAP_NAME);
        ITopic<CancelRequest> cancelTopic = hazelcastInstance.getTopic(CANCEL_TOPIC_NAME);
        cancelTopic.addMessageListener(message -> runLocalCancelHook(message.getMessageObject().jobId()));
        localNodeId = hazelcastInstance.getCluster().getLocalMember().getUuid().toString();
    }

    public String startJob(User user, ProgrammingExercise exercise, String userPrompt, GenerationMode mode) {
        return startJob(user, exercise, userPrompt, mode, null);
    }

    /**
     * Starts a new whole-exercise generation job and attaches a pre-admission budget reservation to the job metadata.
     *
     * @param user                the requesting instructor
     * @param exercise            the target exercise
     * @param userPrompt          the generation brief or the feedback to address
     * @param mode                the explicit run intent
     * @param budgetReservationId the optional token-budget reservation id to release when the job ends
     * @return the started job id
     */
    public String startJob(User user, ProgrammingExercise exercise, String userPrompt, GenerationMode mode, @Nullable String budgetReservationId) {
        String jobId = UUID.randomUUID().toString();
        String key = key(exercise.getId());
        Instant startedAt = Instant.now();
        Instant deadlineAt = deadlineAt(startedAt);
        JobInfo newJob = new JobInfo(jobId, user.getLogin(), exercise.getId(), startedAt, deadlineAt, localNodeId, startedAt, true, budgetReservationId);
        claimSlot(key, newJob, "Exercise generation is already running for this exercise", "exerciseGenerationRunning");
        // Fresh transcript and fileChange store for this run. Keep the previous replay state until the async event is accepted; if publishing fails synchronously, rollback
        // restores
        // the prior terminal replay instead of leaving the status endpoint empty.
        JobTranscript previousTranscript = transcriptMap.get(key);
        JobFileChangeIndex previousFileChangeIndex = fileChangeMap.get(key);
        JobTranscript transcript = new JobTranscript(jobId, user.getLogin(), exercise.getId(), mode, new ArrayList<>(), false);
        transcriptMap.set(key, transcript, JOB_TTL_SECONDS, TimeUnit.SECONDS);
        JobFileChangeIndex fileChangeIndex = new JobFileChangeIndex(jobId, user.getLogin(), new ArrayList<>());
        fileChangeMap.set(key, fileChangeIndex, JOB_TTL_SECONDS, TimeUnit.SECONDS);
        try {
            eventPublisher.publishEvent(
                    new GenerationStartedEvent(jobId, user, exercise, userPrompt, mode, exercise.getProblemStatement(), exercise.getTitle(), deadlineAt, budgetReservationId));
        }
        catch (RejectedExecutionException e) {
            // The generation executor is saturated (AbortPolicy). The @Async listener never ran, so no terminal event will ever fire — roll back the claimed slot and its retained
            // state (value-guarded, so a later run for this exercise is never clobbered) instead of leaving the exercise wedged as "running" for the full TTL, and surface a busy
            // error the instructor can act on. Note: TaskRejectedException (thrown by ThreadPoolTaskExecutor) is a RejectedExecutionException subclass, so it is caught here too.
            rollbackUnpublishedStart(exercise.getId(), key, newJob, transcript, fileChangeIndex, previousTranscript, previousFileChangeIndex);
            log.warn("Exercise generation executor rejected job {} for exercise {}; released the slot", jobId, exercise.getId());
            throw new ServiceUnavailableAlertException("The system is currently busy with too many exercise generations. Please try again in a few minutes.", ENTITY_NAME,
                    "exerciseGenerationCapacityExceeded");
        }
        catch (RuntimeException e) {
            rollbackUnpublishedStart(exercise.getId(), key, newJob, transcript, fileChangeIndex, previousTranscript, previousFileChangeIndex);
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

    private void rollbackUnpublishedStart(long exerciseId, String key, JobInfo newJob, JobTranscript newTranscript, JobFileChangeIndex newFileChangeIndex,
            @Nullable JobTranscript previousTranscript, @Nullable JobFileChangeIndex previousFileChangeIndex) {
        jobMap.lock(key);
        try {
            jobMap.remove(key, newJob);
            if (previousTranscript == null) {
                transcriptMap.remove(key, newTranscript);
            }
            else {
                restoreTranscriptIfStillUnpublished(key, newTranscript, previousTranscript);
            }
            if (previousFileChangeIndex == null) {
                fileChangeMap.remove(key, newFileChangeIndex);
            }
            else {
                restoreFileChangeIndexIfStillUnpublished(key, newFileChangeIndex, previousFileChangeIndex);
            }
        }
        finally {
            jobMap.unlock(key);
        }
    }

    private void restoreTranscriptIfStillUnpublished(String key, JobTranscript newTranscript, JobTranscript previousTranscript) {
        transcriptMap.lock(key);
        try {
            if (newTranscript.equals(transcriptMap.get(key))) {
                transcriptMap.set(key, previousTranscript, TRANSCRIPT_TTL_SECONDS, TimeUnit.SECONDS);
            }
        }
        finally {
            transcriptMap.unlock(key);
        }
    }

    private void restoreFileChangeIndexIfStillUnpublished(String key, JobFileChangeIndex newFileChangeIndex, JobFileChangeIndex previousFileChangeIndex) {
        fileChangeMap.lock(key);
        try {
            if (newFileChangeIndex.equals(fileChangeMap.get(key))) {
                fileChangeMap.set(key, previousFileChangeIndex, TRANSCRIPT_TTL_SECONDS, TimeUnit.SECONDS);
            }
        }
        finally {
            fileChangeMap.unlock(key);
        }
    }

    private void claimSlot(String key, JobInfo newJob, String conflictMessage, String errorKey) {
        jobMap.lock(key);
        try {
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
            jobMap.set(key, newJob, JOB_TTL_SECONDS, TimeUnit.SECONDS);
        }
        finally {
            jobMap.unlock(key);
        }
    }

    /**
     * Appends an event to the running job's transcript so it can be replayed when a client (re)connects. Bounded so a long run cannot grow the distributed map without limit.
     *
     * @param exerciseId the exercise id (the transcript key)
     * @param jobId      the job id; the event is dropped if it does not match the retained transcript (a stale/older run)
     * @param event      the event to retain
     * @param terminal   whether this event terminates the run (marks the transcript done, so a reconnecting client knows not to expect more)
     */
    public void recordEvent(long exerciseId, String jobId, ExerciseGenerationEventDTO event, boolean terminal) {
        String key = key(exerciseId);
        jobMap.lock(key);
        try {
            if (!isActiveJob(exerciseId, jobId)) {
                return;
            }
            JobTranscript transcript = transcriptMap.get(key);
            if (transcript != null && transcript.jobId().equals(jobId) && !transcript.done()) {
                List<ExerciseGenerationEventDTO> events = new ArrayList<>(transcript.events());
                events.add(event);
                while (events.size() > MAX_RETAINED_EVENTS) {
                    events.remove(1);
                }
                transcriptMap.set(key,
                        new JobTranscript(transcript.jobId(), transcript.userLogin(), transcript.exerciseId(), transcript.mode(), events, terminal || transcript.done()),
                        JOB_TTL_SECONDS, TimeUnit.SECONDS);
            }
            refreshActiveJobRetainedStateTtl(exerciseId, jobId);
        }
        finally {
            jobMap.unlock(key);
        }
    }

    private void refreshActiveJobRetainedStateTtl(long exerciseId, String jobId) {
        String key = key(exerciseId);
        JobInfo job = jobMap.get(key);
        if (job == null || !job.jobId().equals(jobId)) {
            return;
        }
        jobMap.set(key, job.withHeartbeat(Instant.now()), JOB_TTL_SECONDS, TimeUnit.SECONDS);
        JobTranscript transcript = transcriptMap.get(key);
        if (transcript != null && transcript.jobId().equals(jobId)) {
            transcriptMap.setTtl(key, JOB_TTL_SECONDS, TimeUnit.SECONDS);
        }
        JobFileChangeIndex fileChangeIndex = fileChangeMap.get(key);
        if (fileChangeIndex != null && fileChangeIndex.jobId().equals(jobId)) {
            fileChangeMap.setTtl(key, JOB_TTL_SECONDS, TimeUnit.SECONDS);
        }
    }

    /**
     * Records the latest lightweight change per path for reconnect replay.
     *
     * @param exerciseId the exercise id (the file-change key)
     * @param jobId      the job id; the change is dropped if it does not match the retained store (a stale/older run)
     * @param fileChange the lightweight file-change metadata to retain
     */
    public void recordFileChange(long exerciseId, String jobId, ExerciseGenerationFileChangeDTO fileChange) {
        String exerciseKey = key(exerciseId);
        jobMap.lock(exerciseKey);
        try {
            JobFileChangeIndex index = fileChangeMap.get(exerciseKey);
            if (index == null || !index.jobId().equals(jobId) || !isActiveJob(exerciseId, jobId) || isCancelled(jobId)) {
                return;
            }
            List<ExerciseGenerationFileChangeDTO> changes = new ArrayList<>(index.changes());
            int existingIndex = -1;
            for (int i = 0; i < changes.size(); i++) {
                if (changes.get(i).path().equals(fileChange.path())) {
                    existingIndex = i;
                    break;
                }
            }
            if (existingIndex >= 0) {
                changes.set(existingIndex, fileChange);
            }
            else {
                changes.add(fileChange);
                while (changes.size() > MAX_RETAINED_FILE_CHANGES) {
                    changes.removeFirst();
                }
            }
            fileChangeMap.set(exerciseKey, new JobFileChangeIndex(index.jobId(), index.userLogin(), changes), JOB_TTL_SECONDS, TimeUnit.SECONDS);
            refreshActiveJobRetainedStateTtl(exerciseId, jobId);
        }
        finally {
            jobMap.unlock(exerciseKey);
        }
    }

    /**
     * Returns the current or most-recent run's transcript for the exercise, for reconnection/replay, if it belongs to the requesting user.
     *
     * @param user     the requesting user
     * @param exercise the exercise
     * @return the reconnection view (with a {@code running} flag derived from the live slot), or empty if none is retained for this user
     */
    public Optional<ExerciseGenerationStatusDTO> getStatus(User user, ProgrammingExercise exercise) {
        String exerciseKey = key(exercise.getId());
        jobMap.lock(exerciseKey);
        try {
            JobTranscript transcript = transcriptMap.get(exerciseKey);
            JobInfo active = jobMap.get(exerciseKey);
            if (active != null) {
                boolean ownedByCaller = active.userLogin().equals(user.getLogin());
                GenerationMode mode = transcript != null && transcript.jobId().equals(active.jobId()) ? transcript.mode() : null;
                if (!ownedByCaller && transcript != null && transcript.jobId().equals(active.jobId()) && transcript.done()) {
                    ExerciseGenerationEventDTO terminal = sanitizedTerminalOutcome(transcript);
                    return terminal == null ? Optional.empty()
                            : Optional.of(
                                    new ExerciseGenerationStatusDTO(transcript.jobId(), false, transcript.mode(), List.of(terminal), List.of(), false, null, null, false, false));
                }
                if (!ownedByCaller || transcript == null || !transcript.jobId().equals(active.jobId()) || !transcript.userLogin().equals(user.getLogin())) {
                    return Optional.of(new ExerciseGenerationStatusDTO(active.jobId(), true, mode, List.of(), List.of(), false, null, null, ownedByCaller,
                            ownedByCaller && active.cancellable()));
                }
                return Optional.of(new ExerciseGenerationStatusDTO(transcript.jobId(), !transcript.done(), transcript.mode(), transcript.events(),
                        latestFileChangesFor(exercise.getId(), transcript.jobId()), false, null, null, true, !transcript.done() && active.cancellable()));
            }
            if (transcript == null) {
                return Optional.empty();
            }
            if (!transcript.userLogin().equals(user.getLogin())) {
                ExerciseGenerationEventDTO terminal = sanitizedTerminalOutcome(transcript);
                if (terminal == null) {
                    return Optional.empty();
                }
                return Optional.of(new ExerciseGenerationStatusDTO(transcript.jobId(), false, transcript.mode(), List.of(terminal), List.of(), false, null, null, false, false));
            }
            return Optional.of(new ExerciseGenerationStatusDTO(transcript.jobId(), false, transcript.mode(), transcript.events(),
                    latestFileChangesFor(exercise.getId(), transcript.jobId()), false, null, null, true, false));
        }
        finally {
            jobMap.unlock(exerciseKey);
        }
    }

    @Nullable
    private ExerciseGenerationEventDTO sanitizedTerminalOutcome(JobTranscript transcript) {
        for (int index = transcript.events().size() - 1; index >= 0; index--) {
            ExerciseGenerationEventDTO event = transcript.events().get(index);
            if (event.type() == ExerciseGenerationEventDTO.Type.DONE || event.type() == ExerciseGenerationEventDTO.Type.CANCELLED
                    || event.type() == ExerciseGenerationEventDTO.Type.ERROR) {
                return new ExerciseGenerationEventDTO(event.type(), null, event.completionStatus(), null, event.liveExerciseChanged(), null, event.timestamp());
            }
        }
        return null;
    }

    /**
     * Removes a matching completed run replay after its live changes were undone.
     *
     * @param exerciseId the exercise whose replay should be removed
     * @param jobId      the completed run to remove
     */
    public void discardRetainedRun(long exerciseId, String jobId) {
        String key = key(exerciseId);
        jobMap.lock(key);
        try {
            JobTranscript transcript = transcriptMap.get(key);
            if (transcript != null && transcript.jobId().equals(jobId)) {
                transcriptMap.remove(key, transcript);
            }
            JobFileChangeIndex index = fileChangeMap.get(key);
            if (index != null && index.jobId().equals(jobId)) {
                fileChangeMap.remove(key, index);
            }
        }
        finally {
            jobMap.unlock(key);
        }
    }

    /**
     * Returns the latest fileChange per file for the given run, in write order, or an empty list if none are retained or they belong to a different run.
     */
    private List<ExerciseGenerationFileChangeDTO> latestFileChangesFor(long exerciseId, String jobId) {
        JobFileChangeIndex index = fileChangeMap.get(key(exerciseId));
        if (index == null || !index.jobId().equals(jobId)) {
            return List.of();
        }
        return index.changes();
    }

    /**
     * Cancels the running job — but only for the instructor who started it. Cancellation closes the transcript before interrupting the disposable sandbox, so clients receive a
     * terminal result even when a synchronous provider request cannot be aborted at the transport layer. The live slot remains claimed until the worker actually returns; this
     * prevents a late provider response from overlapping a replacement job or escaping in-flight admission accounting. The owner check matters because the job id is observable
     * in the client and websocket topic.
     *
     * @param exerciseId the exercise id
     * @param jobId      the job id to cancel
     * @param user       the requesting user; must be the instructor who started the job
     * @return {@code true} if a matching active job owned by {@code user} was found and marked for cancellation
     */
    public boolean requestCancellation(long exerciseId, String jobId, User user) {
        String key = key(exerciseId);
        boolean cancelled;
        jobMap.lock(key);
        try {
            JobInfo job = jobMap.get(key);
            if (job == null || !job.jobId().equals(jobId) || !job.cancellable()) {
                return false;
            }
            JobTranscript transcript = transcriptMap.get(key);
            if (transcript == null || !transcript.jobId().equals(jobId) || !transcript.userLogin().equals(user.getLogin())) {
                return false;
            }
            cancelled = terminalizeCancellation(key, job, transcript, USER_CANCELLATION_MESSAGE);
        }
        finally {
            jobMap.unlock(key);
        }
        if (!cancelled) {
            return false;
        }
        interruptCluster(jobId);
        return true;
    }

    /**
     * Cancels a job for server-side safety controls such as deadlines and token budgets. This uses the same atomic terminalization and late-response fence as user cancellation.
     * The
     * caller already owns the job id from the running task, so no user ownership check is required.
     *
     * @param exerciseId the exercise id whose job should be cancelled
     * @param jobId      the running job id
     * @return true if cancellation was recorded
     */
    public boolean requestSystemCancellation(long exerciseId, String jobId) {
        return requestSystemCancellation(exerciseId, jobId, SYSTEM_CANCELLATION_MESSAGE);
    }

    boolean requestSystemCancellation(long exerciseId, String jobId, String message) {
        String key = key(exerciseId);
        boolean cancelled;
        jobMap.lock(key);
        try {
            JobInfo job = jobMap.get(key);
            if (job == null || !job.jobId().equals(jobId) || !job.cancellable()) {
                return false;
            }
            JobTranscript transcript = transcriptMap.get(key);
            cancelled = terminalizeCancellation(key, job, transcript, message);
        }
        finally {
            jobMap.unlock(key);
        }
        if (!cancelled) {
            return false;
        }
        interruptCluster(jobId);
        return true;
    }

    private boolean terminalizeCancellation(String key, JobInfo job, @Nullable JobTranscript transcript, String message) {
        if (transcript == null || !transcript.jobId().equals(job.jobId()) || transcript.done()) {
            return false;
        }
        cancellationMap.set(job.jobId(), Boolean.TRUE, JOB_TTL_SECONDS, TimeUnit.SECONDS);
        List<ExerciseGenerationEventDTO> events = new ArrayList<>(transcript.events());
        events.add(ExerciseGenerationEventDTO.of(ExerciseGenerationEventDTO.Type.CANCELLED, message));
        while (events.size() > MAX_RETAINED_EVENTS) {
            events.remove(1);
        }
        transcriptMap.set(key, new JobTranscript(transcript.jobId(), transcript.userLogin(), transcript.exerciseId(), transcript.mode(), events, true), TRANSCRIPT_TTL_SECONDS,
                TimeUnit.SECONDS);
        retainFileChangesForTerminalReplay(job.exerciseId(), job.jobId());
        return true;
    }

    private void interruptCluster(String jobId) {
        // Run the node-local interrupt once on this node and publish a cluster-wide interrupt so cancellation is prompt even when the request hits a different core node than the
        // one running the sandbox. The hook remains node-local because it closes over live sandbox objects; every node simply tries remove-and-run for the job id.
        runLocalCancelHook(jobId);
        hazelcastInstance.<CancelRequest>getTopic(CANCEL_TOPIC_NAME).publish(new CancelRequest(jobId));
    }

    private void runLocalCancelHook(String jobId) {
        Runnable hook = cancelHooks.remove(jobId);
        if (hook != null) {
            runCancelHook(jobId, hook);
        }
    }

    private void runCancelHook(String jobId, Runnable hook) {
        try {
            hook.run();
        }
        catch (RuntimeException e) {
            log.warn("Cancel hook for job {} failed: {}", jobId, e.getMessage());
        }
    }

    /**
     * Marks the job as past the cancellation point and returns whether it may continue into durable persistence.
     * <p>
     * Cancellation is meaningful while the agent is still in the disposable sandbox: the cancel hook can destroy the session and no live repository has been touched. Once the
     * task starts saving verified output, accepting a new cancellation would be misleading because the repository operation cannot be safely interrupted. The same
     * distributed job-map lock is used by {@link #requestCancellation(long, String, User)} so a cancel cannot race with this transition across core nodes.
     *
     * @param exerciseId the exercise id
     * @param jobId      the job id
     * @return {@code true} when this node still owns the job and may proceed; {@code false} when ownership was lost
     */
    public boolean enterNonCancellablePhase(long exerciseId, String jobId) {
        String key = key(exerciseId);
        jobMap.lock(key);
        try {
            JobInfo job = jobMap.get(key);
            if (job == null || !job.jobId().equals(jobId) || localNodeId == null || (job.ownerNodeId() != null && !job.ownerNodeId().equals(localNodeId))) {
                return false;
            }
            jobMap.set(key, job.withHeartbeat(Instant.now()).withCancellable(false), JOB_TTL_SECONDS, TimeUnit.SECONDS);
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
            jobMap.set(key, job.withHeartbeat(Instant.now()), JOB_TTL_SECONDS, TimeUnit.SECONDS);
            if (generationBudgetService != null) {
                generationBudgetService.refreshReservation(job.budgetReservationId());
            }
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
     * Releases a revert slot claimed with {@link #claimRevertSlot(User, long)}. Value-guarded so a delayed cleanup cannot clear a newer generation job.
     *
     * @param exerciseId the exercise id
     * @param token      the token returned from {@link #claimRevertSlot(User, long)}
     */
    public void clearRevertSlot(long exerciseId, String token) {
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
            runCancelHook(jobId, hook);
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
        jobMap.lock(key);
        try {
            JobInfo job = jobMap.get(key);
            if (job != null && job.jobId().equals(jobId)) {
                jobMap.remove(key, job);
            }
            JobTranscript transcript = transcriptMap.get(key);
            if (transcript != null && transcript.jobId().equals(jobId)) {
                JobTranscript retainedTranscript = transcript.done() ? transcript
                        : new JobTranscript(transcript.jobId(), transcript.userLogin(), transcript.exerciseId(), transcript.mode(), transcript.events(), true);
                transcriptMap.set(key, retainedTranscript, TRANSCRIPT_TTL_SECONDS, TimeUnit.SECONDS);
            }
        }
        finally {
            jobMap.unlock(key);
        }
        cancellationMap.remove(jobId);
        JobFileChangeIndex fileChangeIndex = fileChangeMap.get(key);
        if (fileChangeIndex != null && fileChangeIndex.jobId().equals(jobId)) {
            fileChangeMap.setTtl(key, TRANSCRIPT_TTL_SECONDS, TimeUnit.SECONDS);
        }
    }

    /** Cancels stale jobs whose owner or execution deadline has expired. */
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
     * Reclaims a stale slot only after Hazelcast confirms that its owner left the cluster. A stale heartbeat from a live member can be a scheduler pause, so a cancellable job is
     * asked to stop while a non-cancellable persistence job retains its slot. Once the owner has left, no worker can finish either phase; the job becomes terminal and its slot and
     * budget reservation are released for manual inspection and recovery.
     *
     * @return {@code true} when the slot was reclaimed
     */
    private boolean reclaimStaleJob(String key, JobInfo current, Instant now) {
        if (ownerMemberIsPresent(current)) {
            if (current.cancellable()) {
                signalStaleLiveOwner(current, now);
            }
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
        cancellationMap.set(current.jobId(), Boolean.TRUE, JOB_TTL_SECONDS, TimeUnit.SECONDS);
        markStoppedTranscript(current, stoppedMessage(current, now));
        retainFileChangesForTerminalReplay(current.exerciseId(), current.jobId());
        if (!alreadyCancelled) {
            interruptCluster(current.jobId());
        }
    }

    private void stopActiveJob(String key, JobInfo current, Instant now) {
        cancellationMap.set(current.jobId(), Boolean.TRUE, JOB_TTL_SECONDS, TimeUnit.SECONDS);
        markStoppedTranscript(current, stoppedMessage(current, now));
        retainFileChangesForTerminalReplay(current.exerciseId(), current.jobId());
        jobMap.remove(key, current);
        releaseBudgetReservation(current.budgetReservationId());
        interruptCluster(current.jobId());
    }

    private void releaseBudgetReservation(@Nullable String budgetReservationId) {
        if (generationBudgetService != null) {
            generationBudgetService.releaseReservation(budgetReservationId);
        }
    }

    @Nullable
    private Instant staleBefore(Instant now) {
        return staleJobTimeout == null || staleJobTimeout.isZero() || staleJobTimeout.isNegative() ? null : now.minus(staleJobTimeout);
    }

    private boolean shouldClearAsStale(JobInfo job, @Nullable Instant staleBefore) {
        return staleBefore != null && !job.lastHeartbeatOrStartedAt().isAfter(staleBefore);
    }

    private String stoppedMessage(JobInfo job, Instant now) {
        if (job.deadlineAt() != null && !job.deadlineAt().isAfter(now)) {
            return "Generation stopped because it exceeded the configured time limit. Nothing was changed.";
        }
        return "Generation stopped because the owning node stopped sending heartbeats. Review the exercise and repositories before use if this happened while saving.";
    }

    private void markStoppedTranscript(JobInfo job, String message) {
        String key = key(job.exerciseId());
        JobTranscript transcript = transcriptMap.get(key);
        if (transcript != null && transcript.jobId().equals(job.jobId()) && !transcript.done()) {
            List<ExerciseGenerationEventDTO> events = new ArrayList<>(transcript.events());
            ExerciseGenerationEventDTO terminalEvent = job.cancellable() ? ExerciseGenerationEventDTO.of(ExerciseGenerationEventDTO.Type.ERROR, message)
                    : ExerciseGenerationEventDTO.done(message, ExerciseGenerationEventDTO.CompletionStatus.PARTIAL, null, true);
            events.add(terminalEvent);
            while (events.size() > MAX_RETAINED_EVENTS) {
                events.remove(1);
            }
            transcriptMap.set(key, new JobTranscript(transcript.jobId(), transcript.userLogin(), transcript.exerciseId(), transcript.mode(), events, true), TRANSCRIPT_TTL_SECONDS,
                    TimeUnit.SECONDS);
        }
    }

    private void retainFileChangesForTerminalReplay(long exerciseId, String jobId) {
        JobFileChangeIndex fileChangeIndex = fileChangeMap.get(key(exerciseId));
        if (fileChangeIndex != null && fileChangeIndex.jobId().equals(jobId)) {
            fileChangeMap.setTtl(key(exerciseId), TRANSCRIPT_TTL_SECONDS, TimeUnit.SECONDS);
        }
    }

    private static String key(long exerciseId) {
        return String.valueOf(exerciseId);
    }

    private Instant deadlineAt(Instant startedAt) {
        return startedAt.plus(maxJobDuration);
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

    public record JobTranscript(String jobId, String userLogin, long exerciseId, GenerationMode mode, List<ExerciseGenerationEventDTO> events, boolean done)
            implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;
    }

    public record JobFileChangeIndex(String jobId, String userLogin, List<ExerciseGenerationFileChangeDTO> changes) implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;
    }
}
