package de.tum.cit.aet.artemis.lecture.domain;

import java.time.ZonedDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.DomainObject;

/**
 * Tracks the processing state of a lecture unit through the automated content processing pipeline.
 * This includes transcription generation and ingestion into Pyris/Iris.
 * <p>
 * The processing state allows:
 * - Recovery after node restart (checking for stuck states)
 * - Retry logic with exponential backoff
 * - Detection of content changes (video URL or PDF version)
 * - Status display in the UI
 */
@Entity
@Table(name = "lecture_unit_processing_state")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class LectureUnitProcessingState extends DomainObject {

    /**
     * The lecture unit this processing state belongs to.
     * One-to-one relationship - each unit has at most one processing state.
     */
    @OneToOne
    @JoinColumn(name = "lecture_unit_id", unique = true, nullable = false)
    @JsonIgnoreProperties(value = "processingState", allowSetters = true)
    private LectureUnit lectureUnit;

    /**
     * Current phase of processing.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "phase", nullable = false)
    private ProcessingPhase phase = ProcessingPhase.IDLE;

    /**
     * Number of retry attempts for the current phase.
     * Resets when phase transitions forward successfully.
     */
    @Column(name = "retry_count")
    private int retryCount = 0;

    /**
     * Hash of the video source URL to detect changes.
     * When the video URL changes, processing should restart from the beginning.
     */
    @Column(name = "video_source_hash")
    private String videoSourceHash;

    /**
     * Version of the attachment when processing started.
     * When the attachment version changes, re-ingestion is needed.
     */
    @Column(name = "attachment_version")
    private Integer attachmentVersion;

    /**
     * Translation key for error message if processing failed.
     * Use i18n keys like "artemisApp.attachmentVideoUnit.processing.error.youtubePrivate".
     * Populated by the state-write boundary after translating raw Pyris {@code error_code}
     * values into specific, instructor-readable keys; falls back to a generic key when
     * the code is absent or unknown.
     */
    @Column(name = "error_key", length = 255)
    private String errorKey;

    /**
     * Current ingestion job token.
     * Used to validate callbacks - only accept callbacks with matching token.
     * When a new ingestion job starts, the token is updated, invalidating old callbacks.
     */
    @Column(name = "ingestion_job_token")
    private String ingestionJobToken;

    /**
     * Timestamp when the current phase started.
     * Used for timeout detection and recovery.
     */
    @Column(name = "started_at")
    private ZonedDateTime startedAt;

    /**
     * Timestamp of the last state update.
     */
    @Column(name = "last_updated")
    private ZonedDateTime lastUpdated;

    /**
     * Timestamp when this state becomes eligible for retry.
     * <p>
     * - null: Not waiting for retry (either processing normally or retry already started)
     * - non-null: Waiting for retry after the specified time (backoff period)
     * <p>
     * This field separates "waiting for retry" from "retry count" to prevent
     * the scheduler from triggering multiple retries for the same failure.
     */
    @Column(name = "retry_eligible_at")
    private ZonedDateTime retryEligibleAt;

    /**
     * Fingerprint of the source content sent with the current or most recent ingestion job.
     * Computed at dispatch time and forwarded to Iris, which stamps it verbatim into the vector store.
     */
    @Column(name = "content_fingerprint", length = 80)
    private String contentFingerprint;

    /**
     * Fingerprint of the last ingestion run whose terminal success callback arrived.
     * The unit is verifiably up to date exactly when this matches the fingerprint of its current content.
     */
    @Column(name = "confirmed_fingerprint", length = 80)
    private String confirmedFingerprint;

    /**
     * Name of the pipeline stage Iris last reported for the running job (e.g. "vision", "embedding").
     * Stage-level liveness: together with the progress columns this distinguishes a stalled run
     * (heartbeats arrive but progress stopped) from a merely slow one.
     */
    @Column(name = "current_stage", length = 64)
    private String currentStage;

    /**
     * When the currently reported stage was first observed.
     */
    @Column(name = "stage_started_at")
    private ZonedDateTime stageStartedAt;

    /**
     * Progress counter within the current stage, as reported by Iris (e.g. page 41 of 180).
     */
    @Column(name = "stage_progress")
    private Integer stageProgress;

    /**
     * Total work items of the current stage, as reported by Iris.
     */
    @Column(name = "stage_total")
    private Integer stageTotal;

    /**
     * When the progress counter last advanced. A running job whose heartbeats keep arriving while
     * this timestamp ages past the stall window is wedged, not slow.
     */
    @Column(name = "last_progress_at")
    private ZonedDateTime lastProgressAt;

    /**
     * Dispatch queue priority: 0 (or null) for user- and content-triggered work, higher values for
     * backfill and reconcile requeues. Lower values dispatch first.
     */
    @Column(name = "dispatch_priority")
    private Integer dispatchPriority;

    /**
     * True when the next dispatch is a quality re-ingestion: Iris bypasses its structural skip
     * checks so unchanged content is genuinely re-processed. Cleared on terminal success.
     */
    @Column(name = "force_reingest")
    private Boolean forceReingest;

    /**
     * Newest ingestion pipeline version a quality requeue was already issued for.
     * Guarantees at most one quality re-run per pipeline version, which is what makes the
     * quality re-ingestion loop terminate.
     */
    @Column(name = "last_quality_pipeline_version")
    private Integer lastQualityPipelineVersion;

    public LectureUnitProcessingState() {
        // Default constructor for JPA
    }

    public LectureUnitProcessingState(LectureUnit lectureUnit) {
        this.lectureUnit = lectureUnit;
        this.phase = ProcessingPhase.IDLE;
        this.lastUpdated = ZonedDateTime.now();
    }

    public LectureUnit getLectureUnit() {
        return lectureUnit;
    }

    public void setLectureUnit(LectureUnit lectureUnit) {
        this.lectureUnit = lectureUnit;
    }

    public ProcessingPhase getPhase() {
        return phase;
    }

    public void setPhase(ProcessingPhase phase) {
        this.phase = phase;
        this.lastUpdated = ZonedDateTime.now();
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    public String getVideoSourceHash() {
        return videoSourceHash;
    }

    public void setVideoSourceHash(String videoSourceHash) {
        this.videoSourceHash = videoSourceHash;
    }

    public Integer getAttachmentVersion() {
        return attachmentVersion;
    }

    public void setAttachmentVersion(Integer attachmentVersion) {
        this.attachmentVersion = attachmentVersion;
    }

    public String getErrorKey() {
        return errorKey;
    }

    public void setErrorKey(String errorKey) {
        this.errorKey = errorKey;
    }

    public String getIngestionJobToken() {
        return ingestionJobToken;
    }

    public void setIngestionJobToken(String ingestionJobToken) {
        this.ingestionJobToken = ingestionJobToken;
    }

    public ZonedDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(ZonedDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public ZonedDateTime getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(ZonedDateTime lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    public ZonedDateTime getRetryEligibleAt() {
        return retryEligibleAt;
    }

    public void setRetryEligibleAt(ZonedDateTime retryEligibleAt) {
        this.retryEligibleAt = retryEligibleAt;
    }

    public String getContentFingerprint() {
        return contentFingerprint;
    }

    public void setContentFingerprint(String contentFingerprint) {
        this.contentFingerprint = contentFingerprint;
    }

    public String getConfirmedFingerprint() {
        return confirmedFingerprint;
    }

    public void setConfirmedFingerprint(String confirmedFingerprint) {
        this.confirmedFingerprint = confirmedFingerprint;
    }

    public String getCurrentStage() {
        return currentStage;
    }

    public ZonedDateTime getStageStartedAt() {
        return stageStartedAt;
    }

    public Integer getStageProgress() {
        return stageProgress;
    }

    public Integer getStageTotal() {
        return stageTotal;
    }

    public ZonedDateTime getLastProgressAt() {
        return lastProgressAt;
    }

    public int getDispatchPriority() {
        return dispatchPriority != null ? dispatchPriority : 0;
    }

    public void setDispatchPriority(Integer dispatchPriority) {
        this.dispatchPriority = dispatchPriority;
    }

    public boolean isForceReingest() {
        return Boolean.TRUE.equals(forceReingest);
    }

    public void setForceReingest(Boolean forceReingest) {
        this.forceReingest = forceReingest;
    }

    public Integer getLastQualityPipelineVersion() {
        return lastQualityPipelineVersion;
    }

    public void setLastQualityPipelineVersion(Integer lastQualityPipelineVersion) {
        this.lastQualityPipelineVersion = lastQualityPipelineVersion;
    }

    /**
     * Record the stage and progress a heartbeat reported.
     * Tracks stage entry and progress-advance times so stuck detection can tell a stalled run
     * (progress frozen while heartbeats arrive) from a slow one (progress keeps moving).
     *
     * @param stageName     the reported stage name; null leaves the stage ledger untouched
     * @param stageProgress the progress counter within the stage; may be null
     * @param stageTotal    the total work items of the stage; may be null
     */
    public void recordStageProgress(String stageName, Integer stageProgress, Integer stageTotal) {
        if (stageName == null) {
            return;
        }
        ZonedDateTime now = ZonedDateTime.now();
        boolean stageChanged = !stageName.equals(this.currentStage);
        boolean progressAdvanced = stageProgress != null && !stageProgress.equals(this.stageProgress);
        if (stageChanged) {
            this.currentStage = stageName;
            this.stageStartedAt = now;
        }
        if (stageChanged || progressAdvanced) {
            this.lastProgressAt = now;
        }
        this.stageProgress = stageProgress;
        this.stageTotal = stageTotal;
    }

    /**
     * Clear the stage ledger, e.g. when a run leaves the in-flight phases.
     */
    public void clearStageProgress() {
        this.currentStage = null;
        this.stageStartedAt = null;
        this.stageProgress = null;
        this.stageTotal = null;
        this.lastProgressAt = null;
    }

    /**
     * Increment the retry count.
     */
    public void incrementRetryCount() {
        this.retryCount++;
        this.lastUpdated = ZonedDateTime.now();
    }

    /**
     * Reset retry count (used when transitioning to a new phase successfully).
     */
    public void resetRetryCount() {
        this.retryCount = 0;
    }

    /**
     * Transition to a new phase and update timestamps.
     * Clears retry eligibility since we're starting fresh in the new phase.
     *
     * @param newPhase the new processing phase
     */
    public void transitionTo(ProcessingPhase newPhase) {
        this.phase = newPhase;
        this.startedAt = ZonedDateTime.now();
        this.lastUpdated = ZonedDateTime.now();
        this.errorKey = null; // Clear error on phase transition
        this.retryEligibleAt = null; // Clear retry scheduling on phase transition
        clearStageProgress(); // A new phase starts a fresh stage ledger
    }

    /**
     * Mark as failed with an error translation key.
     * Clears retry eligibility since we're in a terminal state.
     *
     * @param key the i18n key for the error message
     */
    public void markFailed(String key) {
        this.phase = ProcessingPhase.FAILED;
        this.errorKey = key;
        this.lastUpdated = ZonedDateTime.now();
        this.retryEligibleAt = null;
    }

    /**
     * Schedule a retry after the specified backoff period.
     * The scheduler will pick this up once the time has passed.
     *
     * @param backoffMinutes minutes to wait before retry becomes eligible
     */
    public void scheduleRetry(long backoffMinutes) {
        this.retryEligibleAt = ZonedDateTime.now().plusMinutes(backoffMinutes);
        this.lastUpdated = ZonedDateTime.now();
    }

    /**
     * Clear retry eligibility, typically when a retry starts.
     * This prevents the scheduler from triggering another retry for the same failure.
     */
    public void clearRetryEligibility() {
        this.retryEligibleAt = null;
    }

    /**
     * Put this state back into the IDLE queue for a fresh dispatch.
     * Clears every marker of the previous run except the retry budget and the fingerprints:
     * callers that requeue for new work reset the budget themselves, and callers that
     * invalidate the verification evidence clear the confirmed fingerprint explicitly.
     */
    public void requeue() {
        this.phase = ProcessingPhase.IDLE;
        this.startedAt = null;
        this.ingestionJobToken = null;
        this.retryEligibleAt = null;
        this.errorKey = null;
        this.lastUpdated = ZonedDateTime.now();
        clearStageProgress();
    }

    /**
     * Check if processing is currently active (not idle, done, or failed).
     *
     * @return true if processing is active
     */
    public boolean isProcessing() {
        return phase == ProcessingPhase.TRANSCRIBING || phase == ProcessingPhase.INGESTING;
    }

    /**
     * Check if this is a terminal state (done, failed, or skipped).
     *
     * @return true if in terminal state
     */
    public boolean isTerminal() {
        return phase == ProcessingPhase.DONE || phase == ProcessingPhase.FAILED || phase == ProcessingPhase.SKIPPED;
    }

    @Override
    public String toString() {
        return "LectureUnitProcessingState{" + "id=" + getId() + ", lectureUnitId=" + (lectureUnit != null ? lectureUnit.getId() : null) + ", phase=" + phase + ", retryCount="
                + retryCount + ", retryEligibleAt=" + retryEligibleAt + ", lastUpdated=" + lastUpdated + '}';
    }
}
