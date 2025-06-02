package de.tum.cit.aet.artemis.calendar.service;

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

import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.calendar.domain.CourseCalendarEvent;
import de.tum.cit.aet.artemis.calendar.dto.CalendarEventReadDTO;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupSession;

/**
 * A Service providing tools for processing calendar events derived from both {@link CourseCalendarEvent}s and {@link TutorialGroupSession}s.
 */
@Service
public class CalendarEventService {

    /**
     * Deserializes a list of month keys into a set of {@link YearMonth} objects.
     *
     * @param monthKeys the list of month strings in YYYY-MM format
     * @return a set of parsed {@link YearMonth} objects
     * @throws BadRequestException if the list is empty or contains invalid format
     */
    public Set<YearMonth> deserializeMonthKeysOrElseThrow(List<String> monthKeys) {
        try {
            Set<YearMonth> months = monthKeys.stream().map(YearMonth::parse).collect(Collectors.toSet());
            if (months.isEmpty()) {
                throw new BadRequestException("At least one month key must be provided");
            }
            else {
                return months;
            }
        }
        catch (DateTimeParseException exception) {
            throw new BadRequestException("Invalid monthKey format. Expected format: YYYY-MM.");
        }
    }

    /**
     * Deserializes a time zone string into a {@link ZoneId}.
     *
     * @param timeZone an IANA time zone ID as string
     * @return the corresponding {@link ZoneId}
     * @throws BadRequestException if the input has a wrong format
     */
    public ZoneId deserializeTimeZoneOrElseThrow(String timeZone) {
        try {
            return ZoneId.of(timeZone);
        }
        catch (Exception exception) {
            throw new BadRequestException("Invalid time zone format. Expected IANA time zone ID.");
        }
    }

    /**
     * Filters the given calendar events to include only those that overlap with the specified months.
     *
     * @param eventDTOs      the set of calendar events to filter
     * @param months         the set of {@link YearMonth} values to check for overlap
     * @param clientTimeZone used to accurately compute month boundaries in the client's timezone when checking for overlaps
     * @return a set of {@link CalendarEventReadDTO}s that overlap with at least one of the given months
     */
    public Set<CalendarEventReadDTO> filterForEventsOverlappingMonths(Set<CalendarEventReadDTO> eventDTOs, Set<YearMonth> months, ZoneId clientTimeZone) {
        return eventDTOs.stream().filter(eventDTO -> months.stream().anyMatch(month -> areMonthAndEventOverlapping(month, eventDTO, clientTimeZone))).collect(Collectors.toSet());
    }

    private boolean areMonthAndEventOverlapping(YearMonth month, CalendarEventReadDTO calendarEventReadDTO, ZoneId clientZone) {
        ZonedDateTime eventStart = calendarEventReadDTO.startDate();
        ZonedDateTime eventEnd = calendarEventReadDTO.endDate();
        ZonedDateTime monthStart = ZonedDateTime.of(month.atDay(1), LocalTime.MIDNIGHT, clientZone).withZoneSameInstant(ZoneOffset.UTC);
        ZonedDateTime monthEnd = ZonedDateTime.of(month.atEndOfMonth(), LocalTime.of(23, 59, 59, 999_000_000), clientZone).withZoneSameInstant(ZoneOffset.UTC);

        boolean eventStartFallsIntoMonth = firstIsBeforeOrEqualSecond(monthStart, eventStart) && firstIsBeforeOrEqualSecond(eventStart, monthEnd);
        boolean eventEndFallsIntoMonth = firstIsBeforeOrEqualSecond(monthStart, eventEnd) && firstIsBeforeOrEqualSecond(eventEnd, monthEnd);
        return eventStartFallsIntoMonth || eventEndFallsIntoMonth;
    }

    private boolean firstIsBeforeOrEqualSecond(ZonedDateTime first, ZonedDateTime second) {
        return first.isBefore(second) || first.isEqual(second);
    }

    /**
     * Loops through all events and splits events that span multiple days into several events each covering one day.
     *
     * @param eventDTOs the set of calendar events to filter
     * @return a set of {@link CalendarEventReadDTO}s including all unsplit events and the splitting results
     */
    public Set<CalendarEventReadDTO> splitEventsSpanningMultipleDaysIfNecessary(Set<CalendarEventReadDTO> eventDTOs) {
        return eventDTOs.stream().flatMap(event -> splitEventAcrossDaysIfNecessary(event).stream()).collect(Collectors.toSet());
    }

    private Set<CalendarEventReadDTO> splitEventAcrossDaysIfNecessary(CalendarEventReadDTO event) {
        ZonedDateTime start = event.startDate();
        ZonedDateTime end = event.endDate();

        if (end == null || start.toLocalDate().equals(end.toLocalDate())) {
            return Set.of(event);
        }

        HashSet<CalendarEventReadDTO> splitEvents = new HashSet<>();
        int currentSplitId = 0;
        LocalDate currentDay = start.toLocalDate();
        LocalDate endDay = end.toLocalDate();
        ZoneId zone = start.getZone();

        while (!currentDay.isAfter(endDay)) {
            ZonedDateTime currentStart = currentDay.equals(start.toLocalDate()) ? start : currentDay.atStartOfDay(zone);

            ZonedDateTime currentEnd = currentDay.equals(end.toLocalDate()) ? end : currentDay.atTime(LocalTime.MAX).withNano(999_999_999).atZone(zone);

            splitEvents.add(new CalendarEventReadDTO(event.id() + "-" + currentSplitId, event.title(), event.courseName(), currentStart, currentEnd, event.location(),
                    event.facilitator()));

            currentSplitId++;
            currentDay = currentDay.plusDays(1);
        }

        return splitEvents;
    }
}
