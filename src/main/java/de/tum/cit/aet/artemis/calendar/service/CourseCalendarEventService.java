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

import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.calendar.domain.CourseCalendarEvent;
import de.tum.cit.aet.artemis.calendar.dto.CalendarEventDTO;
import de.tum.cit.aet.artemis.calendar.dto.CourseCalendarEventDTO;
import de.tum.cit.aet.artemis.calendar.repository.CourseCalendarEventRepository;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;

@Service
public class CourseCalendarEventService {

    private final CourseCalendarEventRepository courseCalendarEventRepository;

    private final CourseRepository courseRepository;

    public CourseCalendarEventService(CourseCalendarEventRepository courseCalendarEventRepository, CourseRepository courseRepository) {
        this.courseCalendarEventRepository = courseCalendarEventRepository;
        this.courseRepository = courseRepository;
    }

    /**
     * Retrieves {@code CourseCalendarEvent}s as {@code CalendarEventDTO}s fulfilling the following criteria:
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

        Set<CourseCalendarEvent> courseCalendarEvents = courseCalendarEventRepository.findAllByCourseIdsWithCourse(activeCourseIds);

        Set<CourseCalendarEvent> visibleEvents = new HashSet<>();
        for (CourseCalendarEvent event : courseCalendarEvents) {
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
     * Creates and persists {@link CourseCalendarEvent}s for the given {@link Course} from the provided set of {@link CourseCalendarEventDTO}s.
     *
     * @param courseCalendarEventDTOS the list of DTOs to create
     * @param course                  the course the new events should be related to
     * @return a set of DTOs representing the saved events
     */
    public Set<CourseCalendarEventDTO> createCourseCalendarEventsElseThrow(List<CourseCalendarEventDTO> courseCalendarEventDTOS, Course course) {
        List<CourseCalendarEvent> courseCalendarEvents = new ArrayList<>();
        for (CourseCalendarEventDTO dto : courseCalendarEventDTOS) {
            CourseCalendarEvent event = new CourseCalendarEvent();
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
            courseCalendarEvents.add(event);
        }
        List<CourseCalendarEvent> savedEvents = courseCalendarEventRepository.saveAll(courseCalendarEvents);
        return savedEvents.stream().map(CourseCalendarEventDTO::new).collect(Collectors.toSet());
    }

    /**
     * Updates an existing {@link CourseCalendarEvent} based on the data provided by th given {@link CourseCalendarEventDTO}.
     *
     * @param courseCalendarEventDTO the DTO containing updated calendar event data
     * @return a DTO representing the updated event
     */
    public CourseCalendarEventDTO updateCourseCalendarEventElseThrow(CourseCalendarEvent courseCalendarEvent, CourseCalendarEventDTO courseCalendarEventDTO) {
        courseCalendarEvent.setTitle(courseCalendarEventDTO.title());
        courseCalendarEvent.setStartDate(courseCalendarEventDTO.startDate());
        courseCalendarEvent.setEndDate(courseCalendarEventDTO.endDate());
        courseCalendarEvent.setLocation(courseCalendarEventDTO.location());
        courseCalendarEvent.setFacilitator(courseCalendarEventDTO.facilitator());
        courseCalendarEvent.setVisibleToStudents(courseCalendarEventDTO.visibleToStudents());
        courseCalendarEvent.setVisibleToTutors(courseCalendarEventDTO.visibleToTutors());
        courseCalendarEvent.setVisibleToEditors(courseCalendarEventDTO.visibleToEditors());
        courseCalendarEvent.setVisibleToInstructors(courseCalendarEventDTO.visibleToInstructors());
        courseCalendarEventRepository.save(courseCalendarEvent);
        return new CourseCalendarEventDTO(courseCalendarEvent);
    }

