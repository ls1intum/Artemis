package de.tum.cit.aet.artemis.hyperion.service.variants;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;
import de.tum.cit.aet.artemis.hyperion.dto.VariantGenerationRequestDTO;

/**
 * The distributed job record for one variant-generation run (plan Sections 2.2 and 2.7.1).
 * Lives in a Hazelcast map owned by {@link ExerciseVariantJobService} — mutable-by-replacement: the job
 * service reads the entry, mutates it, and puts it back so updates are visible cluster-wide (this is what
 * makes background generation, reconnect, and the navbar job tray possible, Section 5.4).
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

    /** The phase the job was in when it failed — tray label "failed in VERIFYING" (plan Section 5.4). */
    private VariantJobPhase failedInPhase;

    private int attempt;

    private int maxAttempts;

    private boolean cancelRequested;

    private ChangePlan changePlan;

    private Map<VariantJobPhase, StepOutput> stepOutputs = new EnumMap<>(VariantJobPhase.class);

    private List<String> warnings = new ArrayList<>();

    private Long variantExerciseId;

    private VariantGenerationRequestDTO request;

    private Instant startedAt;

    private Instant finishedAt;

    /** Accumulated LLM tokens across planning + agent rounds (budget enforcement + thesis telemetry, plan Section 7). */
    private long totalTokensUsed;

    // TODO (Sonnet): Add a staleness mechanism so that after a server restart mid-job the `active` endpoint can
    // report FAILED-stale instead of a forever-running job (plan Section 6, "Server restart mid-job" row).

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

    public Map<VariantJobPhase, StepOutput> getStepOutputs() {
        return stepOutputs;
    }

    public void setStepOutputs(Map<VariantJobPhase, StepOutput> stepOutputs) {
        this.stepOutputs = stepOutputs;
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
}
