package de.tum.cit.aet.artemis.exercise.domain;

/**
 * Distinguishes between different kinds of submissions.
 */
public enum SubmissionType {
    /**
     * A submission triggered by a student.
     */
    MANUAL,
    /**
     * Quiz ended before the submission was manually sent to the server.
     *
     * <p>
     * Automatic checkpoint of the last known state.
     */
    TIMEOUT,
    /**
     * Submission created for solution and template repositories after pushing to test repository.
     */
    TEST,
    /**
     * Submissions not covered by one of the more specific cases.
     */
    OTHER,
    /**
     * Commits to the solution, test, template repositories.
     */
    INSTRUCTOR,
    /**
     * Submission that was added through the add external submission dialogue.
     */
    EXTERNAL,
    /**
     * Submission of an exam programming exercise that is submitted before the exercise start date or after the individual exercise end date.
     */
    ILLEGAL
}
