package de.tum.cit.aet.artemis.calendar.web;

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
import org.springframework.context.annotation.Conditional;
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

import de.tum.cit.aet.artemis.calendar.config.CalendarEnabled;
import de.tum.cit.aet.artemis.calendar.domain.CoursewideCalendarEvent;
import de.tum.cit.aet.artemis.calendar.dto.CalendarEventDTO;
import de.tum.cit.aet.artemis.calendar.dto.CoursewideCalendarEventDTO;
import de.tum.cit.aet.artemis.calendar.repository.CoursewideCalendarEventRepository;
import de.tum.cit.aet.artemis.calendar.service.CalendarEventDTOService;
import de.tum.cit.aet.artemis.calendar.service.CoursewideCalendarEventService;
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

@Conditional(CalendarEnabled.class)
@RestController
@RequestMapping("api/calendar/")
public class CalendarResource {

    private static final Logger log = LoggerFactory.getLogger(CalendarResource.class);

    private final UserRepository userRepository;

    private final TutorialGroupApi tutorialGroupApi;

    private final CourseRepository courseRepository;

    private final AuthorizationCheckService authorizationCheckService;

    private final CoursewideCalendarEventService coursewideCalendarEventService;

    private final CalendarEventDTOService calendarEventDTOService;

    private final CoursewideCalendarEventRepository coursewideCalendarEventRepository;

    public CalendarResource(UserRepository userRepository, TutorialGroupApi tutorialGroupApi, CourseRepository courseRepository,
            AuthorizationCheckService authorizationCheckService, CoursewideCalendarEventService coursewideCalendarEventService, CalendarEventDTOService calendarEventDTOService,
            CoursewideCalendarEventRepository coursewideCalendarEventRepository) {
        this.userRepository = userRepository;
        this.tutorialGroupApi = tutorialGroupApi;
        this.courseRepository = courseRepository;
        this.authorizationCheckService = authorizationCheckService;
        this.coursewideCalendarEventService = coursewideCalendarEventService;
        this.calendarEventDTOService = calendarEventDTOService;
        this.coursewideCalendarEventRepository = coursewideCalendarEventRepository;
    }

    /**
     * GET /calendar-events : gets all {@link CalendarEventDTO}s relevant to the user falling into the requested month
     *
     * @param monthKeys a list of ISO 8601 formatted strings representing months
     * @param timeZone  the clients time zone as IANA time zone ID
     * @return {@code 200 (OK)} with a map of DTOs keyed by day as body. All timestamps of the DTOs are in ISO 8601 format with timezone offset according
     *         to the provided timezone.
     * @throws BadRequestException      {@code 400 (Bad Request)} if the monthKeys are empty or formatted incorrectly or if the timeZone is formatted incorrectly.
     * @throws AccessForbiddenException {@code 403 (Forbidden)} if the user does not have at least student role
     */
    @GetMapping("calendar-events")
    @EnforceAtLeastStudent
    public ResponseEntity<Map<String, List<CalendarEventDTO>>> getCalendarEventsOverlappingMonths(@RequestParam List<String> monthKeys, @RequestParam String timeZone) {
        log.debug("REST request to get calendar events falling into: {}", monthKeys);

        Set<YearMonth> months = calendarEventDTOService.deserializeMonthKeysOrElseThrow(monthKeys);
        ZoneId clientTimeZone = calendarEventDTOService.deserializeZoneIdOrElseThrow(timeZone);
        User user = userRepository.getUserWithGroupsAndAuthorities();

        Set<CalendarEventDTO> tutorialEventReadDTOs = tutorialGroupApi.getTutorialEventsForUser(user, clientTimeZone);
        Set<CalendarEventDTO> courseEventReadDTOs = coursewideCalendarEventService.getCourseEventsForUser(user, clientTimeZone);

        Set<CalendarEventDTO> calendarEventDTOS = Stream.concat(tutorialEventReadDTOs.stream(), courseEventReadDTOs.stream()).collect(Collectors.toSet());
        Set<CalendarEventDTO> filteredDTOs = calendarEventDTOService.filterForEventsOverlappingMonths(calendarEventDTOS, months, clientTimeZone);
        Set<CalendarEventDTO> splitDTOs = calendarEventDTOService.splitEventsSpanningMultipleDaysIfNecessary(filteredDTOs);
        Map<String, List<CalendarEventDTO>> calendarEventDTOsByDay = splitDTOs.stream().collect(Collectors.groupingBy(dto -> dto.startDate().toLocalDate().toString()));

        return ResponseEntity.ok(calendarEventDTOsByDay);
    }

