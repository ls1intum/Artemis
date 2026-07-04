package de.tum.cit.aet.artemis.hyperion.service.variants;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import jakarta.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import com.hazelcast.config.MapConfig;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.hyperion.config.HyperionEnabled;
import de.tum.cit.aet.artemis.hyperion.dto.VariantGenerationEventDTO;
import de.tum.cit.aet.artemis.hyperion.dto.VariantGenerationRequestDTO;
import de.tum.cit.aet.artemis.hyperion.service.websocket.HyperionWebsocketService;

/**
 * Hazelcast-backed job store for variant generation — mirrors {@code HyperionCodeGenerationJobService}
 * (plan Section 5.2), generalized: the job record is a rich {@link VariantJob} (phase, ChangePlan, step
 * outputs) and finished jobs are RETAINED under TTL for the navbar tray instead of being removed
 * (Section 5.2, "Job retention for the tray"). A separate short-TTL lock map provides the per-exercise
 * dedup — only the lock is released on completion, the job record stays readable.
 *
 * This service is the single writer to job records AND the single publisher of the per-job websocket
 * topic, so map state and client-visible events cannot diverge.
 */
@Service
@Lazy
@Conditional(HyperionEnabled.class)
public class ExerciseVariantJobService {

    private static final String JOB_MAP_NAME = "hyperion-exercise-variant-jobs";

    private static final String LOCK_MAP_NAME = "hyperion-exercise-variant-job-locks";

    private static final String ENTITY_NAME = "exerciseVariantGeneration";

    private static final String TOPIC_SUFFIX_PREFIX = "variant-generation/jobs/";

    // Finished jobs stay listable/deep-linkable in the tray for a day (plan Section 5.2).
    private static final int JOB_TTL_SECONDS = 24 * 3600;

    // Safety bound for the dedup lock: longer than any plausible job runtime so a crashed node cannot block
    // an exercise forever, short enough that a stale lock resolves the same day.
    private static final int LOCK_TTL_SECONDS = 2 * 3600;

    private final HazelcastInstance hazelcastInstance;

    private final HyperionWebsocketService websocketService;

    private IMap<String, VariantJob> jobMap;

    private IMap<Long, String> lockMap;

    public ExerciseVariantJobService(@Qualifier("hazelcastInstance") HazelcastInstance hazelcastInstance, HyperionWebsocketService websocketService) {
        this.hazelcastInstance = hazelcastInstance;
        this.websocketService = websocketService;
    }

    /**
     * Initializes the Hazelcast-backed job and dedup-lock maps with their TTLs.
     */
    @PostConstruct
    public void init() {
        MapConfig jobMapConfig = hazelcastInstance.getConfig().getMapConfig(JOB_MAP_NAME);
        jobMapConfig.setTimeToLiveSeconds(JOB_TTL_SECONDS);
        jobMap = hazelcastInstance.getMap(JOB_MAP_NAME);

        MapConfig lockMapConfig = hazelcastInstance.getConfig().getMapConfig(LOCK_MAP_NAME);
        lockMapConfig.setTimeToLiveSeconds(LOCK_TTL_SECONDS);
        lockMap = hazelcastInstance.getMap(LOCK_MAP_NAME);
    }

    /**
     * Claims the per-exercise slot and creates the job record (plan Sections 5.1/5.2).
     *
     * @param user     initiating user
     * @param exercise source exercise
     * @param request  validated wizard request
     * @return the claimed job
     * @throws ConflictException when a variant generation is already running for the exercise
     */
    public VariantJob startJob(User user, Exercise exercise, VariantGenerationRequestDTO request) {
        String jobId = UUID.randomUUID().toString();
        String existing = lockMap.putIfAbsent(exercise.getId(), jobId);
        if (existing != null) {
            throw new ConflictException("Variant generation already running for this exercise", ENTITY_NAME, "variantGenerationRunning");
        }
        VariantJob job = new VariantJob();
        job.setJobId(jobId);
        job.setSourceExerciseId(exercise.getId());
        job.setSourceExerciseTitle(exercise.getTitle());
        job.setExerciseType(exercise.getExerciseType());
        job.setInitiatorLogin(user.getLogin());
        job.setPhase(VariantJobPhase.ANALYZING);
        job.setRequest(request);
        job.setStartedAt(Instant.now());
        jobMap.put(jobId, job);
        return job;
    }

    /**
     * Releases the per-exercise dedup lock if it is still held by the given job. The job record itself is
     * retained for the tray (plan Section 5.2, "Job retention").
     *
     * @param exerciseId exercise whose slot should be released
     * @param jobId      the job that held the slot
     */
    public void releaseLock(long exerciseId, String jobId) {
        // Removal is best-effort: the entry may have been evicted/replaced; remove(key, value) no-ops then.
        lockMap.remove(exerciseId, jobId);
    }

