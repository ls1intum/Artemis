package de.tum.cit.aet.artemis.iris.domain;

/**
 * Which Course Memory operation a Pyris webhook run performs. Both share the same job type and
 * status callback, so the job has to carry this to interpret a completion.
 */
public enum CourseMemoryOperation {
    /**
     * A thread's Q/A entry is being written or refreshed.
     */
    INGEST,
    /**
     * A thread's entry is being removed because it stopped being resolved.
     */
    DELETE
}
