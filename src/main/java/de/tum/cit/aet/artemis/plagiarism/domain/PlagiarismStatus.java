package de.tum.cit.aet.artemis.plagiarism.domain;

/**
 * After automatic detection, each plagiarism has to be reviewed and revalidated by an instructor.
 */
public enum PlagiarismStatus {
    /**
     * Plagiarism has been confirmed by an instructor.
     */
    CONFIRMED,

    /**
     * Plagiarism has been denied by an instructor.
     */
    DENIED,

    /**
     * The incident has not been reviewed yet.
     */
    NONE
}
