package de.tum.in.www1.artemis.service.util;

import java.time.Instant;
import java.time.ZonedDateTime;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public class TimeUtil {

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
}
