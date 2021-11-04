package de.tum.in.www1.artemis.domain.enumeration;

/**
 * Due to individual due dates for exercises some particiaptions might need a
 * different schedule than the exercise they belong to.
 * <p>
 * All {@link ExerciseLifecycle ExerciseLifecycles} that are not listed here can
 * be scheduled like a participation without individual due date.
 */
public enum ParticipationLifecycle {
    /**
     * Tasks that should be scheduled after the due date have to respect the
     * individual due date.
     */
    DUE,
    /**
     * If the individual due date is after the exercise build and test after due
     * date, then this task should be scheduled at the later one of the two
     * dates for the participation.
     */
    BUILD_AND_TEST_AFTER_DUE_DATE
}
