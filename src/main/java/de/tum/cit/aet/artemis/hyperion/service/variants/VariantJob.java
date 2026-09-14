package de.tum.cit.aet.artemis.hyperion.service.variants;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;
import de.tum.cit.aet.artemis.hyperion.dto.VariantGenerationRequestDTO;

/**
 * The distributed job record for one variant-generation run. Lives in a Hazelcast map owned by
 * {@link ExerciseVariantJobService} — mutable-by-replacement: the job service reads the entry, mutates it, and
 * puts it back so updates are visible cluster-wide (this is what makes background generation, reconnect, and
 * the navbar job tray possible).
 *
 * NOTE: mutation MUST always go through {@link ExerciseVariantJobService} (single writer) so websocket
 * events and map state stay consistent.
 */
public class VariantJob implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String jobId;

    private Long sourceExerciseId;

    /** Course of the source exercise (resolved via exam group for exam exercises) — tray deep links need it. */
    private Long courseId;

    private String sourceExerciseTitle;

    private ExerciseType exerciseType;

    private String initiatorLogin;

    private VariantJobPhase phase;

    /** The phase the job was in when it failed — tray label "failed in VERIFYING". */
    private VariantJobPhase failedInPhase;

    /**
     * The failure description published with the FAILED event — persisted so the tray's "show summary"
     * modal can still explain why the job failed after the live event is gone.
     */
    private String failureDetail;

    /**
     * AI-generated, instructor-facing failure summary: what state the exercise is in and which next steps
     * fix or retry the generation. Best-effort — null when the summary call itself failed.
     */
    private String instructorSummary;

    private int attempt;

    private int maxAttempts;

    private boolean cancelRequested;

    private ChangePlan changePlan;

    /**
     * Append-only output history per phase, oldest first. A phase can produce several outputs — VERIFYING and
     * REPAIRING record one per attempt — and every one is kept: an instructor debugging a job that failed twice
     * and then succeeded needs the earlier failures, not just the last message.
     */
    private Map<VariantJobPhase, List<StepOutput>> stepOutputs = new EnumMap<>(VariantJobPhase.class);

    private List<String> warnings = new ArrayList<>();

    private Long variantExerciseId;

    /** The planned/provisioned variant title — the tray/modal "source → variant" display. */
    private String variantExerciseTitle;

    private VariantGenerationRequestDTO request;

    private Instant startedAt;

    private Instant finishedAt;

    /** Accumulated LLM tokens across planning + agent rounds (budget enforcement + telemetry). */
    private long totalTokensUsed;

    /**
     * Last time the running node advanced this job (every state change plus each agent tool call). A
     * non-terminal job whose heartbeat has gone stale lost its worker to a restart or crash and is marked
     * FAILED-stale on read — see {@link ExerciseVariantJobService}.
     */
    private Instant lastHeartbeatAt;

    /**
     * Baseline performance telemetry (call count + total wall-clock ms), keyed by tool name, accumulated across
     * every agent round of this job — a prerequisite for quantifying further performance work against a real
     * baseline instead of eyeballing logs.
     */
    private Map<String, CallStat> toolCallStats = new LinkedHashMap<>();

    /**
     * Baseline build telemetry (trigger -> result wall-clock ms), keyed by a human-readable label (e.g.
     * "SOLUTION", "SOLUTION+TEMPLATE (joint)", "VERIFYING:SOLUTION+TEMPLATE (joint)") so agent-triggered builds
     * and the deterministic VERIFYING re-verification gate can be told apart.
     */
    private Map<String, CallStat> buildStats = new LinkedHashMap<>();

    /**
     * One accumulated telemetry bucket: how many times something ran and how much total wall-clock time it took.
     *
     * @param count       number of calls/builds accumulated into this bucket
     * @param totalMillis their combined wall-clock duration
     */
    public record CallStat(int count, long totalMillis) implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /**
         * @return a new stat with one more call/build of the given duration folded in
         */
        CallStat plus(long millis) {
            return new CallStat(count + 1, totalMillis + millis);
        }
    }

    public String getJobId() {
        return jobId;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }

    public Long getSourceExerciseId() {
        return sourceExerciseId;
    }

    public void setSourceExerciseId(Long sourceExerciseId) {
        this.sourceExerciseId = sourceExerciseId;
    }

    public Long getCourseId() {
        return courseId;
    }

    public void setCourseId(Long courseId) {
        this.courseId = courseId;
    }

    public String getSourceExerciseTitle() {
        return sourceExerciseTitle;
    }

    public void setSourceExerciseTitle(String sourceExerciseTitle) {
        this.sourceExerciseTitle = sourceExerciseTitle;
    }

    public ExerciseType getExerciseType() {
        return exerciseType;
    }

    public void setExerciseType(ExerciseType exerciseType) {
        this.exerciseType = exerciseType;
    }

    public String getInitiatorLogin() {
        return initiatorLogin;
    }

    public void setInitiatorLogin(String initiatorLogin) {
        this.initiatorLogin = initiatorLogin;
    }

    public VariantJobPhase getPhase() {
        return phase;
    }

    public void setPhase(VariantJobPhase phase) {
        this.phase = phase;
    }

    public VariantJobPhase getFailedInPhase() {
        return failedInPhase;
    }

    public void setFailedInPhase(VariantJobPhase failedInPhase) {
        this.failedInPhase = failedInPhase;
    }

    public String getFailureDetail() {
        return failureDetail;
    }

    public void setFailureDetail(String failureDetail) {
        this.failureDetail = failureDetail;
    }

    public String getInstructorSummary() {
        return instructorSummary;
    }

    public void setInstructorSummary(String instructorSummary) {
        this.instructorSummary = instructorSummary;
    }

    public int getAttempt() {
        return attempt;
    }

    public void setAttempt(int attempt) {
        this.attempt = attempt;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public boolean isCancelRequested() {
        return cancelRequested;
    }

    public void setCancelRequested(boolean cancelRequested) {
        this.cancelRequested = cancelRequested;
    }

    public ChangePlan getChangePlan() {
        return changePlan;
    }

    public void setChangePlan(ChangePlan changePlan) {
        this.changePlan = changePlan;
    }

    public Map<VariantJobPhase, List<StepOutput>> getStepOutputs() {
        return stepOutputs;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public void setWarnings(List<String> warnings) {
        this.warnings = warnings;
    }

    public Long getVariantExerciseId() {
        return variantExerciseId;
    }

    public void setVariantExerciseId(Long variantExerciseId) {
        this.variantExerciseId = variantExerciseId;
    }

    public String getVariantExerciseTitle() {
        return variantExerciseTitle;
    }

    public void setVariantExerciseTitle(String variantExerciseTitle) {
        this.variantExerciseTitle = variantExerciseTitle;
    }

    public VariantGenerationRequestDTO getRequest() {
        return request;
    }

    public void setRequest(VariantGenerationRequestDTO request) {
        this.request = request;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(Instant finishedAt) {
        this.finishedAt = finishedAt;
    }

    public long getTotalTokensUsed() {
        return totalTokensUsed;
    }

    public void setTotalTokensUsed(long totalTokensUsed) {
        this.totalTokensUsed = totalTokensUsed;
    }

    public Instant getLastHeartbeatAt() {
        return lastHeartbeatAt;
    }

    public void setLastHeartbeatAt(Instant lastHeartbeatAt) {
        this.lastHeartbeatAt = lastHeartbeatAt;
    }

    public Map<String, CallStat> getToolCallStats() {
        return toolCallStats;
    }

    public void setToolCallStats(Map<String, CallStat> toolCallStats) {
        this.toolCallStats = toolCallStats;
    }

    public Map<String, CallStat> getBuildStats() {
        return buildStats;
    }

    public void setBuildStats(Map<String, CallStat> buildStats) {
        this.buildStats = buildStats;
    }
}
