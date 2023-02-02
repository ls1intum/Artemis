package de.tum.in.www1.artemis.domain.enumeration;

import java.util.Optional;

/**
 * Due to individual due dates for exercises some participations might need a
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
    BUILD_AND_TEST_AFTER_DUE_DATE;

    /**
     * Find the corresponding participation to an exercise lifecycle if one exists.
     * @param lifecycle of an exercise.
     * @return a participation lifecycle, if a corresponding participation lifecycle exists.
     */
    public static Optional<ParticipationLifecycle> fromExerciseLifecycle(ExerciseLifecycle lifecycle) {
        return switch (lifecycle) {
            case DUE -> Optional.of(ParticipationLifecycle.DUE);
            case BUILD_AND_TEST_AFTER_DUE_DATE -> Optional.of(ParticipationLifecycle.BUILD_AND_TEST_AFTER_DUE_DATE);
            default -> Optional.empty();
        };
    }
}
