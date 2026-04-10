package de.tum.cit.aet.artemis.lecture.domain;

/**
 * Represents the current phase of automated lecture content processing.
 * <p>
 * The {@code lecture_unit_processing_state} table acts as a database-backed job queue.
 * Iris handles both transcription and ingestion; Artemis tracks progress via these phases.
 */
public enum ProcessingPhase {

    /**
     * Queued state — waiting for dispatch to Iris.
     * Jobs sit in IDLE with {@code startedAt=null} until a slot opens.
     * Also used as the re-entry point after failures (with backoff via retryEligibleAt).
     */
    IDLE,

    /**
     * Iris is generating a transcription for the video.
     * Includes video download, audio extraction, Whisper transcription, and slide alignment.
     * Checkpoint callbacks update the transcription in the database as it progresses.
     */
    TRANSCRIBING,

    /**
     * Iris is ingesting content into the vector database for AI features.
     * This happens after transcription completes (if applicable) or directly for PDF-only units.
     */
    INGESTING,

    /**
     * Processing completed successfully.
     * The content is now available in Iris for AI features.
     */
    DONE,

    /**
     * Processing failed after maximum retry attempts.
     * Manual intervention (retry button) is required.
     */
    FAILED
}
