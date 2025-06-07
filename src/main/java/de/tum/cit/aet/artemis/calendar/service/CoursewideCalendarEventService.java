package de.tum.cit.aet.artemis.calendar.service;

import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.ws.rs.BadRequestException;

import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.calendar.config.CalendarEnabled;
import de.tum.cit.aet.artemis.calendar.domain.CoursewideCalendarEvent;
import de.tum.cit.aet.artemis.calendar.dto.CalendarEventDTO;
import de.tum.cit.aet.artemis.calendar.dto.CoursewideCalendarEventDTO;
import de.tum.cit.aet.artemis.calendar.repository.CoursewideCalendarEventRepository;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;

@Conditional(CalendarEnabled.class)
@Service
public class CoursewideCalendarEventService {

    private final CoursewideCalendarEventRepository coursewideCalendarEventRepository;

    private final CourseRepository courseRepository;

    public CoursewideCalendarEventService(CoursewideCalendarEventRepository coursewideCalendarEventRepository, CourseRepository courseRepository) {
        this.coursewideCalendarEventRepository = coursewideCalendarEventRepository;
        this.courseRepository = courseRepository;
    }

    /**
     * Retrieves {@code CoursewideCalendarEvent}s as {@code CalendarEventDTO}s fulfilling the following criteria:
     * <ol>
     * <li>User is registered for the course of the event</li>
     * <li>The course of the event is active</li>
     * <li>The event is flagged as visible for at least one of the user's groups</li>
     * </ol>
     *
     * @param user           the user for which the DTOs should be retrieved
     * @param clientTimeZone the client's time zone
     * @return the retrieved calendar events
     */
    public Set<CalendarEventDTO> getCourseEventsForUser(User user, ZoneId clientTimeZone) {
        ZonedDateTime now = ZonedDateTime.now(clientTimeZone).withZoneSameInstant(ZoneOffset.UTC);
        Set<Long> activeCourseIds = courseRepository.findActiveCourseIdsForUserGroups(user.getGroups(), now);
        if (activeCourseIds.isEmpty()) {
            return Set.of();
        }

        Set<CoursewideCalendarEvent> coursewideCalendarEvents = coursewideCalendarEventRepository.findAllByCourseIdsWithCourse(activeCourseIds);

        Set<CoursewideCalendarEvent> visibleEvents = new HashSet<>();
        for (CoursewideCalendarEvent event : coursewideCalendarEvents) {
            boolean userIsStudentInCourse = user.getGroups().contains(event.getCourse().getStudentGroupName());
            boolean userIsTutorInCourse = user.getGroups().contains(event.getCourse().getTeachingAssistantGroupName());
            boolean userIsEditorInCourse = user.getGroups().contains(event.getCourse().getEditorGroupName());
            boolean userIsInstructorInCourse = user.getGroups().contains(event.getCourse().getInstructorGroupName());
            boolean userAllowedToViewEvent = userIsStudentInCourse && event.isVisibleToStudents() || userIsTutorInCourse && event.isVisibleToTutors()
                    || userIsEditorInCourse && event.isVisibleToEditors() || userIsInstructorInCourse && event.isVisibleToInstructors();
            if (userAllowedToViewEvent) {
                visibleEvents.add(event);
            }
        }

        return visibleEvents.stream().map(event -> new CalendarEventDTO(event, clientTimeZone)).collect(Collectors.toSet());
    }

    /**
     * Creates and persists {@link CoursewideCalendarEvent}s for the given {@link Course} from the provided set of {@link CoursewideCalendarEventDTO}s.
     *
     * @param coursewideCalendarEventDTOS the list of DTOs to create
     * @param course                      the course the new events should be related to
     * @return a set of DTOs representing the saved events
     */
    public Set<CoursewideCalendarEventDTO> createCoursewideCalendarEventsElseThrow(List<CoursewideCalendarEventDTO> coursewideCalendarEventDTOS, Course course) {
        List<CoursewideCalendarEvent> coursewideCalendarEvents = new ArrayList<>();
        for (CoursewideCalendarEventDTO dto : coursewideCalendarEventDTOS) {
            CoursewideCalendarEvent event = new CoursewideCalendarEvent();
            event.setCourse(course);
            event.setTitle(dto.title());
            event.setStartDate(dto.startDate());
            event.setEndDate(dto.endDate());
            event.setLocation(dto.location());
            event.setFacilitator(dto.facilitator());
            event.setVisibleToStudents(dto.visibleToStudents());
            event.setVisibleToTutors(dto.visibleToTutors());
            event.setVisibleToEditors(dto.visibleToEditors());
            event.setVisibleToInstructors(dto.visibleToInstructors());
            coursewideCalendarEvents.add(event);
        }
        List<CoursewideCalendarEvent> savedEvents = coursewideCalendarEventRepository.saveAll(coursewideCalendarEvents);
        return savedEvents.stream().map(CoursewideCalendarEventDTO::new).collect(Collectors.toSet());
    }

