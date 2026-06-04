package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import com.hazelcast.config.MapConfig;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.hyperion.config.HyperionEnabled;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationEventDTO;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

/**
 * Single-flight slot and cancellation registry for whole-exercise agentic generation jobs.
 * <p>
 * At most one generation runs per exercise at a time (claimed atomically in a Hazelcast map with a TTL safety net), and a separate cancellation set lets the REST layer request
 * a cooperative abort that the running loop polls between turns. This per-exercise single-flight is the primary concurrency bound.
 */
@Service
@Lazy
@Conditional(HyperionEnabled.class)
public class ExerciseGenerationJobService {

    private static final Logger log = LoggerFactory.getLogger(ExerciseGenerationJobService.class);

    private static final String JOB_MAP_NAME = "hyperion-exercise-generation-jobs";

    private static final String CANCEL_MAP_NAME = "hyperion-exercise-generation-cancellations";

    private static final String TRANSCRIPT_MAP_NAME = "hyperion-exercise-generation-transcripts";

    private static final String ENTITY_NAME = "hyperionExerciseGeneration";

    private static final int JOB_TTL_SECONDS = 7200;

    /** How long a finished run's transcript stays retrievable so a reloading client can replay it (including the terminal event) after the slot itself is gone. */
    private static final int TRANSCRIPT_TTL_SECONDS = 900;

    /** Cap on retained events per run so a chatty agent cannot grow the distributed map without bound; the oldest events are dropped first. */
    private static final int MAX_RETAINED_EVENTS = 500;

    private final HazelcastInstance hazelcastInstance;

    // The run is launched by publishing an event (rather than calling the task service directly), so the job service does not depend on the task service — which would otherwise
    // close a construction cycle (task service needs the job service for cancellation/transcript).
    private final ApplicationEventPublisher eventPublisher;

    private IMap<String, JobInfo> jobMap;

    private IMap<String, Boolean> cancellationMap;

    private IMap<String, JobTranscript> transcriptMap;

    // Node-local interrupts for in-flight jobs (e.g. destroy the sandbox session). Held in-process because the hook closes over a live sandbox reference that only exists on the
    // node running the job; on other nodes cancellation falls back to the cross-node Hazelcast flag the loop polls between turns.
    private final ConcurrentMap<String, Runnable> cancelHooks = new ConcurrentHashMap<>();

