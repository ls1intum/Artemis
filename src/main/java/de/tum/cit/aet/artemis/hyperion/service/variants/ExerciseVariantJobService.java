package de.tum.cit.aet.artemis.hyperion.service.variants;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * Hazelcast-backed job store for variant generation — mirrors {@code HyperionCodeGenerationJobService},
 * generalized: the job record is a rich {@link VariantJob} (phase, ChangePlan, step outputs) and finished
 * jobs are RETAINED under TTL for the navbar tray instead of being removed. There is deliberately NO
 * per-exercise dedup: instructors may generate several variants of the same exercise simultaneously; each
 * POST creates an independent job.
 *
 * This service is the single writer to job records AND the single publisher of the per-job websocket
 * topic, so map state and client-visible events cannot diverge.
 */
@Service
@Lazy
@Conditional(HyperionEnabled.class)
public class ExerciseVariantJobService {

    private static final Logger log = LoggerFactory.getLogger(ExerciseVariantJobService.class);

    static final String JOB_MAP_NAME = "hyperion-exercise-variant-jobs";

    private static final String ENTITY_NAME = "exerciseVariantGeneration";

    private static final String TOPIC_SUFFIX_PREFIX = "variant-generation/jobs/";

    // Finished jobs stay listable/deep-linkable in the tray for a day.
    private static final int JOB_TTL_SECONDS = 24 * 3600;

