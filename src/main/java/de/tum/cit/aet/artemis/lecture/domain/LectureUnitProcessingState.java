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
 * This includes transcription generation (Nebula) and ingestion into Pyris/Iris.
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
    @JoinColumn(name = "lecture_unit_id", unique = true)
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
     * Use i18n keys like "artemisApp.processing.error.transcriptionFailed".
     */
    @Column(name = "error_key", length = 500)
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
     *
     * @param newPhase the new processing phase
     */
    public void transitionTo(ProcessingPhase newPhase) {
        this.phase = newPhase;
        this.startedAt = ZonedDateTime.now();
        this.lastUpdated = ZonedDateTime.now();
        this.errorKey = null; // Clear error on phase transition
    }

    /**
     * Mark as failed with an error translation key.
     *
     * @param key the i18n key for the error message
     */
    public void markFailed(String key) {
        this.phase = ProcessingPhase.FAILED;
        this.errorKey = key;
        this.lastUpdated = ZonedDateTime.now();
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
     * Check if this is a terminal state (done or failed).
     *
     * @return true if in terminal state
     */
    public boolean isTerminal() {
        return phase == ProcessingPhase.DONE || phase == ProcessingPhase.FAILED;
    }

    @Override
    public String toString() {
        return "LectureUnitProcessingState{" + "id=" + getId() + ", lectureUnitId=" + (lectureUnit != null ? lectureUnit.getId() : null) + ", phase=" + phase + ", retryCount="
                + retryCount + ", lastUpdated=" + lastUpdated + '}';
    }
}
