package de.tum.cit.aet.artemis.programming.domain;

import java.io.Serializable;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The period in which pushes to student repositories of a programming exercise trigger a build. Pushes outside the period are kept in the repository but
 * neither create a submission nor queue a build.
 * <p>
 * Both bounds are inclusive and optional: without a start, builds run from the beginning, without an end, they run until the end.
 * A window without any bound does not restrict anything and is represented by {@code null} instead (see {@link #normalized(BuildTimeWindow)}).
 * The bounds are kept in UTC, so that two windows describing the same instants are equal no matter in which zone they were entered or read back.
 *
 * @param start the first instant at which a push is built, or {@code null} for no lower bound
 * @param end   the last instant at which a push is built, or {@code null} for no upper bound
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
public record BuildTimeWindow(@Nullable @JsonFormat(shape = JsonFormat.Shape.STRING) ZonedDateTime start, @Nullable @JsonFormat(shape = JsonFormat.Shape.STRING) ZonedDateTime end)
        implements Serializable {

    public BuildTimeWindow {
        start = start == null ? null : start.withZoneSameInstant(ZoneOffset.UTC);
        end = end == null ? null : end.withZoneSameInstant(ZoneOffset.UTC);
    }

    /**
     * Checks whether a push at the given instant is built.
     *
     * @param instant the instant of the push
     * @return true if the instant lies inside this window (bounds included)
     */
    public boolean isBuildAllowedAt(ZonedDateTime instant) {
        return (start == null || !instant.isBefore(start)) && (end == null || !instant.isAfter(end));
    }

    /**
     * Gets the window that applies to a student with an individual due date. Like the due date, the end of the window moves later by the extension, which is the time
     * between the due date of the exercise and the individual due date. The start does not move.
     *
     * @param dueDate           the due date of the exercise, or {@code null} if it has none
     * @param individualDueDate the individual due date of the student, or {@code null} if there is none
     * @return this window if there is no end or no extension, otherwise the window with the extended end
     */
    public BuildTimeWindow extendedByDueDateExtension(@Nullable ZonedDateTime dueDate, @Nullable ZonedDateTime individualDueDate) {
        if (end == null || dueDate == null || individualDueDate == null || !individualDueDate.isAfter(dueDate)) {
            return this;
        }
        return new BuildTimeWindow(start, end.plus(Duration.between(dueDate, individualDueDate)));
    }

    /**
     * Checks whether the bounds of this window lie between the given dates.
     *
     * @param earliest the first allowed instant, or {@code null} for no restriction
     * @param latest   the last allowed instant, or {@code null} for no restriction
     * @return true if neither bound is before {@code earliest} or after {@code latest}
     */
    public boolean isWithin(@Nullable ZonedDateTime earliest, @Nullable ZonedDateTime latest) {
        return !(isBefore(start, earliest) || isBefore(end, earliest) || isBefore(latest, start) || isBefore(latest, end));
    }

    private static boolean isBefore(@Nullable ZonedDateTime first, @Nullable ZonedDateTime second) {
        return first != null && second != null && first.isBefore(second);
    }

    /**
     * Whether neither bound is set, i.e. the window does not restrict anything.
     *
     * @return true if both bounds are missing
     */
    @JsonIgnore
    public boolean hasNoBounds() {
        return start == null && end == null;
    }

    /**
     * Gives a window without any bound the single representation of "no window".
     *
     * @param window the window to normalise, may be null
     * @return the window, or null if it is null or has no bound
     */
    @Nullable
    public static BuildTimeWindow normalized(@Nullable BuildTimeWindow window) {
        return window == null || window.hasNoBounds() ? null : window;
    }
}
