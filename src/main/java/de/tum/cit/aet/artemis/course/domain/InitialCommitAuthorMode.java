package de.tum.cit.aet.artemis.course.domain;

/**
 * Who is the author of the first commit of the template, solution and test repositories of a new programming exercise of a course. Student repositories are copied from
 * the template and carry the same author on their setup commit.
 */
public enum InitialCommitAuthorMode {

    /**
     * The default, and the behavior of every course that never chose another mode. Nothing is rewritten: a repository keeps the author it was created with. A student
     * repository keeps the author of the first commit of the template repository, which can differ from the creator of the exercise if the course used another mode when the
     * exercise was created or imported.
     */
    EXERCISE_CREATOR,

    /** The identity that the server configures as {@code artemis.git.name} and {@code artemis.git.email}. */
    PLACEHOLDER,

    /** A name and an email address that an instructor of the course entered. */
    CUSTOM
}
