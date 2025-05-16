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
import de.tum.cit.aet.artemis.tutorialgroup.service.TutorialGroupService;

@Profile(PROFILE_CORE)
@RestController
@RequestMapping("api/calendar/")
public class CalendarResource {

    private static final Logger log = LoggerFactory.getLogger(CalendarResource.class);

    private final UserRepository userRepository;

    private final TutorialGroupService tutorialGroupService;

    public CalendarResource(UserRepository userRepository, TutorialGroupService tutorialGroupService) {
        this.userRepository = userRepository;
        this.tutorialGroupService = tutorialGroupService;
    }

    /**
     * GET /calendar-events : gets the calendar-events relevant to the user falling into the requested month
     *
     * @param monthKeys - a ISO 8601 formatted string representing a month
     * @return ResponseEntity with status 200 (OK) and body containing a map of calendar-events keyed by day
     */
    @GetMapping("calendar-events")
    public ResponseEntity<Map<ZonedDateTime, List<CalendarEventDTO>>> getCalendarEventsOfMonths(@RequestParam List<String> monthKeys) {
        log.debug("REST request to get calendar events falling into: {}", monthKeys);
        var user = userRepository.getUserWithGroupsAndAuthorities();

        // get tutorialEventDTOs for requested months
        var calendarEventDTOs = tutorialGroupService.getTutorialEventsForUserFallingIntoMonthsOrElseThrough(user, monthKeys);

        // group tutorialEventDTOs by day
        Map<ZonedDateTime, List<CalendarEventDTO>> eventDTOsByDay = calendarEventDTOs.stream()
                .collect(Collectors.groupingBy(eventDTO -> eventDTO.start().truncatedTo(ChronoUnit.DAYS)));

        return ResponseEntity.ok(eventDTOsByDay);
    }
}