    /**
     * GET /courses/{courseId}/calendar-events : gets all {@link CoursewideCalendarEventDTO}s related to the {@link Course} identified by courseId
     *
     * @param courseId the id of the course for which to get the events
     * @return {@code 200 (OK)} with a list of DTOs as body. All timestamps of the DTOs are in ISO 8601 format in UTC timezone.
     * @throws EntityNotFoundException  {@code 404 (Not Found)} if no course exists for the provided courseId
     * @throws AccessForbiddenException {@code 403 (Forbidden)} if the user does not have at least editor role or is not editor or instructor of the course.
     */
    @GetMapping("courses/{courseId}/coursewide-calendar-events")
    @EnforceAtLeastEditor
    public ResponseEntity<List<CoursewideCalendarEventDTO>> getCalendarEventsForCourse(@PathVariable Long courseId) {
        log.debug("REST request to get calendar events for course with id: {}", courseId);

        Course course = courseRepository.findWithEagerCoursewideCalendarEventsByIdElseThrow(courseId);
        User responsibleUser = userRepository.getUserWithGroupsAndAuthorities();
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, course, responsibleUser);

        List<CoursewideCalendarEventDTO> coursewideCalendarEventDTOS = course.getCoursewideCalendarEvents().stream().map(CoursewideCalendarEventDTO::new).toList();

