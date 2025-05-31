package de.tum.cit.aet.artemis.calendar.service;

import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.ws.rs.BadRequestException;

import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.calendar.dto.CalendarEventReadDTO;

@Service
public class CalendarEventFilteringService {

    public Set<CalendarEventReadDTO> filterForEventsOverlappingMonths(Set<CalendarEventReadDTO> eventDTOs, Set<YearMonth> months, ZoneId clientZone) {
        return eventDTOs.stream().filter(eventDTO -> months.stream().anyMatch(month -> areMonthAndEventOverlapping(month, eventDTO, clientZone))).collect(Collectors.toSet());
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

    public ZoneId deserializeTimeZoneOrElseThrow(String timeZone) {
        try {
            return ZoneId.of(timeZone);
        }
        catch (Exception exception) {
            throw new BadRequestException("Invalid time zone format. Expected IANA time zone ID.");
        }
    }
}
