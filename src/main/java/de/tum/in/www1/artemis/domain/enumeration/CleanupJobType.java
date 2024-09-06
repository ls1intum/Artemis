package de.tum.in.www1.artemis.domain.enumeration;

public enum CleanupJobType {

    ORPHANS("deleteOrphans"), PLAGIARISM_COMPARISONS("deletePlagiarismComparisons"), NON_RATED_RESULTS("deleteNonRatedResults"), RATED_RESULTS("deleteRatedResults"),
    SUBMISSION_VERSIONS("deleteSubmissionVersions"), FEEDBACK("deleteFeedback");

    private final String name;

    CleanupJobType(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return name;
    }
}
