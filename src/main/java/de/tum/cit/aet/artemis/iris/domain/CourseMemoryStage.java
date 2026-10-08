package de.tum.cit.aet.artemis.iris.domain;

/**
 * How far a Course Memory run has got. Sent to the acting user so the UI can report that ingestion
 * actually started (as opposed to being skipped) and later that it finished.
 */
public enum CourseMemoryStage {
    /**
     * Artemis has dispatched the webhook to Pyris.
     */
    TRIGGERED,
    /**
     * Pyris reported the run finished.
     */
    COMPLETED,
    /**
     * Pyris reported the run failed.
     */
    FAILED
}
