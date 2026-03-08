package de.tum.cit.aet.artemis.exam.util;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;

/**
 * Utility class for exam date comparisons.
 */
public final class ExamDateUtil {

    private ExamDateUtil() {
        // Utility class
    }

    /**
     * Creates a comparator for ZonedDateTime that truncates to seconds and converts to Instant.
     * This is necessary because dates retrieved from the database lose precision, and using Instant
     * ensures timezone differences are taken into account.
     *
     * @return a comparator that can be used to compare ZonedDateTime values with second precision
     */
    public static Comparator<ZonedDateTime> truncatedComparator() {
        return Comparator.comparing(date -> date.truncatedTo(ChronoUnit.SECONDS).toInstant());
    }
}
