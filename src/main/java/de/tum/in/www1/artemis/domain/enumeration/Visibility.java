package de.tum.in.www1.artemis.domain.enumeration;

/**
 * Used to define when students are able to see a {@link de.tum.in.www1.artemis.domain.Feedback}.
 * <p>
 * Currently, this is only used for Feedbacks linked to a {@link de.tum.in.www1.artemis.domain.ProgrammingExerciseTestCase}.
 * <p>
 * Tutors/Instructors/Admins should always see all levels of visibility.
 * <p>
 * Note: The order is used as part of an {@link javax.persistence.EnumType#ORDINAL} mapping in {@link de.tum.in.www1.artemis.domain.Feedback}.
 * Do NOT change the order of existing values.
 */
public enum Visibility {
    /**
     * Students can see it before and after the exercise due date.
     * <p>
     * ordinal = 0
     */
    ALWAYS,
    /**
     * Students can only see it after the exercise due date has passed.
     * <p>
     * Tutors/Instructors/Admins of the exercise can still always see it.
     * <p>
     * ordinal = 1
     */
    AFTER_DUE_DATE,
    /**
     * Students are not able to see it, neither before nor after the due date has passed.
     * <p>
     * Tutors/Instructors/Admins of the exercise can still always see it.
     * <p>
     * ordinal = 2
     */
    NEVER,
}
