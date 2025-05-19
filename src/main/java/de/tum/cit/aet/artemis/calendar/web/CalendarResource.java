package de.tum.cit.aet.artemis.calendar.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.calendar.dto.CalendarEventDTO;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.tutorialgroup.api.TutorialGroupApi;

@Profile(PROFILE_CORE)
@RestController
@RequestMapping("api/calendar/")
public class CalendarResource {

    private static final Logger log = LoggerFactory.getLogger(CalendarResource.class);

    private final UserRepository userRepository;

    private final TutorialGroupApi tutorialGroupApi;

    public CalendarResource(UserRepository userRepository, TutorialGroupApi tutorialGroupApi) {
        this.userRepository = userRepository;
        this.tutorialGroupApi = tutorialGroupApi;
    }

    /**
     * GET /calendar-events : gets the calendar-events relevant to the user falling into the requested month
     *
     * @param monthKeys a list of ISO 8601 formatted strings representing months
     * @param timeZone  the clients time zone as IANA time zone ID
     * @return ResponseEntity with status 200 (OK) and body containing a map of calendar-events keyed by day (all timestamps in UTC format)
     */
    @GetMapping("calendar-events")
    public ResponseEntity<Map<ZonedDateTime, List<CalendarEventDTO>>> getCalendarEventsOfMonths(@RequestParam List<String> monthKeys, @RequestParam String timeZone) {
        log.debug("REST request to get calendar events falling into: {}", monthKeys);
        var user = userRepository.getUserWithGroupsAndAuthorities();

        // get tutorialEventDTOs for requested months
        var calendarEventDTOs = tutorialGroupApi.getTutorialEventsForUserFallingIntoMonthsOrElseThrough(user, monthKeys, timeZone);

        // group tutorialEventDTOs by day
        Map<ZonedDateTime, List<CalendarEventDTO>> eventDTOsByDay = calendarEventDTOs.stream().collect(Collectors.groupingBy(dto -> dto.start().truncatedTo(ChronoUnit.DAYS)));

        return ResponseEntity.ok(eventDTOsByDay);
    }
}