        return ResponseEntity.ok(coursewideCalendarEventDTOS);
    }

    /**
     * POST /courses/:courseId/coursewide-calendar-events : creates {@link CoursewideCalendarEvent} for the given {@link CoursewideCalendarEventDTO}s
     * and associated them to the {@link Course} identified by courseId.
     *
     * @param courseId                    the id identifying the course to which the new event is supposed to be associated to
     * @param coursewideCalendarEventDTOS a list of DTOs representing the events that should be created
     * @return {@code 200 (OK)} with a set of DTOs representing the created events as body.
     * @throws BadRequestException      {@code 400 (Bad Request)} if any course has an id or courseName (fields will be set automatically) or is visible to none of the course's
     *                                      user groups
     * @throws EntityNotFoundException  {@code 404 (Not Found)} if no course is identified by courseId
     * @throws AccessForbiddenException {@code 403 (Forbidden)} if the user does not have at least student editor role and is not editor or instructor of the course
     */
    @PostMapping("courses/{courseId}/coursewide-calendar-events")
    @EnforceAtLeastEditor
    public ResponseEntity<Set<CoursewideCalendarEventDTO>> createCoursewideCalendarEvents(@PathVariable Long courseId,
            @RequestBody @Valid List<CoursewideCalendarEventDTO> coursewideCalendarEventDTOS) {
        log.debug("REST request to create CoursewideCalendarEvents: {} in course: {}", coursewideCalendarEventDTOS, courseId);

        Course course = courseRepository.findByIdElseThrow(courseId);
        User responsibleUser = userRepository.getUserWithGroupsAndAuthorities();
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, course, responsibleUser);
        coursewideCalendarEventService.checkThatNoEventHasIdElseThrow(coursewideCalendarEventDTOS);
        coursewideCalendarEventService.checkThatNoEventHasACourseNameElseThrow(coursewideCalendarEventDTOS);
        coursewideCalendarEventService.checkThatAllEventsAreAtLeastVisibleToOneUserGroupElseThrow(coursewideCalendarEventDTOS);

        Set<CoursewideCalendarEventDTO> createdCoursewideCalendarEventDTOS = coursewideCalendarEventService.createCoursewideCalendarEventsElseThrow(coursewideCalendarEventDTOS,
                course);

        return ResponseEntity.ok(createdCoursewideCalendarEventDTOS);
    }

    /**
     * PUT /coursewide-calendar-event : updates a {@link CoursewideCalendarEvent} according to the given {@link CoursewideCalendarEventDTO}s.
     *
     * @param coursewideCalendarEventDTO a DTO representing the events that should be updated
     * @return {@code 200 (OK)} with a DTO representing the updated event as body.
     * @throws BadRequestException      {@code 400 (Bad Request)} if the DTO has a wrongly formatted id, has a courseName (will be set
     *                                      automatically according to the course of the event) or if the updated event is supposed to be visible to none of the
     *                                      course's user groups.
     * @throws EntityNotFoundException  {@code 404 (Not Found)} if no event corresponding to the DTO's id exists
     * @throws AccessForbiddenException {@code 403 (Forbidden)} if the user does not have at least editor role and is not editor
     *                                      or instructor of the course associated to the event
     */
    @PutMapping("coursewide-calendar-event")
    @EnforceAtLeastEditor
    public ResponseEntity<CoursewideCalendarEventDTO> updateCoursewideCalendarEvent(@Valid @RequestBody CoursewideCalendarEventDTO coursewideCalendarEventDTO) {
        log.debug("REST request to update CoursewideCalendarEvent: {}", coursewideCalendarEventDTO);

        Long coursewideCalendarEventId = coursewideCalendarEventService.checkIfValidIdAndExtractCoursewideCalendarEventIdOrThrow(coursewideCalendarEventDTO.id());
        CoursewideCalendarEvent event = coursewideCalendarEventRepository.findByIdWithCourseElseThrow(coursewideCalendarEventId);
        Course course = event.getCourse();
        User responsibleUser = userRepository.getUserWithGroupsAndAuthorities();
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, course, responsibleUser);
        coursewideCalendarEventService.checkThatEventHasNoCourseNameElseThrow(coursewideCalendarEventDTO);
        coursewideCalendarEventService.checkThatEventIsAtLeastVisibleToOneUserGroupElseThrow(coursewideCalendarEventDTO);

        CoursewideCalendarEventDTO updatedCoursewideCalendarEventDTO = coursewideCalendarEventService.updateCoursewideCalendarEventElseThrow(event, coursewideCalendarEventDTO);

        return ResponseEntity.ok(updatedCoursewideCalendarEventDTO);
    }

    /**
     * DELETE /coursewide-calendar-event : deletes a {@link CoursewideCalendarEvent} according to the given calendarEventId.
     *
     * @param calendarEventId the id of the calendar event to delete
     * @return {@code 204 (No Content)}
     * @throws BadRequestException      {@code 400 (Bad Request)} if the DTO has a wrongly formatted id (expected format: "course-{coursewideCalendarEventId}")
     * @throws EntityNotFoundException  {@code 404 (Not Found)} if no event exists corresponding to the DTO's id
     * @throws AccessForbiddenException {@code 403 (Forbidden)} if the user does not have at least student editor role and is not editor or instructor of the course associated to
     *                                      the event
     */
    @DeleteMapping("coursewide-calendar-event/{calendarEventId}")
    @EnforceAtLeastEditor
    public ResponseEntity<Void> deleteCoursewideCalendarEvent(@PathVariable String calendarEventId) {
        log.debug("REST request to delete CoursewideCalendarEvent {}", calendarEventId);

        Long coursewideCalendarEventId = coursewideCalendarEventService.checkIfValidIdAndExtractCoursewideCalendarEventIdOrThrow(calendarEventId);
        CoursewideCalendarEvent event = coursewideCalendarEventRepository.findByIdWithCourseElseThrow(coursewideCalendarEventId);
        Course course = event.getCourse();
        User responsibleUser = userRepository.getUserWithGroupsAndAuthorities();
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, course, responsibleUser);

        coursewideCalendarEventRepository.delete(event);

        return ResponseEntity.noContent().build();
    }
}
