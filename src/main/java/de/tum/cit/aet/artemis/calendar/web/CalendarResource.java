package de.tum.cit.aet.artemis.calendar.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
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

import de.tum.cit.aet.artemis.calendar.domain.CourseCalendarEvent;
import de.tum.cit.aet.artemis.calendar.dto.CalendarEventDTO;
import de.tum.cit.aet.artemis.calendar.dto.CalendarEventManageDTO;
import de.tum.cit.aet.artemis.calendar.repository.CourseCalendarEventRepository;
import de.tum.cit.aet.artemis.calendar.service.CalendarEventService;
import de.tum.cit.aet.artemis.calendar.service.CourseCalendarEventService;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
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

    private final CalendarEventService calendarEventService;

    private final CourseCalendarEventRepository courseCalendarEventRepository;

    public CalendarResource(UserRepository userRepository, TutorialGroupApi tutorialGroupApi, CourseRepository courseRepository,
            AuthorizationCheckService authorizationCheckService, CourseCalendarEventService courseCalendarEventService, CalendarEventService calendarEventService,
            CourseCalendarEventRepository courseCalendarEventRepository) {
        this.userRepository = userRepository;
        this.tutorialGroupApi = tutorialGroupApi;
        this.courseRepository = courseRepository;
        this.authorizationCheckService = authorizationCheckService;
        this.courseCalendarEventService = courseCalendarEventService;
        this.calendarEventService = calendarEventService;
        this.courseCalendarEventRepository = courseCalendarEventRepository;
    }

    // TODO: extract all validation into methods if not already happened and call in REST endpoints -> service methods should call no validation methods themselves

    /**
     * GET /calendar-events : gets all calendar-events relevant to the user falling into the requested month
     *
     * @param monthKeys a list of ISO 8601 formatted strings representing months
     * @param timeZone  the clients time zone as IANA time zone ID
     * @return {@code 200 (OK)} with a map of {@link CalendarEventDTO}s keyed by day as body. All timestamps of the DTOs are in ISO 8601 format with timezone offset according
     *         to the provided timezone.
     * @throws BadRequestException      {@code 400 (Bad Request)} if the monthKeys are empty or formatted incorrectly or if the timeZone is formatted incorrectly.
     * @throws AccessForbiddenException {@code 403 (Forbidden)} if the user does not have at least student role
     */
    @GetMapping("calendar-events")
    @EnforceAtLeastStudent
    public ResponseEntity<Map<String, List<CalendarEventDTO>>> getCalendarEventsOverlappingMonths(@RequestParam List<String> monthKeys, @RequestParam String timeZone) {
        log.debug("REST request to get calendar events falling into: {}", monthKeys);

        Set<YearMonth> months = calendarEventService.deserializeMonthKeysOrElseThrow(monthKeys);
        ZoneId clientTimeZone = calendarEventService.deserializeTimeZoneOrElseThrow(timeZone);

        User user = userRepository.getUserWithGroupsAndAuthorities();

        Set<CalendarEventDTO> tutorialEventReadDTOs = tutorialGroupApi.getTutorialEventsForUser(user, clientTimeZone);
        Set<CalendarEventDTO> courseEventReadDTOs = courseCalendarEventService.getCourseEventsForUser(user, clientTimeZone);
        Set<CalendarEventDTO> calendarEventDTOS = Stream.concat(tutorialEventReadDTOs.stream(), courseEventReadDTOs.stream()).collect(Collectors.toSet());
        Set<CalendarEventDTO> filteredDTOs = calendarEventService.filterForEventsOverlappingMonths(calendarEventDTOS, months, clientTimeZone);
        Set<CalendarEventDTO> splitDTOs = calendarEventService.splitEventsSpanningMultipleDaysIfNecessary(filteredDTOs);

        Map<String, List<CalendarEventDTO>> calendarEventReadDTOsByDay = splitDTOs.stream().collect(Collectors.groupingBy(dto -> dto.startDate().toLocalDate().toString()));
        return ResponseEntity.ok(calendarEventReadDTOsByDay);
    }

    /**
     * GET /courses/{courseId}/calendar-events : gets all calendar-events related to the {@link Course} identified by courseId
     *
     * @param courseId the id of the course for which to get the events
     * @return {@code 200 (OK)} with a list of {@link CalendarEventDTO}s as body. All timestamps of the DTOs are in ISO 8601 format in UTC timezone.
     * @throws EntityNotFoundException  {@code 404 (Not Found)} if no course exists for the provided courseId
     * @throws AccessForbiddenException {@code 403 (Forbidden)} if the user does not have at least editor role or is not editor or instructor of the course.
     */
    @GetMapping("courses/{courseId}/course-calendar-events")
    @EnforceAtLeastEditor
    public ResponseEntity<List<CalendarEventManageDTO>> getCalendarEventsForCourse(@PathVariable Long courseId) {
        log.debug("REST request to get calendar events for course with id: {}", courseId);

        Course course = courseRepository.findWithEagerCourseCalendarEventsByIdElseThrow(courseId);
        User responsibleUser = userRepository.getUserWithGroupsAndAuthorities();
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, course, responsibleUser);

        List<CalendarEventManageDTO> courseCalendarEventDTOs = course.getCourseCalendarEvents().stream().map(CalendarEventManageDTO::new).toList();

        return ResponseEntity.ok(courseCalendarEventDTOs);
    }

    /**
     * POST /courses/:courseId/course-calendar-events : creates {@link CourseCalendarEvent} for the given {@link CalendarEventManageDTO}s
     * and associated them to the {@link Course} identified by courseId.
     *
     * @param courseId                the id identifying the course to which the new event is supposed to be associated to
     * @param calendarEventManageDTOS a list of DTOs representing the events that should be created
     * @return {@code 200 (OK)} with a set of {@link CalendarEventManageDTO} representing the created events as body.
     * @throws BadRequestException      {@code 400 (Bad Request)} if any course has an id or courseName (fields will be set automatically) or is visible to none of the course's
     *                                      user groups
     * @throws EntityNotFoundException  {@code 404 (Not Found)} if no {@link Course} is identified by courseId
     * @throws AccessForbiddenException {@code 403 (Forbidden)} if the user does not have at least student editor role and is not editor or instructor of the course
     */
    @PostMapping("courses/{courseId}/course-calendar-events")
    @EnforceAtLeastEditor
    public ResponseEntity<Set<CalendarEventManageDTO>> createCourseCalendarEvents(@PathVariable Long courseId,
            @RequestBody @Valid List<CalendarEventManageDTO> calendarEventManageDTOS) {
        log.debug("REST request to create CourseCalendarEvents: {} in course: {}", calendarEventManageDTOS, courseId);

        Course course = courseRepository.findByIdElseThrow(courseId);
        User responsibleUser = userRepository.getUserWithGroupsAndAuthorities();
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, course, responsibleUser);

        Set<CalendarEventManageDTO> createdCalendarEventManageDTOS = courseCalendarEventService.createCourseCalendarEventsOrThrow(calendarEventManageDTOS, course);

        return ResponseEntity.ok(createdCalendarEventManageDTOS);
    }

    /**
     * PUT /course-calendar-event : updates a {@link CourseCalendarEvent} according to the given {@link CalendarEventManageDTO}s.
     *
     * @param calendarEventManageDTO a DTO representing the events that should be updated
     * @return {@code 200 (OK)} with a {@link CalendarEventManageDTO} representing the updated event as body.
     * @throws BadRequestException      {@code 400 (Bad Request)} if the DTO has a wrongly formatted id, has a courseName (will be set automatically
     *                                      according to the course of the event) or if the updated event is supposed to be visible to none of the course's user groups.
     * @throws EntityNotFoundException  {@code 404 (Not Found)} if no {@link CourseCalendarEvent} is identified by DTO's id
     * @throws AccessForbiddenException {@code 403 (Forbidden)} if the user does not have at least student editor role and is not editor or instructor of the course associated to
     *                                      the event
     */
    @PutMapping("course-calendar-event")
    @EnforceAtLeastEditor
    public ResponseEntity<CalendarEventManageDTO> updateCourseCalendarEvent(@Valid @RequestBody CalendarEventManageDTO calendarEventManageDTO) {
        log.debug("REST request to update CourseCalendarEvent: {}", calendarEventManageDTO);

        Long courseCalendarEventId = courseCalendarEventService.checkIfValidIdAndExtractCourseCalendarEventIdOrThrow(calendarEventManageDTO.id());
        CourseCalendarEvent event = courseCalendarEventRepository.findByIdWithCourse(courseCalendarEventId).orElseThrow(EntityNotFoundException::new);
        Course course = event.getCourse();
        User responsibleUser = userRepository.getUserWithGroupsAndAuthorities();
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, course, responsibleUser);

        CalendarEventManageDTO updatedCalendarEventManageDTO = courseCalendarEventService.updateCourseCalendarEventOrThrow(event, calendarEventManageDTO);

        return ResponseEntity.ok(updatedCalendarEventManageDTO);
    }

    /**
     * DELETE /course-calendar-event : deletes a {@link CourseCalendarEvent} according to the given calendarEventId.
     *
     * @param calendarEventId the id of the calendar event to delete
     * @return {@code 204 (No Content)}
     * @throws BadRequestException      {@code 400 (Bad Request)} if the DTO has a wrongly formatted id (expected format: "course-{courseCalendarEventId}")
     * @throws EntityNotFoundException  {@code 404 (Not Found)} if no {@link CourseCalendarEvent} is identified by DTO's id
     * @throws AccessForbiddenException {@code 403 (Forbidden)} if the user does not have at least student editor role and is not editor or instructor of the course associated to
     *                                      the event
     */
    @DeleteMapping("course-calendar-event/{calendarEventId}")
    @EnforceAtLeastEditor
    public ResponseEntity<Void> deleteCourseCalendarEvent(@PathVariable String calendarEventId) {
        log.debug("REST request to delete CourseCalendarEvent {}", calendarEventId);

        Long courseCalendarEventId = courseCalendarEventService.checkIfValidIdAndExtractCourseCalendarEventIdOrThrow(calendarEventId);
        CourseCalendarEvent event = courseCalendarEventRepository.findByIdWithCourse(courseCalendarEventId).orElseThrow(EntityNotFoundException::new);
        Course course = event.getCourse();
        User responsibleUser = userRepository.getUserWithGroupsAndAuthorities();
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, course, responsibleUser);

        courseCalendarEventRepository.delete(event);

        return ResponseEntity.noContent().build();
    }
}
