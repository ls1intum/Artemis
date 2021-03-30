package de.tum.in.www1.artemis.domain.enumeration;

/**
 * Used to define when students are able to see a {@link de.tum.in.www1.artemis.domain.Feedback}.
 *
 * Currently this is only used for Feedbacks linked to a {@link de.tum.in.www1.artemis.domain.ProgrammingExerciseTestCase}.
 *
 * Tutors/Instructors/Admins should always see all levels of visibility.
 */
public enum Visibility {
    /**
     * Students can see it before and after the exercise due date.
     */
    ALWAYS,
    /**
     * Students can only see it after the exercise due date has passed.
     *
     * Tutors/Instructors/Admins of the exercise can still always see it.
     */
    AFTER_DUE_DATE,
    /**
     * Students are not able to see it, neither before nor after the due date has passed.
     *
     * Tutors/Instructors/Admins of the exercise can still always see it.
     */
    NEVER,
}
