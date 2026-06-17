package de.tum.cit.aet.artemis.exam.domain;

/**
 * Status of a single exercise during an exam (or exercise-group) import, used in the live progress emitted over a websocket.
 */
public enum ExerciseImportStatus {

    /**
     * The exercise is currently being imported (e.g. its repositories are being copied).
     */
    IMPORTING,

    /**
     * The exercise was imported successfully.
     */
    IMPORTED,

    /**
     * The exercise was cleanly skipped: nothing was persisted (e.g. the source exercise was deleted or the responsible import module is unavailable).
     */
    SKIPPED,

    /**
     * The exercise failed partway and may have left an incomplete exercise that should be reviewed and removed.
     */
    INCOMPLETE
}
