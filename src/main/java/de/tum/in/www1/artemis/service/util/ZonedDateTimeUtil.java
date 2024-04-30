package de.tum.in.www1.artemis.service.util;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.Optional;
import java.util.stream.LongStream;

import javax.validation.constraints.NotNull;

public class ZonedDateTimeUtil {

    /**
     * Private constructor to prevent instantiation.
     */
    private ZonedDateTimeUtil() {
        throw new IllegalStateException("Utility class");
    }

    public static <T extends ZonedDateTime> LongStream getEpochSecondStream(@NotNull Collection<T> zonedDateTimes) {
        return zonedDateTimes.stream().mapToLong(ZonedDateTime::toEpochSecond);
    }

    /**
     * Get the average ZonedDateTime of a collection of ZonedDateTime objects.
     *
     * @param zonedDateTimes the collection of ZonedDateTime objects
     * @return optional of the average ZonedDateTime object, empty if the collection is empty
     */
    public static Optional<ZonedDateTime> getZonedDateTimeAverage(@NotNull Collection<ZonedDateTime> zonedDateTimes) {
        if (zonedDateTimes == null || zonedDateTimes.isEmpty()) {
            return Optional.empty();
        }
        final var avg = getEpochSecondStream(zonedDateTimes).average();
        if (avg.isPresent()) {
            final var epochSecond = Math.round(avg.getAsDouble());
            final var instant = Instant.ofEpochSecond(epochSecond);
            final var zoneId = zonedDateTimes.iterator().next().getZone();
            return Optional.of(ZonedDateTime.ofInstant(instant, zoneId));
        }
        return Optional.empty();
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
        return 100.0 * target.toEpochSecond() / (unit.toEpochSecond() - origin.toEpochSecond());
    }
}
