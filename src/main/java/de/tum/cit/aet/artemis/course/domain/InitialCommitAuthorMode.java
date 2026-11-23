package de.tum.cit.aet.artemis.course.domain;

/**
 * Who is the author of the first commit of the template, solution and test repositories of a new programming exercise of a course. Student repositories are copied from
 * the template and carry the same author on their setup commit.
 */
public enum InitialCommitAuthorMode {

    /** The user who created the exercise. This is the behavior of every course that never chose another one. */
    EXERCISE_CREATOR,

    /** The identity that the server configures as {@code artemis.git.name} and {@code artemis.git.email}. */
    PLACEHOLDER,

    /** A name and an email address that an instructor of the course entered. */
    CUSTOM
}