    public ExerciseGenerationJobService(@Qualifier("hazelcastInstance") HazelcastInstance hazelcastInstance, ApplicationEventPublisher eventPublisher) {
        this.hazelcastInstance = hazelcastInstance;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Initialises the Hazelcast-backed job and cancellation maps with the configured TTL safety net.
     */
    @PostConstruct
    public void init() {
        MapConfig jobMapConfig = hazelcastInstance.getConfig().getMapConfig(JOB_MAP_NAME);
        jobMapConfig.setTimeToLiveSeconds(JOB_TTL_SECONDS);
        jobMap = hazelcastInstance.getMap(JOB_MAP_NAME);
        MapConfig cancelMapConfig = hazelcastInstance.getConfig().getMapConfig(CANCEL_MAP_NAME);
        cancelMapConfig.setTimeToLiveSeconds(JOB_TTL_SECONDS);
        cancellationMap = hazelcastInstance.getMap(CANCEL_MAP_NAME);
        // The transcript outlives the slot (it is NOT removed on clearJob) so a client that reloads right as the run finishes can still fetch the terminal outcome; a TTL bounds
        // it.
        MapConfig transcriptMapConfig = hazelcastInstance.getConfig().getMapConfig(TRANSCRIPT_MAP_NAME);
        transcriptMapConfig.setTimeToLiveSeconds(TRANSCRIPT_TTL_SECONDS);
        transcriptMap = hazelcastInstance.getMap(TRANSCRIPT_MAP_NAME);
    }

    /**
     * Starts a new whole-exercise generation job, rejecting the request if one is already running for the exercise.
     *
     * @param user       the requesting instructor
     * @param exercise   the target exercise
     * @param userPrompt the generation brief or the feedback to address
     * @return the started job id
     */
    public String startJob(User user, ProgrammingExercise exercise, String userPrompt) {
        String jobId = UUID.randomUUID().toString();
        JobInfo newJob = new JobInfo(jobId, user.getLogin(), exercise.getId(), Instant.now());
        JobInfo existing = jobMap.putIfAbsent(key(exercise.getId()), newJob);
        if (existing != null) {
            throw new ConflictException("Exercise generation is already running for this exercise", ENTITY_NAME, "exerciseGenerationRunning");
        }
        // Start a fresh transcript for this run so progress can be replayed on reconnect (overwrites any previous run's retained transcript for this exercise).
        transcriptMap.put(key(exercise.getId()), new JobTranscript(jobId, user.getLogin(), exercise.getId(), new ArrayList<>(), false));
        // Hand off to the async task via an event so this service stays free of a dependency on the task service (see eventPublisher above).
        eventPublisher.publishEvent(new ExerciseGenerationStartedEvent(jobId, user, exercise, userPrompt));
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
            // Always keep events[0] (the STARTED head, needed for a faithful replay) and drop from the front of the remainder when over the cap.
            while (events.size() > MAX_RETAINED_EVENTS) {
                events.remove(1);
            }
            return new JobTranscript(transcript.jobId(), transcript.userLogin(), transcript.exerciseId(), events, terminal || transcript.done());
        });
    }

    /**
     * Returns the current or most-recent run's transcript for the exercise, for reconnection/replay, if it belongs to the requesting user.
     *
     * @param user     the requesting user
     * @param exercise the exercise
     * @return the transcript (with a {@code running} flag derived from the live slot), or empty if none is retained for this user
     */
    public Optional<JobStatus> getStatus(User user, ProgrammingExercise exercise) {
        JobTranscript transcript = transcriptMap.get(key(exercise.getId()));
        if (transcript == null || !transcript.userLogin().equals(user.getLogin())) {
            return Optional.empty();
        }
        JobInfo active = jobMap.get(key(exercise.getId()));
        boolean running = active != null && active.jobId().equals(transcript.jobId()) && !transcript.done();
        return Optional.of(new JobStatus(transcript.jobId(), running, transcript.events()));
    }

    /**
     * Requests cooperative cancellation of the running job for an exercise — but ONLY by the instructor who started it.
     * <p>
     * The owner check is symmetric to {@link #getStatus(User, ProgrammingExercise)}: the jobId is not a secret (it is returned to the client in the start response and embedded in
     * the websocket topic path {@code …/jobs/{jobId}}), so without this check any same-course editor who observes the id could abort a colleague's expensive multi-minute run.
     * Returning {@code false} for a non-owner maps to a 404, which does not even confirm the job exists.
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
            // Not the owner (or no transcript for this job): refuse, indistinguishably from "no such job".
            return false;
        }
        cancellationMap.put(jobId, Boolean.TRUE);
        // Run the node-local interrupt once (remove-and-run) so a long in-flight build is aborted promptly instead of only at the next between-turn poll.
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
        // Free the single-flight slot but keep the transcript (TTL-bounded) for replay; mark it done so a reconnecting client knows the run finished even if the terminal event
        // was never recorded (e.g. an unexpected error path).
        transcriptMap.computeIfPresent(key,
                (k, transcript) -> transcript.jobId().equals(jobId) && !transcript.done()
                        ? new JobTranscript(transcript.jobId(), transcript.userLogin(), transcript.exerciseId(), transcript.events(), true)
                        : transcript);
    }

    private static String key(long exerciseId) {
        return String.valueOf(exerciseId);
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
     * @param events     the events produced so far, oldest first (bounded)
     * @param done       whether the run has finished (so a reconnecting client knows whether to keep listening)
     */
    public record JobTranscript(String jobId, String userLogin, long exerciseId, List<ExerciseGenerationEventDTO> events, boolean done) implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;
    }

    /**
     * The reconnection view of a run: its id, whether it is still running, and the events so far to replay.
     */
    public record JobStatus(String jobId, boolean running, List<ExerciseGenerationEventDTO> events) {
    }
}
