package de.tum.cit.aet.artemis.admin.domain;

public enum CleanupJobType {

    ORPHANS("deleteOrphans"), PLAGIARISM_COMPARISONS("deletePlagiarismComparisons"), NON_RATED_RESULTS("deleteNonRatedResults"), RATED_RESULTS("deleteRatedResults"),
    SUBMISSION_VERSIONS("deleteSubmissionVersions"), FEEDBACK("deleteFeedback"), OLD_COURSES_RESET_WARNING("warnOldCoursesReset"), OLD_COURSES_RESET("resetOldCourses"),
    NOT_ENROLLED_USERS("deleteNotEnrolledUsers"), OLD_COURSE_SUBMISSION_VERSIONS("deleteOldCourseSubmissionVersions");

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
