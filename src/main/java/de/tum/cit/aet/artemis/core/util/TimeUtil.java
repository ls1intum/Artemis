package de.tum.cit.aet.artemis.core.util;

import java.time.Clock;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Objects;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

/**
 * Utility class for time-related operations.
 * It provides methods to calculate relative time, convert between ZonedDateTime and Instant, and get the current time.
 * It also allows setting a custom Clock for testing purposes.
 */
public class TimeUtil {

    private static volatile Clock clock = Clock.systemDefaultZone();

    /**
     * Private constructor to prevent instantiation.
     */
    private TimeUtil() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * Get the relative time of a ZonedDateTime object compared to an origin and a unit ZonedDateTime object in percent.
     * <p>
     * Example: origin = 0:00, unit = 10:00, target = 2:30 => 25%
     *
     * @param origin the origin ZonedDateTime object
     * @param unit   the unit ZonedDateTime object
     * @param target the target ZonedDateTime object
     * @return the relative time of the target ZonedDateTime object compared to the origin and unit ZonedDateTime objects
     */
    public static double toRelativeTime(@NotNull ZonedDateTime origin, @NotNull ZonedDateTime unit, @NotNull ZonedDateTime target) {
        return toRelativeTime(origin.toEpochSecond(), unit.toEpochSecond(), target.toEpochSecond());
    }

    /**
     * Get the relative time of a ZonedDateTime object compared to an origin and a unit ZonedDateTime object in percent.
     * <p>
     * Example: origin = 0:00, unit = 10:00, target = 2:30 => 25%
     *
     * @param origin the origin ZonedDateTime object
     * @param unit   the unit ZonedDateTime object
     * @param target the target ZonedDateTime object
     * @return the relative time of the target ZonedDateTime object compared to the origin and unit ZonedDateTime objects
     */
    public static double toRelativeTime(@NotNull Instant origin, @NotNull Instant unit, @NotNull Instant target) {
        return toRelativeTime(origin.getEpochSecond(), unit.getEpochSecond(), target.getEpochSecond());
    }

    private static double toRelativeTime(@NotNull long originEpochSecond, @NotNull long unitEpochSecond, @NotNull long targetEpochSecond) {
        if (originEpochSecond == unitEpochSecond) {
            return 1;
        }
        return 100.0 * (targetEpochSecond - originEpochSecond) / (unitEpochSecond - originEpochSecond);
    }

    /**
     * Convert a ZonedDateTime object to an Instant object.
     *
     * @param zonedDateTime the ZonedDateTime object to convert
     * @return the Instant object
     */
    @Nullable
    public static Instant toInstant(@Nullable ZonedDateTime zonedDateTime) {
        if (zonedDateTime == null) {
            return null;
        }
        return zonedDateTime.toInstant();
    }

    /**
     * Calculates the current ZonedDateTime based on the current clock.
     * In production, this is the system default clock.
     * In tests, the clock can be set to a fixed time for consistent results.
     *
     * @return the current ZonedDateTime
     */
    public static ZonedDateTime now() {
        return ZonedDateTime.now(clock);
    }

    /**
     * Sets a new Clock instance.
     * This is used for testing purposes to control the current time.
     * When no longer needed, the clock should be reset to the system default using {@link #resetClock()}.
     *
     * @param newClock the new Clock instance to set
     */
    public static void setClock(@NotNull Clock newClock) {
        clock = Objects.requireNonNull(newClock, "Clock must not be null");

    }

    public static void resetClock() {
        clock = Clock.systemDefaultZone();
    }
}