    /**
     * Updates the given {@link CoursewideCalendarEvent} based on the data provided by th given {@link CoursewideCalendarEventDTO}.
     *
     * @param coursewideCalendarEvent    the event to update
     * @param coursewideCalendarEventDTO the DTO containing updated calendar event data
     * @return a DTO representing the updated event
     */
    public CoursewideCalendarEventDTO updateCoursewideCalendarEventElseThrow(CoursewideCalendarEvent coursewideCalendarEvent,
            CoursewideCalendarEventDTO coursewideCalendarEventDTO) {
        coursewideCalendarEvent.setTitle(coursewideCalendarEventDTO.title());
        coursewideCalendarEvent.setStartDate(coursewideCalendarEventDTO.startDate());
        coursewideCalendarEvent.setEndDate(coursewideCalendarEventDTO.endDate());
        coursewideCalendarEvent.setLocation(coursewideCalendarEventDTO.location());
        coursewideCalendarEvent.setFacilitator(coursewideCalendarEventDTO.facilitator());
        coursewideCalendarEvent.setVisibleToStudents(coursewideCalendarEventDTO.visibleToStudents());
        coursewideCalendarEvent.setVisibleToTutors(coursewideCalendarEventDTO.visibleToTutors());
        coursewideCalendarEvent.setVisibleToEditors(coursewideCalendarEventDTO.visibleToEditors());
        coursewideCalendarEvent.setVisibleToInstructors(coursewideCalendarEventDTO.visibleToInstructors());
        coursewideCalendarEventRepository.save(coursewideCalendarEvent);
        return new CoursewideCalendarEventDTO(coursewideCalendarEvent);
    }

    /**
     * Validates a {@link CalendarEventDTO}'s id and extracts the id of a {@link CoursewideCalendarEvent} from it.
     *
     * @param calendarEventId the id string to validate and parse. The expected format consists of a "course-" prefix followed by a non-negative long (e.g., "course-12")
     * @return the extracted id
     * @throws BadRequestException if the format of calendarEventId is invalid
     */
    public Long checkIfValidIdAndExtractCoursewideCalendarEventIdOrThrow(String calendarEventId) {
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
     * Validates that all id fields of the given {@link CoursewideCalendarEventDTO}s are set to null.
     *
     * @param coursewideCalendarEventDTOS the list of DTOs to check
     * @throws BadRequestException if any DTO has a non-null id
     */
    public void checkThatNoEventHasIdElseThrow(List<CoursewideCalendarEventDTO> coursewideCalendarEventDTOS) {
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
    public void checkThatNoEventHasACourseNameElseThrow(List<CoursewideCalendarEventDTO> coursewideCalendarEventDTOS) {
        coursewideCalendarEventDTOS.forEach(this::checkThatEventHasNoCourseNameElseThrow);
    }

    /**
     * Validates that the courseName fields of the given {@link CoursewideCalendarEventDTO} is set to null.
     *
     * @param coursewideCalendarEventDTO the list of DTO to check
     * @throws BadRequestException if the DTO has a non-null courseName
     */
    public void checkThatEventHasNoCourseNameElseThrow(CoursewideCalendarEventDTO coursewideCalendarEventDTO) {
        boolean hasCourseName = coursewideCalendarEventDTO.courseName() != null;
        if (hasCourseName) {
            throw new BadRequestException("Each calendar events must not have a courseName, since it is assigned automatically.");
        }
    }

    /**
     * Validates that each of the given {@link CoursewideCalendarEventDTO}s is visible to at least one of the following user groups: students, tutors, editors, or instructors.
     *
     * @param coursewideCalendarEventDTOS the list of DTOs to check
     * @throws BadRequestException if any of the DTOs is visible to none of the user groups.
     */
    public void checkThatAllEventsAreAtLeastVisibleToOneUserGroupElseThrow(List<CoursewideCalendarEventDTO> coursewideCalendarEventDTOS) {
        coursewideCalendarEventDTOS.forEach(this::checkThatEventIsAtLeastVisibleToOneUserGroupElseThrow);
    }

    /**
     * Validates that the given {@link CoursewideCalendarEventDTO} is visible to at least one of the following user groups: students, tutors, editors, or instructors.
     *
     * @param coursewideCalendarEventDTO the DTO to check
     * @throws BadRequestException if the DTO is visible to none of the user groups.
     */
    public void checkThatEventIsAtLeastVisibleToOneUserGroupElseThrow(CoursewideCalendarEventDTO coursewideCalendarEventDTO) {
        boolean isVisibleToNoUserGroup = !coursewideCalendarEventDTO.visibleToStudents() && !coursewideCalendarEventDTO.visibleToTutors()
                && !coursewideCalendarEventDTO.visibleToEditors() && !coursewideCalendarEventDTO.visibleToInstructors();
        if (isVisibleToNoUserGroup) {
            throw new BadRequestException("Each calendar event must be visible to at least one user group.");
        }
    }
}
