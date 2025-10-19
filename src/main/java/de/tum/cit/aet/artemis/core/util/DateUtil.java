package de.tum.cit.aet.artemis.core.util;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalField;
import java.time.temporal.WeekFields;
import java.util.List;

import de.tum.cit.aet.artemis.core.dto.StatisticsEntry;

public class DateUtil {

    /*
     * Note: We can NOT use LocalTime.MIN as the precision is not supported by the database, and thus it will be rounded
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
     * @return true if the string follows the ISO 8601 format for date or if null
     */
    public static boolean isIso8601DateString(String dateString) {
        if (dateString == null) {
            return true;
        }

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
     * @return true if the string follows the ISO 8601 format for time or if null
     */
    public static boolean isIso8601TimeString(String timeString) {
        if (timeString == null) {
            return true;
        }

        try {
            LocalTime.parse(timeString);
            return true;
        }
        catch (DateTimeParseException e) {
            return false;
        }
    }

    /**
     * Check if a string follows the ISO 8601 format for dateTime
     *
     * @param timeString the string to check
     * @return true if the string follows the ISO 8601 format for time or if null
     */
    public static boolean isIso8601DateTimeString(String timeString) {
        if (timeString == null) {
            return true;
        }

        try {
            LocalDateTime.parse(timeString);
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
            throw new IllegalArgumentException("Week day must be in range 1-7, Monday-Sunday");
        }
        var searchStart = start;
        while (searchStart.getDayOfWeek().getValue() != weekDay) {
            searchStart = searchStart.plusDays(1);
        }
        // searchStart is now the first occurrence of the week day
        return searchStart;
    }

    /**
     * Gets the week of the given date
     *
     * @param date the date to get the week for
     * @return the calendar week of the given date
     */
    public static Integer getWeekOfDate(ZonedDateTime date) {
        LocalDate localDate = date.toLocalDate();
        TemporalField weekOfYear = WeekFields.of(DayOfWeek.MONDAY, 4).weekOfWeekBasedYear();
        return localDate.get(weekOfYear);
    }

    /**
     * Gets a List<StatisticsData> each containing a date and an amount of entries. We take the amount of entries and
     * map it into the results list based on the date of the entry. This method handles the spanType DAY
     * **Note**: The length of the result list must be correct, all values must be initialized with 0
     *
     * @param outcome A List<StatisticsData>, each StatisticsData containing a date and the amount of entries for one timeslot
     * @param result  the list in which the converted outcome should be inserted
     */
    public static void sortDataIntoHours(List<StatisticsEntry> outcome, List<Integer> result) {
        for (StatisticsEntry entry : outcome) {
            int hourIndex = ((ZonedDateTime) entry.getDay()).getHour();
            int amount = Math.toIntExact(entry.getAmount());
            int currentValue = result.get(hourIndex);
            result.set(hourIndex, currentValue + amount);
        }
    }

    /**
     * Gets a List<StatisticsData> each containing a date and an amount of entries. We take the amount of entries and
     * map it into the results list based on the date of the entry. This method handles the spanType WEEK and MONTH
     * **Note**: The length of the result list must be correct, all values must be initialized with 0
     *
     * @param outcome   A List<StatisticsData>, each StatisticsData containing a date and the amount of entries for one timeslot
     * @param result    the list in which the converted outcome should be inserted
     * @param startDate the startDate of the result list
     */
    public static void sortDataIntoDays(List<StatisticsEntry> outcome, List<Integer> result, ZonedDateTime startDate) {
        for (StatisticsEntry entry : outcome) {
            ZonedDateTime date = (ZonedDateTime) entry.getDay();
            int amount = Math.toIntExact(entry.getAmount());
            int dayIndex = Math.toIntExact(ChronoUnit.DAYS.between(startDate, date));
            int currentValue = result.get(dayIndex);
            result.set(dayIndex, currentValue + amount);
        }
    }

    /**
     * Gets a List<StatisticsData> each containing a date and an amount of entries. We take the amount of entries and
     * map it into the results list based on the date of the entry. This method sorts the data into weeks.
     * **Note**: The length of the result list must be correct, all values must be initialized with 0
     *
     * @param outcome   A List<StatisticsData>, each StatisticsData containing a date and the amount of entries for one timeslot
     * @param result    the list in which the converted outcome should be inserted, should be initialized with enough values
     * @param startDate the startDate of the result list
     */
    public static void sortDataIntoWeeks(List<StatisticsEntry> outcome, List<Integer> result, ZonedDateTime startDate) {
        for (StatisticsEntry entry : outcome) {
            ZonedDateTime date = (ZonedDateTime) entry.getDay();
            int amount = Math.toIntExact(entry.getAmount());
            int dateWeek = getWeekOfDate(date);
            int startDateWeek = getWeekOfDate(startDate);
            int weeksInYear = Math.toIntExact(IsoFields.WEEK_OF_WEEK_BASED_YEAR.rangeRefinedBy(startDate).getMaximum());    // either 52 or 53
            int weekIndex = (dateWeek - startDateWeek + weeksInYear) % weeksInYear;     // make sure to have a positive value in the range [0, 52 or 53]
            int currentValue = result.get(weekIndex);
            result.set(weekIndex, currentValue + amount);
        }
    }

    /**
     * Gets a List<StatisticsData> each containing a date and an amount of entries. We take the amount of entries and
     * map it into the results list based on the date of the entry. This method handles the spanType YEAR
     * **Note**: The length of the result list must be correct, all values must be initialized with 0
     *
     * @param outcome   A List<StatisticsData>, each StatisticsData containing a date and the amount of entries for one timeslot
     * @param result    the list in which the converted outcome should be inserted
     * @param startDate the startDate of the result list
     */
    public static void sortDataIntoMonths(List<StatisticsEntry> outcome, List<Integer> result, ZonedDateTime startDate) {
        for (StatisticsEntry entry : outcome) {
            ZonedDateTime date = (ZonedDateTime) entry.getDay();
            int amount = (int) entry.getAmount();
            int monthOfDate = date.getMonth().getValue();
            int monthOfStartDate = startDate.getMonth().getValue();
            int monthsPerYear = 12;
            int monthIndex = (monthOfDate - monthOfStartDate + monthsPerYear) % monthsPerYear; // make sure to have a positive value in the range [0, 12]
            int currentValue = result.get(monthIndex);
            result.set(monthIndex, currentValue + amount);
        }
    }

}
