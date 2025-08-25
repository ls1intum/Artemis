package de.tum.cit.aet.artemis.core.util;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.ws.rs.BadRequestException;

import de.tum.cit.aet.artemis.core.dto.calendar.CalendarEventDTO;

public final class CalendarUtil {

    private CalendarUtil() {
    }

    /**
     * Deserializes a list of month keys into a set of {@link YearMonth} objects.
     *
     * @param monthKeys the list of month strings in YYYY-MM format
     * @return the set of parsed objects
     * @throws BadRequestException if the list is empty or contains invalid format
     */
    public static Set<YearMonth> deserializeMonthKeysOrElseThrow(List<String> monthKeys) {
        try {
            Set<YearMonth> months = monthKeys.stream().map(YearMonth::parse).collect(Collectors.toSet());
            if (months.isEmpty()) {
                throw new BadRequestException("At least one month key must be provided");
            }
            return months;
        }
        catch (DateTimeParseException exception) {
            throw new BadRequestException("Invalid monthKey format. Expected format: YYYY-MM.");
        }
    }

    /**
     * Deserializes a time zone string into a {@link ZoneId}.
     *
     * @param timeZone an IANA time zone ID as string
     * @return the parsed object
     * @throws BadRequestException if the input has a wrong format
     */
    public static ZoneId deserializeZoneIdOrElseThrow(String timeZone) {
        try {
            return ZoneId.of(timeZone);
        }
        catch (DateTimeException exception) {
            throw new BadRequestException("Invalid time zone format. Expected IANA time zone ID.");
        }
    }

    /**
     * Filters the given {@link CalendarEventDTO}s to include only those that overlap with the specified {@link YearMonth}.
     *
     * @param eventDTOs      the set of calendar events to filter
     * @param months         a set of months to check for overlap
     * @param clientTimeZone used to accurately compute month boundaries in the client's timezone when checking for overlaps
     * @return the filtered calendar events
     */
    public static Set<CalendarEventDTO> filterForEventsOverlappingMonths(Set<CalendarEventDTO> eventDTOs, Set<YearMonth> months, ZoneId clientTimeZone) {
        return eventDTOs.stream().filter(eventDTO -> months.stream().anyMatch(month -> areMonthAndEventOverlapping(month, eventDTO, clientTimeZone))).collect(Collectors.toSet());
    }

    private static boolean areMonthAndEventOverlapping(YearMonth month, CalendarEventDTO calendarEventDTO, ZoneId clientZone) {
        ZonedDateTime eventStart = calendarEventDTO.startDate();
        ZonedDateTime eventEnd = calendarEventDTO.endDate();
        ZonedDateTime monthStart = ZonedDateTime.of(month.atDay(1), LocalTime.MIDNIGHT, clientZone).withZoneSameInstant(ZoneOffset.UTC);
        ZonedDateTime monthEnd = ZonedDateTime.of(month.atEndOfMonth(), LocalTime.of(23, 59, 59, 999_000_000), clientZone).withZoneSameInstant(ZoneOffset.UTC);

        boolean eventStartFallsIntoMonth = firstIsBeforeOrEqualSecond(monthStart, eventStart) && firstIsBeforeOrEqualSecond(eventStart, monthEnd);
        if (eventEnd == null) {
            return eventStartFallsIntoMonth;
        }
        boolean eventEndFallsIntoMonth = firstIsBeforeOrEqualSecond(monthStart, eventEnd) && firstIsBeforeOrEqualSecond(eventEnd, monthEnd);
        boolean eventWrapsMonth = eventStart.isBefore(monthStart) && monthEnd.isBefore(eventEnd);
        return eventStartFallsIntoMonth || eventEndFallsIntoMonth || eventWrapsMonth;
    }

    private static boolean firstIsBeforeOrEqualSecond(ZonedDateTime first, ZonedDateTime second) {
        return first.isBefore(second) || first.isEqual(second);
    }

    /**
     * Loops through the given {@link CalendarEventDTO}s and splits events that span multiple days into several events each covering one day.
     *
     * @param eventDTOs      the set of calendar events to process
     * @param clientTimeZone the timezone of the client
     * @return a set including all unsplit events and the splitting results
     */
    public static Set<CalendarEventDTO> splitEventsSpanningMultipleDaysIfNecessary(Set<CalendarEventDTO> eventDTOs, ZoneId clientTimeZone) {
        return eventDTOs.stream().flatMap(event -> splitEventAcrossDaysIfNecessary(event, clientTimeZone).stream()).collect(Collectors.toSet());
    }

    private static Set<CalendarEventDTO> splitEventAcrossDaysIfNecessary(CalendarEventDTO event, ZoneId clientTimeZone) {
        ZonedDateTime start = event.startDate();
        ZonedDateTime end = event.endDate();
        if (end == null || start.toLocalDate().equals(end.toLocalDate())) {
            return Set.of(event);
        }

        start = start.withZoneSameInstant(clientTimeZone);
        end = end.withZoneSameInstant(clientTimeZone);

        HashSet<CalendarEventDTO> splitEvents = new HashSet<>();
        LocalDate currentDay = start.toLocalDate();
        LocalDate endDay = end.toLocalDate();

        while (!currentDay.isAfter(endDay)) {
            ZonedDateTime currentStart = currentDay.equals(start.toLocalDate()) ? start : currentDay.atStartOfDay(clientTimeZone);

            ZonedDateTime currentEnd = currentDay.equals(end.toLocalDate()) ? end : currentDay.atTime(DateUtil.END_OF_DAY).atZone(clientTimeZone);

            splitEvents.add(new CalendarEventDTO(event.id(), event.type(), event.title(), currentStart, currentEnd, event.location(), event.facilitator()));

            currentDay = currentDay.plusDays(1);
        }

        return splitEvents;
    }
}