    /**
     * Returns the RUNNING job for the exercise if it belongs to the given user (per-user scoping re-checked
     * server-side, plan Section 5.1) — backs GET .../generate-variant/active for wizard reconnect.
     *
     * @param user       requesting user
     * @param exerciseId source exercise id
     * @return the running job, or empty
     */
    public Optional<VariantJob> getActiveJob(User user, long exerciseId) {
        String jobId = lockMap.get(exerciseId);
        if (jobId == null) {
            return Optional.empty();
        }
        VariantJob job = jobMap.get(jobId);
        if (job == null || !job.getInitiatorLogin().equals(user.getLogin()) || job.getPhase().isTerminal()) {
            return Optional.empty();
        }
        return Optional.of(job);
    }

    /**
     * Returns ALL jobs (running + retained-finished) of the user for the navbar tray (plan Section 5.4):
     * running first, then finished by finish time descending.
     *
     * @param login the user's login
     * @return the user's jobs
     */
    public List<VariantJob> getJobsOfUser(String login) {
        // Full-values scan is fine here: the map only holds per-user jobs of the last 24h (TTL).
        return jobMap.values().stream().filter(job -> login.equals(job.getInitiatorLogin())).sorted(Comparator.comparing((VariantJob job) -> job.getPhase().isTerminal())
                .thenComparing(job -> job.getFinishedAt() != null ? job.getFinishedAt() : job.getStartedAt(), Comparator.reverseOrder())).toList();
    }

    /**
     * Returns the job by id if it belongs to the user — backs GET /variant-jobs/{jobId} (plan Section 5.4).
     * Foreign and unknown jobs are indistinguishable (both empty) so job ids cannot be probed.
     *
     * @param jobId the job id
     * @param login the requesting user's login
     * @return the job, or empty
     */
    public Optional<VariantJob> getJob(String jobId, String login) {
        VariantJob job = jobMap.get(jobId);
        if (job == null || !job.getInitiatorLogin().equals(login)) {
            return Optional.empty();
        }
        return Optional.of(job);
    }

    /**
     * Cooperative cancel (plan Section 5.2): flips the distributed {@code cancelRequested} flag; the pipeline
     * observes it at phase transitions / between agent rounds and performs the cleanup, regardless of which
     * node runs the job.
     *
     * @param jobId the job to cancel
     * @param login the requesting user's login (must be the initiator)
     * @throws ConflictException when the job already reached FINALIZING or a terminal phase
     */
    public void requestCancel(String jobId, String login) {
        VariantJob job = getJob(jobId, login).orElseThrow(() -> new ConflictException("Unknown variant generation job", ENTITY_NAME, "unknownJob"));
        if (!job.getPhase().isCancellable()) {
            throw new ConflictException("Job can no longer be cancelled — the variant already exists", ENTITY_NAME, "jobNotCancellable");
        }
        mutate(jobId, mutableJob -> mutableJob.setCancelRequested(true));
    }

    /**
     * Reads the distributed cancel flag — called by the pipeline at every phase boundary and between agent
     * rounds (plan Section 5.2).
     *
     * @param jobId the job id
     * @return true when cancellation was requested
     */
    public boolean isCancelRequested(String jobId) {
        VariantJob job = jobMap.get(jobId);
        return job != null && job.isCancelRequested();
    }

    // --- Single-writer mutation API used by the pipeline; each method updates the record and publishes the
    // --- matching websocket event (plan Section 5.2).

    /**
     * Transitions the job to a new phase and publishes PHASE_CHANGED.
     *
     * @param jobId the job id
     * @param phase the new phase
     */
    public void updatePhase(String jobId, VariantJobPhase phase) {
        VariantJob job = mutate(jobId, mutableJob -> mutableJob.setPhase(phase));
        publish(job, VariantGenerationEventDTO.phaseChanged(phase));
    }

    /**
     * Records a repair attempt and publishes ATTEMPT (rendered as "attempt 2/3", plan Section 5.2).
     *
     * @param jobId       the job id
     * @param attempt     current attempt (1-based)
     * @param maxAttempts attempt budget
     * @param detail      type-specific sub-label, e.g. "Building solution repository"
     */
    public void recordAttempt(String jobId, int attempt, int maxAttempts, String detail) {
        VariantJob job = mutate(jobId, mutableJob -> {
            mutableJob.setAttempt(attempt);
            mutableJob.setMaxAttempts(maxAttempts);
        });
        publish(job, VariantGenerationEventDTO.attempt(job.getPhase(), attempt, maxAttempts, detail));
    }

