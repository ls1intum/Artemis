package de.tum.cit.aet.artemis.exercise.factories;

import java.time.ZonedDateTime;

import org.jspecify.annotations.Nullable;

/**
 * The four dates that drive the lifecycle of a course exercise, grouped so that factories do not have to thread them through individually.
 *
 * @param releaseDate       When the exercise becomes visible to students, null means immediately.
 * @param startDate         When students may start participating, null means the release date applies.
 * @param dueDate           Until when students may submit, null means indefinitely.
 * @param assessmentDueDate Until when tutors may assess. Must not be set without a due date.
 */
public record ExerciseDates(@Nullable ZonedDateTime releaseDate, @Nullable ZonedDateTime startDate, @Nullable ZonedDateTime dueDate, @Nullable ZonedDateTime assessmentDueDate) {

    /**
     * Dates for an exercise that is currently ongoing, i.e. already released and still open for submissions. Used by the demo course seeded by the {@code demo} profile.
     * <p>
     * The due date is deliberately far in the future: a demo instance is long lived and the seeding routine never revisits an exercise that already exists, so a short due date
     * would silently make the exercise unparticipatable shortly after the instance was set up.
     *
     * @return the dates of an ongoing exercise.
     */
    public static ExerciseDates ongoing() {
        ZonedDateTime now = ZonedDateTime.now();
        // No start date: participation then starts with the release date, exactly like an exercise that does not define a separate start date in production.
        return new ExerciseDates(now.minusDays(7), null, now.plusYears(1), now.plusYears(1).plusDays(14));
    }
}
