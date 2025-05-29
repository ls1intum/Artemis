package de.tum.cit.aet.artemis.calendar.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.validation.Valid;
import jakarta.ws.rs.BadRequestException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.calendar.dto.CalendarEventDTO;
import de.tum.cit.aet.artemis.calendar.repository.CourseCalendarEventRepository;
import de.tum.cit.aet.artemis.calendar.service.CalendarEventFilteringService;
import de.tum.cit.aet.artemis.calendar.service.CourseCalendarEventService;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.InternalServerErrorException;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastEditor;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.tutorialgroup.api.TutorialGroupApi;

@Profile(PROFILE_CORE)
@RestController
@RequestMapping("api/calendar/")
public class CalendarResource {

    private static final Logger log = LoggerFactory.getLogger(CalendarResource.class);

    private final UserRepository userRepository;

    private final TutorialGroupApi tutorialGroupApi;

    private final CourseRepository courseRepository;

    private final AuthorizationCheckService authorizationCheckService;

    private final CourseCalendarEventService courseCalendarEventService;

    private final CourseCalendarEventRepository courseCalendarEventRepository;

    private final CalendarEventFilteringService calendarEventFilteringService;

    public CalendarResource(UserRepository userRepository, TutorialGroupApi tutorialGroupApi, CourseRepository courseRepository,
            AuthorizationCheckService authorizationCheckService, CourseCalendarEventService courseCalendarEventService, CourseCalendarEventRepository courseCalendarEventRepository,
            CalendarEventFilteringService calendarEventFilteringService) {
        this.userRepository = userRepository;
        this.tutorialGroupApi = tutorialGroupApi;
        this.courseRepository = courseRepository;
        this.authorizationCheckService = authorizationCheckService;
        this.courseCalendarEventService = courseCalendarEventService;
        this.courseCalendarEventRepository = courseCalendarEventRepository;
        this.calendarEventFilteringService = calendarEventFilteringService;
    }

    /**
     * GET /calendar-events : gets the calendar-events relevant to the user falling into the requested month
     *
     * @param monthKeys a list of ISO 8601 formatted strings representing months
     * @param timeZone  the clients time zone as IANA time zone ID
     * @return ResponseEntity with status 200 (OK) and body containing a map of calendar-events keyed by day (all timestamps in UTC format)
     * @throws BadRequestException {@code 400 (Bad Request)} if the monthKeys are empty or formatted incorrectly or if the timeZone is formatted incorrectly.
     */
    @GetMapping("calendar-events")
    @EnforceAtLeastStudent
    public ResponseEntity<Map<String, List<CalendarEventDTO>>> getCalendarEventsOfMonths(@RequestParam List<String> monthKeys, @RequestParam String timeZone) {
        log.debug("REST request to get calendar events falling into: {}", monthKeys);
        Set<YearMonth> months = calendarEventFilteringService.deserializeMonthKeysOrElseThrow(monthKeys);
        ZoneId clientTimeZone = calendarEventFilteringService.deserializeTimeZoneOrElseThrow(timeZone);

        User user = userRepository.getUserWithGroupsAndAuthorities();
        Set<CalendarEventDTO> tutorialEventDTOs = tutorialGroupApi.getTutorialEventsForUser(user, clientTimeZone);
        Set<CalendarEventDTO> courseEventDTOs = courseCalendarEventService.getCourseEventsForUser(user, clientTimeZone);
        Set<CalendarEventDTO> calendarEventDTOs = Stream.concat(tutorialEventDTOs.stream(), courseEventDTOs.stream()).collect(Collectors.toSet());

        Set<CalendarEventDTO> filteredEventDTOs = calendarEventFilteringService.filterForEventsOverlappingMonths(calendarEventDTOs, months, clientTimeZone);

        Map<String, List<CalendarEventDTO>> eventDTOsByDay = filteredEventDTOs.stream().collect(Collectors.groupingBy(dto -> dto.startDate().toLocalDate().toString()));
        return ResponseEntity.ok(eventDTOsByDay);
    }

    @PostMapping("courses/{courseId}/course-calendar-event")
    @EnforceAtLeastEditor
    public ResponseEntity<CalendarEventDTO> createCourseCalendarEvent(@PathVariable Long courseId, @RequestBody @Valid CalendarEventDTO calendarEventDTO) {
        log.debug("REST request to create CourseCalendarEvent: {} in course: {}", calendarEventDTO, courseId);

        Course course = courseRepository.findByIdElseThrow(courseId);
        User responsibleUser = userRepository.getUserWithGroupsAndAuthorities();
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, course, responsibleUser);

        Set<CalendarEventDTO> createdCalendarEventDtoSet = courseCalendarEventService.createCourseCalendarEventsOrThrow(List.of(calendarEventDTO), course);
        Optional<CalendarEventDTO> createdCalendarEventDto = createdCalendarEventDtoSet.stream().findFirst();
        return createdCalendarEventDto.map(ResponseEntity::ok).orElseThrow(() -> new InternalServerErrorException("Unexpected error while creating CourseCalendarEvent"));
    }

    @PostMapping("courses/{courseId}/course-calendar-events")
    @EnforceAtLeastEditor
    public ResponseEntity<Set<CalendarEventDTO>> createCourseCalendarEvent(@PathVariable Long courseId, @RequestBody @Valid List<CalendarEventDTO> calendarEventDtos) {
        log.debug("REST request to create CourseCalendarEvents: {} in course: {}", calendarEventDtos, courseId);

        Course course = courseRepository.findByIdElseThrow(courseId);
        User responsibleUser = userRepository.getUserWithGroupsAndAuthorities();
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, course, responsibleUser);

        Set<CalendarEventDTO> createdCalendarEventDtos = courseCalendarEventService.createCourseCalendarEventsOrThrow(calendarEventDtos, course);

        return ResponseEntity.ok(createdCalendarEventDtos);
    }

    @PutMapping("courses/{courseId}/course-calendar-event")
    @EnforceAtLeastEditor
    public ResponseEntity<CalendarEventDTO> updateCourseCalendarEvent(@PathVariable Long courseId, @Valid @RequestBody CalendarEventDTO calendarEventDTO) {
        log.debug("REST request to update CourseCalendarEvent: {}", calendarEventDTO);

        Course course = courseRepository.findByIdElseThrow(courseId);
        User responsibleUser = userRepository.getUserWithGroupsAndAuthorities();
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, course, responsibleUser);

        CalendarEventDTO updatedCalendarEventDTO = courseCalendarEventService.updateCourseCalendarEventOrThrow(calendarEventDTO);

        return ResponseEntity.ok(updatedCalendarEventDTO);
    }

    @DeleteMapping("courses/{courseId}/course-calendar-event/{courseCalendarEventId}")
    @EnforceAtLeastEditor
    public ResponseEntity<Void> deleteCourseCalendarEvent(@PathVariable Long courseId, @PathVariable Long courseCalendarEventId) {
        log.debug("REST request to delete CourseCalendarEvent {} from course {}", courseCalendarEventId, courseId);

        Course course = courseRepository.findByIdElseThrow(courseId);
        User responsibleUser = userRepository.getUserWithGroupsAndAuthorities();
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, course, responsibleUser);

        courseCalendarEventRepository.deleteById(courseCalendarEventId);

        return ResponseEntity.noContent().build();
    }
}