    /**
     * Publishes a PROGRESS sub-label without changing job state ("Validating quiz questions", plan Section 5.2).
     *
     * @param jobId  the job id
     * @param detail the progress detail
     */
    public void recordProgress(String jobId, String detail) {
        VariantJob job = jobMap.get(jobId);
        if (job != null) {
            publish(job, VariantGenerationEventDTO.progress(job.getPhase(), detail));
        }
    }

    /**
     * Stores a phase's step output on the job and publishes STEP_OUTPUT (expandable panels, plan Section 2.4).
     *
     * @param jobId  the job id
     * @param phase  the phase the output belongs to
     * @param output the output
     */
    public void recordStepOutput(String jobId, VariantJobPhase phase, StepOutput output) {
        VariantJob job = mutate(jobId, mutableJob -> mutableJob.getStepOutputs().put(phase, output));
        publish(job, VariantGenerationEventDTO.stepOutput(phase, output.summary()));
    }

    /**
     * Stores the plan produced in PLANNING on the job (no event of its own — the plan is surfaced as the
     * PLANNING step output).
     *
     * @param jobId the job id
     * @param plan  the change plan
     */
    public void recordChangePlan(String jobId, ChangePlan plan) {
        mutate(jobId, mutableJob -> mutableJob.setChangePlan(plan));
    }

    /**
     * Stores the provisioned variant exercise id on the job (set during PROVISIONING; used by cleanup, the
     * tray deep link, and the DONE event).
     *
     * @param jobId             the job id
     * @param variantExerciseId the provisioned exercise id
     */
    public void recordVariantExerciseId(String jobId, Long variantExerciseId) {
        mutate(jobId, mutableJob -> mutableJob.setVariantExerciseId(variantExerciseId));
    }

    /**
     * Terminal transition to COMPLETED or DRAFT_WITH_WARNINGS; publishes DONE with the variant exercise id
     * and any warnings, and releases the dedup lock (plan Section 5.2).
     *
     * @param jobId             the job id
     * @param variantExerciseId the created exercise
     * @param warnings          non-empty for DRAFT_WITH_WARNINGS
     */
    public void complete(String jobId, Long variantExerciseId, List<String> warnings) {
        VariantJobPhase terminalPhase = warnings == null || warnings.isEmpty() ? VariantJobPhase.COMPLETED : VariantJobPhase.DRAFT_WITH_WARNINGS;
        VariantJob job = mutate(jobId, mutableJob -> {
            mutableJob.setPhase(terminalPhase);
            mutableJob.setVariantExerciseId(variantExerciseId);
            if (warnings != null) {
                mutableJob.setWarnings(warnings);
            }
            mutableJob.setFinishedAt(Instant.now());
        });
        releaseLock(job.getSourceExerciseId(), jobId);
        publish(job, VariantGenerationEventDTO.done(terminalPhase, variantExerciseId, warnings));
    }

    /**
     * Terminal transition to FAILED; publishes FAILED with the failure detail and releases the dedup lock.
     * The phase the job failed in is preserved in the FAILED event's detail (plan Section 5.4 tray label).
     *
     * @param jobId  the job id
     * @param detail failure description including the phase
     */
    public void fail(String jobId, String detail) {
        VariantJob job = mutate(jobId, mutableJob -> {
            mutableJob.setPhase(VariantJobPhase.FAILED);
            mutableJob.setFinishedAt(Instant.now());
        });
        releaseLock(job.getSourceExerciseId(), jobId);
        publish(job, VariantGenerationEventDTO.failed(detail));
    }

    /**
     * Terminal transition to CANCELLED (after the pipeline finished the clone cleanup); publishes CANCELLED
     * and releases the dedup lock (plan Section 5.2).
     *
     * @param jobId the job id
     */
    public void markCancelled(String jobId) {
        VariantJob job = mutate(jobId, mutableJob -> {
            mutableJob.setPhase(VariantJobPhase.CANCELLED);
            mutableJob.setVariantExerciseId(null); // clone was deleted — no deep link (plan Section 5.4)
            mutableJob.setFinishedAt(Instant.now());
        });
        releaseLock(job.getSourceExerciseId(), jobId);
        publish(job, VariantGenerationEventDTO.cancelled());
    }

    private VariantJob mutate(String jobId, Consumer<VariantJob> mutation) {
        VariantJob job = jobMap.get(jobId);
        if (job == null) {
            throw new IllegalStateException("Variant job " + jobId + " no longer exists");
        }
        mutation.accept(job);
        jobMap.put(jobId, job);
        return job;
    }

    private void publish(VariantJob job, VariantGenerationEventDTO event) {
        websocketService.send(job.getInitiatorLogin(), TOPIC_SUFFIX_PREFIX + job.getJobId(), event);
    }
}
