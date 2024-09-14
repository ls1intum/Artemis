package de.tum.cit.aet.artemis.core.domain;

public enum CleanupJobType {

    ORPHANS("deleteOrphans"), PLAGIARISM_COMPARISONS("deletePlagiarismComparisons"), NON_RATED_RESULTS("deleteNonRatedResults"), RATED_RESULTS("deleteRatedResults"),
    SUBMISSION_VERSIONS("deleteSubmissionVersions"), FEEDBACK("deleteFeedback");

    private final String label;

    CleanupJobType(String name) {
        this.label = name;
    }

    public String label() {
        return label;
    }

    @Override
    public String toString() {
        return this.name();
    }
}
