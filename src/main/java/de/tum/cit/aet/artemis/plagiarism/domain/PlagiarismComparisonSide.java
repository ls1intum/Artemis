package de.tum.cit.aet.artemis.plagiarism.domain;

/**
 * Which of the two submissions of a {@link PlagiarismComparison} a {@link PlagiarismSubmission} is.
 * <p>
 * The comparison is a pair, and the side is recorded on the submission rather than by two columns on the comparison:
 * the key then lives on the row that would otherwise be reachable from nowhere when the pointer to it is lost.
 */
public enum PlagiarismComparisonSide {
    /** The submission the comparison reports as {@code submissionA}. */
    FIRST,
    /** The submission the comparison reports as {@code submissionB}. */
    SECOND
}
