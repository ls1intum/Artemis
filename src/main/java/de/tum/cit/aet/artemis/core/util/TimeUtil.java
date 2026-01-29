package de.tum.cit.aet.artemis.core.util;

import java.time.Clock;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Objects;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Utility class for time-related operations.
 * It provides methods to calculate relative time, convert between ZonedDateTime and Instant, and get the current time.
 * It also allows setting a custom Clock for testing purposes.
 * <p>
 * The clock is thread-local to ensure proper isolation when tests run in parallel.
 * Each thread can set its own clock without affecting other threads.
 */
public class TimeUtil {

    private static final Clock DEFAULT_CLOCK = Clock.systemDefaultZone();

    private static final ThreadLocal<Clock> threadLocalClock = new ThreadLocal<>();

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
    public static double toRelativeTime(@NonNull ZonedDateTime origin, @NonNull ZonedDateTime unit, @NonNull ZonedDateTime target) {
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
    public static double toRelativeTime(@NonNull Instant origin, @NonNull Instant unit, @NonNull Instant target) {
        return toRelativeTime(origin.getEpochSecond(), unit.getEpochSecond(), target.getEpochSecond());
    }

    private static double toRelativeTime(@NonNull long originEpochSecond, @NonNull long unitEpochSecond, @NonNull long targetEpochSecond) {
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
     * <p>
     * The clock is thread-local to ensure proper isolation when tests run in parallel.
     *
     * @return the current ZonedDateTime
     */
    public static ZonedDateTime now() {
        Clock clock = threadLocalClock.get();
        return ZonedDateTime.now(clock != null ? clock : DEFAULT_CLOCK);
    }

    /**
     * Sets a new Clock instance for the current thread.
     * This is used for testing purposes to control the current time.
     * When no longer needed, the clock should be reset using {@link #resetClock()}.
     * <p>
     * The clock is thread-local to ensure proper isolation when tests run in parallel.
     *
     * @param newClock the new Clock instance to set
     */
    public static void setClock(@NonNull Clock newClock) {
        threadLocalClock.set(Objects.requireNonNull(newClock, "Clock must not be null"));
    }

    /**
     * Resets the clock for the current thread to use the system default clock.
     * This removes the thread-local clock value, allowing subsequent calls to {@link #now()}
     * to use the default system clock.
     */
    public static void resetClock() {
        threadLocalClock.remove();
    }
}
