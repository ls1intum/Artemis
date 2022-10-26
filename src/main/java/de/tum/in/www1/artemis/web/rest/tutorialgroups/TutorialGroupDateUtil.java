package de.tum.in.www1.artemis.web.rest.tutorialgroups;

import java.time.*;
import java.time.format.DateTimeParseException;

public class TutorialGroupDateUtil {

    /*
     * Note: We can NOT use LocalTime.MIN as the precision is not supported by the database, and thus it will rounded
     */
    public static final LocalTime START_OF_DAY = LocalTime.of(0, 0, 0);

    /*
     * Note: We can NOT use LocalTime.MAX as the precision is not supported by the database, and thus it will be rounded
     */
    public static final LocalTime END_OF_DAY = LocalTime.of(23, 59, 59);

    /**
     * Check if a string follows the ISO 8601 format for date
     *
     * @param dateString the string to check
     * @return true if the string follows the ISO 8601 format for date
     */
    public static boolean isIso8601DateString(String dateString) {
        try {
            LocalDate.parse(dateString);
            return true;
        }
        catch (DateTimeParseException e) {
            return false;
        }
    }

    /**
     * Check if a string follows the ISO 8601 format for time
     *
     * @param timeString the string to check
     * @return true if the string follows the ISO 8601 format for time
     */
    public static boolean isIso8601TimeString(String timeString) {
        try {
            LocalTime.parse(timeString);
            return true;
        }
        catch (DateTimeParseException e) {
            return false;
        }
    }

    /**
     * Convert a LocalDate and LocalTime to a ZonedDateTime by interpreting them in a specific time zone
     *
     * @param localDate date to convert
     * @param localTime time to convert
     * @param timeZone  time zone to interpret the date and time in
     * @return the ZonedDateTime object interpreted in the given time zone
     */
    public static ZonedDateTime interpretInTimeZone(LocalDate localDate, LocalTime localTime, String timeZone) {
        return ZonedDateTime.of(localDate, localTime, ZoneId.of(timeZone));
    }

    /**
     * Convert a LocalDateTime to a ZonedDateTime by interpreting them in a specific time zone
     *
     * @param localDateTime date and time to convert
     * @param timeZone      time zone to interpret the date and time in
     * @return the ZonedDateTime object interpreted in the given time zone
     */
    public static ZonedDateTime interpretInTimeZone(LocalDateTime localDateTime, String timeZone) {
        return interpretInTimeZone(localDateTime.toLocalDate(), localDateTime.toLocalTime(), timeZone);
    }

    /**
     * Get the first date of the week day from the given start date
     *
     * @param start   the start date to start the search from
     * @param weekDay the week day to search for (1-7)
     * @return date of the first occurrence of the week day from the given start date
     */
    public static LocalDate getFirstDateOfWeekDay(LocalDate start, Integer weekDay) {
        if (weekDay < 1 || weekDay > 7) {
            throw new IllegalArgumentException("Week day must be in range 1-7");
        }
        while (start.getDayOfWeek().getValue() != weekDay) {
            start = start.plusDays(1);
        }
        return start;
    }

}
