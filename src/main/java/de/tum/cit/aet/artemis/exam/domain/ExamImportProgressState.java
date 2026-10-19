package de.tum.cit.aet.artemis.exam.domain;

/**
 * Overall state of an exam (or exercise-group) import, emitted as live progress over a websocket while the import runs.
 */
public enum ExamImportProgressState {

    /**
     * The import is still running; individual exercises are being imported.
     */
    RUNNING,

    /**
     * The import finished and every exercise was imported successfully.
     */
    COMPLETED,

    /**
     * The import finished, but at least one exercise was skipped or could only be imported incompletely.
     */
    COMPLETED_WITH_ISSUES
}
