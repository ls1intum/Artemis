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
 * (Section 5.2, "Job retention for the tray"). There is deliberately NO per-exercise dedup: instructors
 * may generate several variants of the same exercise simultaneously; each POST creates an independent job.
 *
 * This service is the single writer to job records AND the single publisher of the per-job websocket
 * topic, so map state and client-visible events cannot diverge.
 */
@Service
@Lazy
@Conditional(HyperionEnabled.class)
public class ExerciseVariantJobService {

    private static final String JOB_MAP_NAME = "hyperion-exercise-variant-jobs";

    private static final String ENTITY_NAME = "exerciseVariantGeneration";

    private static final String TOPIC_SUFFIX_PREFIX = "variant-generation/jobs/";

    // Finished jobs stay listable/deep-linkable in the tray for a day (plan Section 5.2).
    private static final int JOB_TTL_SECONDS = 24 * 3600;

    private final HazelcastInstance hazelcastInstance;

    private final HyperionWebsocketService websocketService;

    private IMap<String, VariantJob> jobMap;

    public ExerciseVariantJobService(@Qualifier("hazelcastInstance") HazelcastInstance hazelcastInstance, HyperionWebsocketService websocketService) {
        this.hazelcastInstance = hazelcastInstance;
        this.websocketService = websocketService;
    }

    /**
     * Initializes the Hazelcast-backed job map with its TTL.
     */
    @PostConstruct
    public void init() {
        MapConfig jobMapConfig = hazelcastInstance.getConfig().getMapConfig(JOB_MAP_NAME);
        jobMapConfig.setTimeToLiveSeconds(JOB_TTL_SECONDS);
        jobMap = hazelcastInstance.getMap(JOB_MAP_NAME);
    }

    /**
     * Creates the job record (plan Sections 5.1/5.2). Several jobs may run for the same exercise at the
     * same time — parallel variant generation is an explicit requirement, so there is no dedup here.
     *
     * @param user     initiating user
     * @param exercise source exercise
     * @param request  validated wizard request
     * @return the created job
     */
    public VariantJob startJob(User user, Exercise exercise, VariantGenerationRequestDTO request) {
        String jobId = UUID.randomUUID().toString();
        VariantJob job = new VariantJob();
        job.setJobId(jobId);
        job.setSourceExerciseId(exercise.getId());
        try {
            // Resolved via the exam's course for exam exercises; the tray deep link needs it (plan Section 5.4).
            job.setCourseId(exercise.getCourseViaExerciseGroupOrCourseMember() != null ? exercise.getCourseViaExerciseGroupOrCourseMember().getId() : null);
        }
        catch (RuntimeException ignored) {
            // Course resolution is best-effort (mirrors the codegen resource) — the tray just omits the link.
        }
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
     * Accumulates LLM token usage on the job (budget enforcement + thesis telemetry, plan Section 7).
     * No event — token totals are read from the job record.
     *
     * @param jobId  the job id
     * @param tokens tokens consumed by the completed LLM call/round
     */
    public void addTokensUsed(String jobId, long tokens) {
        if (tokens > 0) {
            mutate(jobId, mutableJob -> mutableJob.setTotalTokensUsed(mutableJob.getTotalTokensUsed() + tokens));
        }
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
     * and any warnings (plan Section 5.2).
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
        publish(job, VariantGenerationEventDTO.done(terminalPhase, variantExerciseId, warnings));
    }

    /**
     * Terminal transition to FAILED; publishes FAILED with the failure detail. The phase the job failed in
     * is preserved on the job record so the tray can label the entry "Failed (VERIFYING)" (plan Section 5.4).
     *
     * @param jobId  the job id
     * @param detail failure description including the phase
     */
    public void fail(String jobId, String detail) {
        VariantJob job = mutate(jobId, mutableJob -> {
            mutableJob.setFailedInPhase(mutableJob.getPhase());
            mutableJob.setPhase(VariantJobPhase.FAILED);
            mutableJob.setFinishedAt(Instant.now());
        });
        publish(job, VariantGenerationEventDTO.failed(detail));
    }

    /**
     * Terminal transition to CANCELLED (after the pipeline finished the clone cleanup); publishes CANCELLED
     * (plan Section 5.2).
     *
     * @param jobId the job id
     */
    public void markCancelled(String jobId) {
        VariantJob job = mutate(jobId, mutableJob -> {
            mutableJob.setPhase(VariantJobPhase.CANCELLED);
            mutableJob.setVariantExerciseId(null); // clone was deleted — no deep link (plan Section 5.4)
            mutableJob.setFinishedAt(Instant.now());
        });
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
