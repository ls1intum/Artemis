package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;

import jakarta.annotation.PostConstruct;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import com.hazelcast.config.MapConfig;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.admin.domain.LLMServiceType;
import de.tum.cit.aet.artemis.admin.service.LLMTokenUsageService;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.hyperion.config.HyperionExerciseGenerationEnabled;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationEventDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationFileSnapshotDTO;
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

    private static final String TRANSCRIPT_MAP_NAME = "hyperion-exercise-generation-transcripts";

    /**
     * The per-file snapshot store: one {@link ExerciseGenerationFileSnapshotDTO} per {@code <exerciseId>::<path>} key (latest-per-file), so a write updates exactly that file's
     * key instead of re-serializing the whole retained set. Kept out of the replay transcript so a reloading client can rehydrate the live editor preview without bloating it.
     */
    static final String SNAPSHOT_MAP_NAME = "hyperion-exercise-generation-file-snapshots";

    /** The small ordered index (one {@link JobFileSnapshotIndex} per exercise) that owns the write order, the per-file cap, and the run-ownership guard for the snapshot store. */
    static final String SNAPSHOT_INDEX_MAP_NAME = "hyperion-exercise-generation-file-snapshot-index";

    private static final String ENTITY_NAME = "hyperionExerciseGeneration";

    /** Pipeline id under which a generation run's model-call token usage is recorded. */
    private static final String GENERATION_PIPELINE_ID = "HYPERION_EXERCISE_GENERATION";

    private static final int JOB_TTL_SECONDS = 7200;

    /** How long a finished run's transcript stays retrievable so a reloading client can replay it after the slot is gone. */
    private static final int TRANSCRIPT_TTL_SECONDS = 900;

    /** Cap on retained events per run so a chatty agent cannot grow the distributed map without bound; the oldest events are dropped first. */
    private static final int MAX_RETAINED_EVENTS = 500;

    /** Cap on retained file snapshots per run (latest-per-file), so many distinct files cannot grow the distributed map without bound; the eldest file is dropped first. */
    static final int MAX_RETAINED_SNAPSHOT_FILES = 300;

    private final HazelcastInstance hazelcastInstance;

    // Run launched via an event so this service does not depend on the task service, which would close a construction cycle.
    private final ApplicationEventPublisher eventPublisher;

    private final LLMTokenUsageService llmTokenUsageService;

    private IMap<String, JobInfo> jobMap;

    private IMap<String, Boolean> cancellationMap;

    private IMap<String, JobTranscript> transcriptMap;

    private IMap<String, ExerciseGenerationFileSnapshotDTO> snapshotMap;

    private IMap<String, JobFileSnapshotIndex> snapshotIndexMap;

    // Node-local interrupts held in-process because the hook closes over a live sandbox reference that exists only on the node running the job; other nodes rely on the Hazelcast
    // flag.
    private final ConcurrentMap<String, Runnable> cancelHooks = new ConcurrentHashMap<>();

    public GenerationJobService(@Qualifier("hazelcastInstance") HazelcastInstance hazelcastInstance, ApplicationEventPublisher eventPublisher,
            LLMTokenUsageService llmTokenUsageService) {
        this.hazelcastInstance = hazelcastInstance;
        this.eventPublisher = eventPublisher;
        this.llmTokenUsageService = llmTokenUsageService;
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

    /**
     * Initialises the Hazelcast-backed job, cancellation, transcript and snapshot maps with their TTL safety nets.
     */
    @PostConstruct
    public void init() {
        MapConfig jobMapConfig = hazelcastInstance.getConfig().getMapConfig(JOB_MAP_NAME);
        jobMapConfig.setTimeToLiveSeconds(JOB_TTL_SECONDS);
        jobMap = hazelcastInstance.getMap(JOB_MAP_NAME);
        MapConfig cancelMapConfig = hazelcastInstance.getConfig().getMapConfig(CANCEL_MAP_NAME);
        cancelMapConfig.setTimeToLiveSeconds(JOB_TTL_SECONDS);
        cancellationMap = hazelcastInstance.getMap(CANCEL_MAP_NAME);
        // The transcript outlives the slot (not removed on clearJob) so a client reloading as the run finishes can still fetch the terminal outcome; a TTL bounds it.
        MapConfig transcriptMapConfig = hazelcastInstance.getConfig().getMapConfig(TRANSCRIPT_MAP_NAME);
        transcriptMapConfig.setTimeToLiveSeconds(TRANSCRIPT_TTL_SECONDS);
        transcriptMap = hazelcastInstance.getMap(TRANSCRIPT_MAP_NAME);
        // The per-file snapshot store and its ordered index are actively deleted at clearJob (see clearJob), so their TTL is only a crash safety net (a node dying without
        // clearing). It matches the job-slot TTL so a long-running generation's live preview is never expired mid-run — a shorter TTL would silently drop the retained files while
        // the run is still writing them.
        MapConfig snapshotMapConfig = hazelcastInstance.getConfig().getMapConfig(SNAPSHOT_MAP_NAME);
        snapshotMapConfig.setTimeToLiveSeconds(JOB_TTL_SECONDS);
        snapshotMap = hazelcastInstance.getMap(SNAPSHOT_MAP_NAME);
        MapConfig snapshotIndexMapConfig = hazelcastInstance.getConfig().getMapConfig(SNAPSHOT_INDEX_MAP_NAME);
        snapshotIndexMapConfig.setTimeToLiveSeconds(JOB_TTL_SECONDS);
        snapshotIndexMap = hazelcastInstance.getMap(SNAPSHOT_INDEX_MAP_NAME);
    }

    /**
     * Starts a new whole-exercise generation job in an explicit mode, rejecting the request if one is already running for the exercise.
     *
     * @param user       the requesting instructor
     * @param exercise   the target exercise
     * @param userPrompt the generation brief or the feedback to address
     * @param mode       the explicit run intent (generate vs. adapt), carried on the job model so the engine can branch its seed and prompt
     * @return the started job id
     */
    public String startJob(User user, ProgrammingExercise exercise, String userPrompt, GenerationMode mode) {
        String jobId = UUID.randomUUID().toString();
        String key = key(exercise.getId());
        JobInfo newJob = new JobInfo(jobId, user.getLogin(), exercise.getId(), Instant.now());
        JobInfo existing = jobMap.putIfAbsent(key, newJob);
        if (existing != null) {
            throw new ConflictException("Exercise generation is already running for this exercise", ENTITY_NAME, "exerciseGenerationRunning");
        }
        // Fresh transcript and snapshot store for this run (overwrites any previous run's retained state for this exercise). With per-file keying a previous run's snapshots would
        // otherwise linger under their own keys, so drop them explicitly before installing the empty index.
        JobTranscript transcript = new JobTranscript(jobId, user.getLogin(), exercise.getId(), mode, new ArrayList<>(), false);
        transcriptMap.put(key, transcript);
        removeSnapshotFiles(exercise.getId(), snapshotIndexMap.get(key));
        JobFileSnapshotIndex snapshotIndex = new JobFileSnapshotIndex(jobId, user.getLogin(), new ArrayList<>());
        snapshotIndexMap.put(key, snapshotIndex);
        try {
            eventPublisher.publishEvent(new GenerationStartedEvent(jobId, user, exercise, userPrompt, mode));
        }
        catch (RejectedExecutionException e) {
            // The generation executor is saturated (AbortPolicy). The @Async listener never ran, so no terminal event will ever fire — roll back the claimed slot and its retained
            // state (value-guarded, so a later run for this exercise is never clobbered) instead of leaving the exercise wedged as "running" for the full TTL, and surface a busy
            // error the instructor can act on. Note: TaskRejectedException (thrown by ThreadPoolTaskExecutor) is a RejectedExecutionException subclass, so it is caught here too.
            jobMap.remove(key, newJob);
            transcriptMap.remove(key, transcript);
            // Nothing was written to the per-file store yet (the run never started), so removing the just-installed index is enough to leave no retained state behind.
            snapshotIndexMap.remove(key, snapshotIndex);
            log.warn("Exercise generation executor rejected job {} for exercise {}; released the slot", jobId, exercise.getId());
            throw new ConflictException("The system is currently busy with too many exercise generations. Please try again in a few minutes.", ENTITY_NAME,
                    "exerciseGenerationCapacityExceeded");
        }
        return jobId;
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
        transcriptMap.computeIfPresent(key(exerciseId), (k, transcript) -> {
            if (!transcript.jobId().equals(jobId)) {
                return transcript;
            }
            List<ExerciseGenerationEventDTO> events = new ArrayList<>(transcript.events());
            events.add(event);
            // Keep events[0] (the STARTED head, needed for a faithful replay) and drop from the front of the remainder when over the cap.
            while (events.size() > MAX_RETAINED_EVENTS) {
                events.remove(1);
            }
            return new JobTranscript(transcript.jobId(), transcript.userLogin(), transcript.exerciseId(), transcript.mode(), events, terminal || transcript.done());
        });
    }

    /**
     * Records the latest whole-file snapshot for a file written by the running job, keeping only the newest snapshot per path (latest-per-file) and bounding the number of retained
     * files. Kept out of the replay transcript so a chatty write stream never bloats the transcript; a reloading client rehydrates the editor preview from here instead.
     *
     * @param exerciseId the exercise id (the snapshot key)
     * @param jobId      the job id; the snapshot is dropped if it does not match the retained store (a stale/older run)
     * @param snapshot   the whole-file snapshot to retain
     */
    public void recordSnapshot(long exerciseId, String jobId, ExerciseGenerationFileSnapshotDTO snapshot) {
        String exerciseKey = key(exerciseId);
        JobFileSnapshotIndex index = snapshotIndexMap.get(exerciseKey);
        if (index == null || !index.jobId().equals(jobId)) {
            // No snapshot store for this run (never started, already cleared, or a stale/older run whose index was overwritten): drop the snapshot.
            return;
        }
        // O(1) per write: store only this one file's snapshot under its own key, never re-serializing the other retained files. A repeat write to the same path overwrites its
        // single key in place (latest-per-file), leaving the write order and the bounded index untouched — the whole point of the per-file keying.
        snapshotMap.set(fileKey(exerciseId, snapshot.path()), snapshot);
        if (index.orderedPaths().contains(snapshot.path())) {
            return;
        }
        // A genuinely new path: append it to the small ordered index and, when over the cap, evict the eldest file (dropping its own per-file key too so the store stays bounded).
        // The single writer per exercise (single-flight) makes this read-then-set race-free.
        List<String> orderedPaths = new ArrayList<>(index.orderedPaths());
        orderedPaths.add(snapshot.path());
        while (orderedPaths.size() > MAX_RETAINED_SNAPSHOT_FILES) {
            String evicted = orderedPaths.removeFirst();
            snapshotMap.delete(fileKey(exerciseId, evicted));
        }
        snapshotIndexMap.set(exerciseKey, new JobFileSnapshotIndex(index.jobId(), index.userLogin(), orderedPaths));
    }

    /**
     * Returns the current or most-recent run's transcript for the exercise, for reconnection/replay, if it belongs to the requesting user.
     *
     * @param user     the requesting user
     * @param exercise the exercise
     * @return the reconnection view (with a {@code running} flag derived from the live slot), or empty if none is retained for this user
     */
    public Optional<ExerciseGenerationStatusDTO> getStatus(User user, ProgrammingExercise exercise) {
        JobTranscript transcript = transcriptMap.get(key(exercise.getId()));
        if (transcript == null || !transcript.userLogin().equals(user.getLogin())) {
            return Optional.empty();
        }
        JobInfo active = jobMap.get(key(exercise.getId()));
        boolean running = active != null && active.jobId().equals(transcript.jobId()) && !transcript.done();
        return Optional
                .of(new ExerciseGenerationStatusDTO(transcript.jobId(), running, transcript.mode(), transcript.events(), latestSnapshotsFor(exercise.getId(), transcript.jobId())));
    }

    /**
     * Returns the latest snapshot per file for the given run, in write order, or an empty list if none are retained or they belong to a different run.
     */
    private List<ExerciseGenerationFileSnapshotDTO> latestSnapshotsFor(long exerciseId, String jobId) {
        JobFileSnapshotIndex index = snapshotIndexMap.get(key(exerciseId));
        if (index == null || !index.jobId().equals(jobId)) {
            return List.of();
        }
        Set<String> keys = new LinkedHashSet<>();
        for (String path : index.orderedPaths()) {
            keys.add(fileKey(exerciseId, path));
        }
        // One batched read of just the retained files (bounded by the cap), then re-projected into write order.
        Map<String, ExerciseGenerationFileSnapshotDTO> byKey = snapshotMap.getAll(keys);
        List<ExerciseGenerationFileSnapshotDTO> snapshots = new ArrayList<>();
        for (String path : index.orderedPaths()) {
            ExerciseGenerationFileSnapshotDTO snapshot = byKey.get(fileKey(exerciseId, path));
            if (snapshot != null) {
                snapshots.add(snapshot);
            }
        }
        return List.copyOf(snapshots);
    }

    /**
     * Requests cooperative cancellation of the running job — but only by the instructor who started it. The owner check matters because the jobId is not a secret (returned to the
     * client and embedded in the websocket topic path), so without it any same-course editor who observes the id could abort a colleague's run. A non-owner gets {@code false}
     * (404).
     *
     * @param exerciseId the exercise id
     * @param jobId      the job id to cancel
     * @param user       the requesting user; must be the instructor who started the job
     * @return {@code true} if a matching active job owned by {@code user} was found and marked for cancellation
     */
    public boolean requestCancellation(long exerciseId, String jobId, User user) {
        JobInfo job = jobMap.get(key(exerciseId));
        if (job == null || !job.jobId().equals(jobId)) {
            return false;
        }
        JobTranscript transcript = transcriptMap.get(key(exerciseId));
        if (transcript == null || !transcript.jobId().equals(jobId) || !transcript.userLogin().equals(user.getLogin())) {
            return false;
        }
        cancellationMap.put(jobId, Boolean.TRUE);
        // Run the node-local interrupt once (remove-and-run) so a long in-flight build is aborted promptly.
        Runnable hook = cancelHooks.remove(jobId);
        if (hook != null) {
            try {
                hook.run();
            }
            catch (RuntimeException e) {
                log.warn("Cancel hook for job {} failed: {}", jobId, e.getMessage());
            }
        }
        return true;
    }

    /**
     * Registers a node-local interrupt (e.g. destroy the sandbox session) invoked when this job is cancelled, so a cancellation arriving during a long build aborts it promptly.
     *
     * @param jobId the running job id
     * @param hook  the interrupt to run on cancellation
     */
    public void registerCancelHook(String jobId, Runnable hook) {
        cancelHooks.put(jobId, hook);
    }

    /**
     * Removes a job's cancel hook once the run has finished (the session it would interrupt is gone).
     *
     * @param jobId the finished job id
     */
    public void deregisterCancelHook(String jobId) {
        cancelHooks.remove(jobId);
    }

    /**
     * @param jobId the job id
     * @return whether cancellation has been requested for the job
     */
    public boolean isCancelled(String jobId) {
        return Boolean.TRUE.equals(cancellationMap.get(jobId));
    }

    /**
     * Clears the slot and any cancellation flag once a job has finished.
     *
     * @param exerciseId the exercise id
     * @param jobId      the finished job id
     */
    public void clearJob(long exerciseId, String jobId) {
        String key = key(exerciseId);
        JobInfo job = jobMap.get(key);
        if (job != null && job.jobId().equals(jobId)) {
            jobMap.remove(key, job);
        }
        cancellationMap.remove(jobId);
        // Keep the transcript (TTL-bounded) for replay but mark it done, so a reconnecting client knows the run finished even if the terminal event was never recorded.
        transcriptMap.computeIfPresent(key,
                (k, transcript) -> transcript.jobId().equals(jobId) && !transcript.done()
                        ? new JobTranscript(transcript.jobId(), transcript.userLogin(), transcript.exerciseId(), transcript.mode(), transcript.events(), true)
                        : transcript);
        // Delete every retained per-file snapshot key and the index (guarded by jobId so a newer run's store is never wiped by an older job's cleanup). Unlike the single-value
        // store this had before, per-file keys multiply per run, so leaving them to the TTL alone would accumulate; the live preview is a during-run affordance, and a client
        // reconnecting after the run reads the persisted repositories, not the snapshots.
        JobFileSnapshotIndex snapshotIndex = snapshotIndexMap.get(key);
        if (snapshotIndex != null && snapshotIndex.jobId().equals(jobId)) {
            removeSnapshotFiles(exerciseId, snapshotIndex);
            snapshotIndexMap.remove(key, snapshotIndex);
        }
    }

    /** Deletes every retained per-file snapshot key listed by {@code index}; a {@code null} index (nothing retained) is a no-op. */
    private void removeSnapshotFiles(long exerciseId, @Nullable JobFileSnapshotIndex index) {
        if (index == null) {
            return;
        }
        for (String path : index.orderedPaths()) {
            snapshotMap.delete(fileKey(exerciseId, path));
        }
    }

    private static String key(long exerciseId) {
        return String.valueOf(exerciseId);
    }

    /** The per-file snapshot map key for a file: the exercise id and the workspace-relative path, so each file occupies exactly one distributed-map entry. */
    static String fileKey(long exerciseId, String path) {
        return exerciseId + "::" + path;
    }

    /**
     * Metadata for an active whole-exercise generation job (claimed slot owner and claim time).
     */
    public record JobInfo(String jobId, String userLogin, long exerciseId, Instant startedAt) implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;
    }

    /**
     * The retained, replayable transcript of a generation run.
     *
     * @param jobId      the job id
     * @param userLogin  the owner's login (transcripts are private to the instructor who started the run)
     * @param exerciseId the exercise id
     * @param mode       the explicit run intent (generate vs. adapt), carried so a reconnecting client can restore the header label and revert affordance
     * @param events     the events produced so far, oldest first (bounded)
     * @param done       whether the run has finished (so a reconnecting client knows whether to keep listening)
     */
    public record JobTranscript(String jobId, String userLogin, long exerciseId, GenerationMode mode, List<ExerciseGenerationEventDTO> events, boolean done)
            implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;
    }

    /**
     * The small ordered index of a run's retained file snapshots. It holds only the write-ordered path list (bounded to {@link #MAX_RETAINED_SNAPSHOT_FILES}) plus the ownership
     * guard; the whole-file snapshots themselves live one-per-key in the per-file snapshot map ({@code <exerciseId>::<path>}). Keeping the bulky content out of this value is what
     * makes a write O(1) — it updates one file's key and, only for a genuinely new path, appends to this small list — instead of re-serializing the whole retained set on every
     * write. Kept separate from {@link JobTranscript} so the write stream never bloats the replay transcript.
     *
     * @param jobId        the job id (snapshots from a superseded run are ignored)
     * @param userLogin    the owner's login (snapshots are private to the instructor who started the run)
     * @param orderedPaths the retained file paths in write (insertion) order; bounded to {@link #MAX_RETAINED_SNAPSHOT_FILES}, eldest evicted first
     */
    public record JobFileSnapshotIndex(String jobId, String userLogin, List<String> orderedPaths) implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;
    }
}
