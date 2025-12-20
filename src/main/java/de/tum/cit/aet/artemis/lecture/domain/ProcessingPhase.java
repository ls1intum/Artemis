package de.tum.cit.aet.artemis.lecture.domain;

/**
 * Represents the current phase of automated lecture content processing.
 * This includes transcription generation via Nebula and ingestion into Pyris/Iris.
 */
public enum ProcessingPhase {

    /**
     * Initial state - not currently being processed.
     * Processing will be triggered automatically when content changes.
     */
    IDLE,

    /**
     * Transcription is in progress with Nebula.
     * The system polls Nebula for completion status.
     */
    TRANSCRIBING,

    /**
     * Content is being ingested into Pyris for Iris AI features.
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