    // A non-terminal job whose worker node advanced it (state change or agent tool call) less recently than this
    // lost that node to a restart/crash: it is marked FAILED-stale on read instead of showing as running until the
    // TTL expires. The threshold comfortably exceeds the longest gap between updates (one agent round with its
    // capped 3-minute build waits) while staying far below the 24h TTL.
    private static final Duration STALE_THRESHOLD = Duration.ofMinutes(10);

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
     * Creates the job record. Several jobs may run for the same exercise at the
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
            // Resolved via the exam's course for exam exercises; the tray deep link needs it.
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
        Instant now = Instant.now();
        job.setStartedAt(now);
        job.setLastHeartbeatAt(now);
        jobMap.put(jobId, job);
        return job;
    }

    /**
     * Returns ALL jobs (running + retained-finished) of the user for the navbar tray:
     * running first, then finished by finish time descending.
     *
     * @param login the user's login
     * @return the user's jobs
     */
    public List<VariantJob> getJobsOfUser(String login) {
        // Full-values scan is fine here: the map only holds per-user jobs of the last 24h (TTL).
        return jobMap.values().stream().filter(job -> login.equals(job.getInitiatorLogin())).map(this::reconcileStaleness)
                .sorted(Comparator.comparing((VariantJob job) -> job.getPhase().isTerminal())
                        .thenComparing(job -> job.getFinishedAt() != null ? job.getFinishedAt() : job.getStartedAt(), Comparator.reverseOrder()))
                .toList();
    }

    /**
     * Returns the job by id if it belongs to the user — backs GET /variant-jobs/{jobId}.
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
        return Optional.of(reconcileStaleness(job));
    }

    /**
     * Cooperative cancel: flips the distributed {@code cancelRequested} flag; the pipeline
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
     * rounds.
     *
     * @param jobId the job id
     * @return true when cancellation was requested
     */
    public boolean isCancelRequested(String jobId) {
        VariantJob job = jobMap.get(jobId);
        return job != null && job.isCancelRequested();
    }

    // --- Single-writer mutation API used by the pipeline; each method updates the record and publishes the
    // --- matching websocket event.

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
     * Records a repair attempt and publishes ATTEMPT (rendered as "attempt 2/3").
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
     * Publishes a PROGRESS sub-label without changing job state ("Validating quiz questions").
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
     * Appends a phase's step output to the job's per-phase history and publishes STEP_OUTPUT (expandable
     * panels). Outputs are never overwritten: a phase visited multiple times (verify/repair attempts) keeps
     * every message, oldest first, so instructors can debug earlier failures after a later success.
     *
     * @param jobId  the job id
     * @param phase  the phase the output belongs to
     * @param output the output
     */
    public void recordStepOutput(String jobId, VariantJobPhase phase, StepOutput output) {
        VariantJob job = mutate(jobId, mutableJob -> mutableJob.getStepOutputs().computeIfAbsent(phase, key -> new ArrayList<>()).add(output));
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
        mutate(jobId, mutableJob -> {
            mutableJob.setChangePlan(plan);
            // The planned title doubles as the "source → variant" display in the tray/modal — it is known long
            // before the exercise is provisioned.
            mutableJob.setVariantExerciseTitle(plan.variantTitle());
        });
    }

    /**
     * Accumulates LLM token usage on the job (budget enforcement + telemetry).
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
     * Merges one agent round's per-tool-call telemetry (name -> call count/total ms, collected by
     * {@link VariantToolset#toolCallStats()}) into the job's running totals. No event — read from the job record
     * (and logged as a summary at job end), like token usage.
     *
     * @param jobId      the job id
     * @param roundStats this round's tool-call stats, possibly empty
     */
    public void recordToolCallStats(String jobId, Map<String, VariantJob.CallStat> roundStats) {
        if (roundStats == null || roundStats.isEmpty()) {
            return;
        }
        mutate(jobId, mutableJob -> {
            Map<String, VariantJob.CallStat> merged = new LinkedHashMap<>(mutableJob.getToolCallStats());
            roundStats.forEach((toolName, stat) -> merged.merge(toolName, stat,
                    (existing, added) -> new VariantJob.CallStat(existing.count() + added.count(), existing.totalMillis() + added.totalMillis())));
            mutableJob.setToolCallStats(merged);
        });
    }

    /**
     * Records one build's trigger-to-result wall-clock time under a human-readable label (e.g. "SOLUTION",
     * "SOLUTION+TEMPLATE (joint)", "VERIFYING:SOLUTION+TEMPLATE (joint)"), accumulating count/total ms per label.
     *
     * @param jobId         the job id
     * @param label         which build this was
     * @param elapsedMillis how long the trigger-to-result wait took
     */
    public void recordBuildStat(String jobId, String label, long elapsedMillis) {
        mutate(jobId, mutableJob -> {
            Map<String, VariantJob.CallStat> merged = new LinkedHashMap<>(mutableJob.getBuildStats());
            merged.merge(label, new VariantJob.CallStat(1, elapsedMillis), (existing, added) -> existing.plus(added.totalMillis()));
            mutableJob.setBuildStats(merged);
        });
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
     * and any warnings.
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
        logTelemetrySummary(job);
        publish(job, VariantGenerationEventDTO.done(terminalPhase, variantExerciseId, warnings));
    }

    /**
     * Terminal transition to FAILED; publishes FAILED with the failure detail. The phase the job failed in
     * is preserved on the job record so the tray can label the entry "Failed (VERIFYING)".
     *
     * @param jobId  the job id
     * @param detail failure description including the phase
     */
    public void fail(String jobId, String detail) {
        fail(jobId, detail, null);
    }

    /**
     * Terminal transition to FAILED with an optional AI-generated instructor summary (state of the exercise
     * plus next steps). Also clears the variant exercise id — the hard-failure policy deletes the provisioned
     * clone before failing, so a deep link would point at a deleted exercise.
     *
     * @param jobId             the job id
     * @param detail            failure description including the phase
     * @param instructorSummary AI-generated next-steps summary, or null when unavailable
     */
    public void fail(String jobId, String detail, String instructorSummary) {
        VariantJob job = mutate(jobId, mutableJob -> {
            mutableJob.setFailedInPhase(mutableJob.getPhase());
            mutableJob.setFailureDetail(detail);
            mutableJob.setInstructorSummary(instructorSummary);
            mutableJob.setPhase(VariantJobPhase.FAILED);
            mutableJob.setVariantExerciseId(null); // clone was deleted by the hard-failure cleanup — no deep link
            mutableJob.setFinishedAt(Instant.now());
        });
        logTelemetrySummary(job);
        publish(job, VariantGenerationEventDTO.failed(detail));
    }

    /**
     * Terminal transition to CANCELLED (after the pipeline finished the clone cleanup); publishes CANCELLED.
     *
     * @param jobId the job id
     */
    public void markCancelled(String jobId) {
        VariantJob job = mutate(jobId, mutableJob -> {
            mutableJob.setPhase(VariantJobPhase.CANCELLED);
            mutableJob.setVariantExerciseId(null); // clone was deleted — no deep link
            mutableJob.setFinishedAt(Instant.now());
        });
        logTelemetrySummary(job);
        publish(job, VariantGenerationEventDTO.cancelled());
    }

    /**
     * Logs the job's accumulated per-tool-call and per-build telemetry at its terminal transition — the baseline
     * every further performance change (batching, joint builds, ...) should be quantified against instead of
     * eyeballed from raw logs.
     */
    private void logTelemetrySummary(VariantJob job) {
        if (job.getToolCallStats().isEmpty() && job.getBuildStats().isEmpty()) {
            return;
        }
        String toolSummary = formatStatsSummary(job.getToolCallStats());
        String buildSummary = formatStatsSummary(job.getBuildStats());
        log.info("Variant job {} telemetry — tool calls: [{}], builds: [{}]", job.getJobId(), toolSummary, buildSummary);
    }

    private static String formatStatsSummary(Map<String, VariantJob.CallStat> stats) {
        return stats.entrySet().stream().map(entry -> entry.getKey() + "=" + entry.getValue().count() + "x/" + entry.getValue().totalMillis() + "ms")
                .collect(Collectors.joining(", "));
    }

    /**
     * Refreshes the job's heartbeat without changing its state — called by the agent tools on every tool call
     * so the long internal agent round (whose only other update is at its boundary) keeps the job from being
     * misjudged as stale. No-op once the job is terminal or gone.
     *
     * @param jobId the job id
     */
    public void heartbeat(String jobId) {
        VariantJob job = jobMap.get(jobId);
        if (job != null && !job.getPhase().isTerminal()) {
            job.setLastHeartbeatAt(Instant.now());
            jobMap.put(jobId, job);
        }
    }

    /**
     * Read-side reconciliation: a non-terminal job whose heartbeat is older than {@link #STALE_THRESHOLD} lost
     * its worker node to a restart/crash. Transition it to FAILED so it stops showing as running; any provisioned
     * clone is left in place for the instructor to inspect or delete.
     */
    private VariantJob reconcileStaleness(VariantJob job) {
        if (job.getPhase().isTerminal()) {
            return job;
        }
        Instant lastBeat = job.getLastHeartbeatAt() != null ? job.getLastHeartbeatAt() : job.getStartedAt();
        if (lastBeat == null || lastBeat.isAfter(Instant.now().minus(STALE_THRESHOLD))) {
            return job;
        }
        VariantJob staleJob = mutate(job.getJobId(), mutableJob -> {
            mutableJob.setFailedInPhase(mutableJob.getPhase());
            mutableJob.setFailureDetail("Generation stopped responding (the server node running it restarted or crashed) and was marked as failed.");
            mutableJob.setPhase(VariantJobPhase.FAILED);
            mutableJob.setFinishedAt(Instant.now());
        });
        logTelemetrySummary(staleJob);
        publish(staleJob, VariantGenerationEventDTO.failed(staleJob.getFailureDetail()));
        return staleJob;
    }

    private VariantJob mutate(String jobId, Consumer<VariantJob> mutation) {
        VariantJob job = jobMap.get(jobId);
        if (job == null) {
            throw new IllegalStateException("Variant job " + jobId + " no longer exists");
        }
        mutation.accept(job);
        job.setLastHeartbeatAt(Instant.now());
        jobMap.put(jobId, job);
        return job;
    }

    private void publish(VariantJob job, VariantGenerationEventDTO event) {
        websocketService.send(job.getInitiatorLogin(), TOPIC_SUFFIX_PREFIX + job.getJobId(), event);
    }
}
