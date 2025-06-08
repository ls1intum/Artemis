package de.tum.cit.aet.artemis.calendar.util;

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

import de.tum.cit.aet.artemis.calendar.domain.CoursewideCalendarEvent;
import de.tum.cit.aet.artemis.calendar.dto.CalendarEventDTO;
import de.tum.cit.aet.artemis.calendar.dto.CoursewideCalendarEventDTO;
import de.tum.cit.aet.artemis.core.util.DateUtil;

public class CalendarUtil {

    /**
     * Validates that all id fields of the given {@link CoursewideCalendarEventDTO}s are set to null.
     *
     * @param coursewideCalendarEventDTOS the list of DTOs to check
     * @throws BadRequestException if any DTO has a non-null id
     */
    public static void checkThatNoEventHasIdElseThrow(List<CoursewideCalendarEventDTO> coursewideCalendarEventDTOS) {
        boolean anyHasId = coursewideCalendarEventDTOS.stream().anyMatch(dto -> dto.id() != null);
        if (anyHasId) {
            throw new BadRequestException("New calendar events must not have an id, since ids are assigned automatically.");
        }
    }

    /**
     * Validates that all courseName fields of the given {@link CoursewideCalendarEventDTO}s are set to null.
     *
     * @param coursewideCalendarEventDTOS the list of DTOs to check
     * @throws BadRequestException if any DTO has a non-null courseName
     */
    public static void checkThatNoEventHasACourseNameElseThrow(List<CoursewideCalendarEventDTO> coursewideCalendarEventDTOS) {
        coursewideCalendarEventDTOS.forEach(CalendarUtil::checkThatEventHasNoCourseNameElseThrow);
    }

    /**
     * Validates that each of the given {@link CoursewideCalendarEventDTO}s is visible to at least one of the following user groups: students, tutors, editors, or instructors.
     *
     * @param coursewideCalendarEventDTOS the list of DTOs to check
     * @throws BadRequestException if any of the DTOs is visible to none of the user groups.
     */
    public static void checkThatAllEventsAreAtLeastVisibleToOneUserGroupElseThrow(List<CoursewideCalendarEventDTO> coursewideCalendarEventDTOS) {
        coursewideCalendarEventDTOS.forEach(CalendarUtil::checkThatEventIsAtLeastVisibleToOneUserGroupElseThrow);
    }

    /**
     * Validates that the given {@link CoursewideCalendarEventDTO} is visible to at least one of the following user groups: students, tutors, editors, or instructors.
     *
     * @param coursewideCalendarEventDTO the DTO to check
     * @throws BadRequestException if the DTO is visible to none of the user groups.
     */
    public static void checkThatEventIsAtLeastVisibleToOneUserGroupElseThrow(CoursewideCalendarEventDTO coursewideCalendarEventDTO) {
        boolean isVisibleToNoUserGroup = !coursewideCalendarEventDTO.visibleToStudents() && !coursewideCalendarEventDTO.visibleToTutors()
                && !coursewideCalendarEventDTO.visibleToEditors() && !coursewideCalendarEventDTO.visibleToInstructors();
        if (isVisibleToNoUserGroup) {
            throw new BadRequestException("Each calendar event must be visible to at least one user group.");
        }
    }

    /**
     * Validates that the courseName fields of the given {@link CoursewideCalendarEventDTO} is set to null.
     *
     * @param coursewideCalendarEventDTO the list of DTO to check
     * @throws BadRequestException if the DTO has a non-null courseName
     */
    public static void checkThatEventHasNoCourseNameElseThrow(CoursewideCalendarEventDTO coursewideCalendarEventDTO) {
        boolean hasCourseName = coursewideCalendarEventDTO.courseName() != null;
        if (hasCourseName) {
            throw new BadRequestException("Each calendar event must not have a courseName, since it is assigned automatically.");
        }
    }

    /**
     * Validates a {@link CalendarEventDTO}'s id and extracts the id of a {@link CoursewideCalendarEvent} from it.
     *
     * @param calendarEventId the id string to validate and parse. The expected format consists of a "course-" prefix followed by a non-negative long (e.g., "course-12")
     * @return the extracted id
     * @throws BadRequestException if the format of calendarEventId is invalid
     */
    public static Long checkIfValidIdAndExtractCoursewideCalendarEventIdElseThrow(String calendarEventId) {
        String prefix = "course-";
        if (calendarEventId == null || !calendarEventId.startsWith(prefix)) {
            throw new BadRequestException("Invalid ID format for CoursewideCalendarEvent: must start with 'course-'");
        }
        String numericPart = calendarEventId.substring(prefix.length());
        try {
            return Long.parseLong(numericPart);
        }
        catch (NumberFormatException ex) {
            throw new BadRequestException("Invalid ID format for CoursewideCalendarEvent: After 'course-' must follow a valid number");
        }
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
     * @param eventDTOs the set of calendar events to process
     * @return a set including all unsplit events and the splitting results
     */
    public static Set<CalendarEventDTO> splitEventsSpanningMultipleDaysIfNecessary(Set<CalendarEventDTO> eventDTOs) {
        return eventDTOs.stream().flatMap(event -> splitEventAcrossDaysIfNecessary(event).stream()).collect(Collectors.toSet());
    }

    private static Set<CalendarEventDTO> splitEventAcrossDaysIfNecessary(CalendarEventDTO event) {
        ZonedDateTime start = event.startDate();
        ZonedDateTime end = event.endDate();

        if (end == null || start.toLocalDate().equals(end.toLocalDate())) {
            return Set.of(event);
        }

        HashSet<CalendarEventDTO> splitEvents = new HashSet<>();
        int currentSplitId = 0;
        LocalDate currentDay = start.toLocalDate();
        LocalDate endDay = end.toLocalDate();
        ZoneId zone = start.getZone();

        while (!currentDay.isAfter(endDay)) {
            ZonedDateTime currentStart = currentDay.equals(start.toLocalDate()) ? start : currentDay.atStartOfDay(zone);

            ZonedDateTime currentEnd = currentDay.equals(end.toLocalDate()) ? end : currentDay.atTime(DateUtil.END_OF_DAY).atZone(zone);

            splitEvents.add(
                    new CalendarEventDTO(event.id() + "-" + currentSplitId, event.title(), event.courseName(), currentStart, currentEnd, event.location(), event.facilitator()));

            currentSplitId++;
            currentDay = currentDay.plusDays(1);
        }

        return splitEvents;
    }
}