    /**
     * Validates a {@link CalendarEventDTO}'s id and extracts the id of a {@link CourseCalendarEvent} from it.
     *
     * @param calendarEventId the id string to validate and parse. The expected format consists of a "course-" prefix followed by a non-negative long (e.g., "course-12")
     * @return the extracted id
     * @throws BadRequestException if the format of calendarEventId is invalid
     */
    public Long checkIfValidIdAndExtractCourseCalendarEventIdOrThrow(String calendarEventId) {
        String prefix = "course-";
        if (calendarEventId == null || !calendarEventId.startsWith(prefix)) {
            throw new BadRequestException("Invalid ID format for CourseCalendarEvent: must start with 'course-'");
        }
        String numericPart = calendarEventId.substring(prefix.length());
        try {
            return Long.parseLong(numericPart);
        }
        catch (NumberFormatException ex) {
            throw new BadRequestException("Invalid ID format for CourseCalendarEvent: After 'course-' must follow a valid number");
        }
    }

    /**
     * Validates that all id fields of the given {@link CourseCalendarEventDTO}s are set to null.
     *
     * @param courseCalendarEventDTOS the list of DTOs to check
     * @throws BadRequestException if any DTO has a non-null id
     */
    public void checkThatNoEventHasIdElseThrow(List<CourseCalendarEventDTO> courseCalendarEventDTOS) {
        boolean anyHasId = courseCalendarEventDTOS.stream().anyMatch(dto -> dto.id() != null);
        if (anyHasId) {
            throw new BadRequestException("New calendar events must not have an id, since ids are assigned automatically.");
        }
    }

    /**
     * Validates that all courseName fields of the given {@link CourseCalendarEventDTO}s are set to null.
     *
     * @param courseCalendarEventDTOs the list of DTOs to check
     * @throws BadRequestException if any DTO has a non-null courseName
     */
    public void checkThatNoEventHasACourseNameElseThrow(List<CourseCalendarEventDTO> courseCalendarEventDTOs) {
        courseCalendarEventDTOs.forEach(this::checkThatEventHasNoCourseNameElseThrow);
    }

    /**
     * Validates that the courseName fields of the given {@link CourseCalendarEventDTO} is set to null.
     *
     * @param courseCalendarEventDTO the list of DTO to check
     * @throws BadRequestException if the DTO has a non-null courseName
     */
    public void checkThatEventHasNoCourseNameElseThrow(CourseCalendarEventDTO courseCalendarEventDTO) {
        boolean hasCourseName = courseCalendarEventDTO.courseName() != null;
        if (hasCourseName) {
            throw new BadRequestException("Each calendar events must not have a courseName, since it is assigned automatically.");
        }
    }

    /**
     * Validates that each of the given {@link CourseCalendarEventDTO}s is visible to at least one of the following user groups: students, tutors, editors, or instructors.
     *
     * @param courseCalendarEventDTOs the list of DTOs to check
     * @throws BadRequestException if any of the DTOs is visible to none of the user groups.
     */
    public void checkThatAllEventsAreAtLeastVisibleToOneUserGroupElseThrow(List<CourseCalendarEventDTO> courseCalendarEventDTOs) {
        courseCalendarEventDTOs.forEach(this::checkThatEventIsAtLeastVisibleToOneUserGroupElseThrow);
    }

    /**
     * Validates that the given {@link CourseCalendarEventDTO} is visible to at least one of the following user groups: students, tutors, editors, or instructors.
     *
     * @param courseCalendarEventDTO the DTO to check
     * @throws BadRequestException if the DTO is visible to none of the user groups.
     */
    public void checkThatEventIsAtLeastVisibleToOneUserGroupElseThrow(CourseCalendarEventDTO courseCalendarEventDTO) {
        boolean isVisibleToNoUserGroup = !courseCalendarEventDTO.visibleToStudents() && !courseCalendarEventDTO.visibleToTutors() && !courseCalendarEventDTO.visibleToEditors()
                && !courseCalendarEventDTO.visibleToInstructors();
        if (isVisibleToNoUserGroup) {
            throw new BadRequestException("Each calendar event must be visible to at least one user group.");
        }
    }
}
