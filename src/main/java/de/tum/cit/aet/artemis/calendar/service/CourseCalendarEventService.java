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
import de.tum.cit.aet.artemis.calendar.dto.CalendarEventManageDTO;
import de.tum.cit.aet.artemis.calendar.repository.CourseCalendarEventRepository;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
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
     * Retrieves {@code CourseCalendarEvent}s as {@code CalendarEventReadDTO}s fulfilling the following criteria:
     * <ol>
     * <li>User is registered for the course of the event</li>
     * <li>The course of the event is active</li>
     * <li>The event is flagged as visible for at least one of the user's groups</li>
     * </ol>
     *
     * @param user           the user for which the DTOs should be retrieved
     * @param clientTimeZone the client's time zone
     * @return a set of {@code CalendarEventReadDTO}s representing {@code CourseCalendarEvent}s relevant for the user
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
     * Creates and persists {@link CourseCalendarEvent}s for the given course from the provided set of {@link CalendarEventManageDTO}s.
     *
     * @param calendarEventManageDTOS the list of DTOs to create
     * @param course                  the course to associate the new {@link CourseCalendarEvent}s with
     * @return a set of {@link CalendarEventManageDTO}s representing the saved events
     * @throws BadRequestException if any DTO contains an ID or courseName (fields are set automatically) or is not visible to at least one user group
     */
    public Set<CalendarEventManageDTO> createCourseCalendarEventsOrThrow(List<CalendarEventManageDTO> calendarEventManageDTOS, Course course) {
        checkThatNoEventHasIdOrThrow(calendarEventManageDTOS);
        checkThatNoEventHasACourseNameOrThrow(calendarEventManageDTOS);
        checkThatAllEventsAreAtLeastVisibleToOneUserGroupOrThrow(calendarEventManageDTOS);

        List<CourseCalendarEvent> courseCalendarEvents = new ArrayList<>();
        for (CalendarEventManageDTO dto : calendarEventManageDTOS) {
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

        return savedEvents.stream().map(CalendarEventManageDTO::new).collect(Collectors.toSet());
    }

    /**
     * Updates an existing {@link CourseCalendarEvent} based on the data in the provided {@link CalendarEventManageDTO}.
     *
     * @param calendarEventManageDTO the DTO containing updated calendar event data
     * @return a {@link CalendarEventManageDTO} representing the updated event
     * @throws BadRequestException     if the id of the DTO is invalid, if the DTO has a courseName (the field is set automatically according to its current course) or if the
     *                                     updated event is supposed to not be visible to any user group
     * @throws EntityNotFoundException if no event corresponding to the DTO's id exists
     */
    public CalendarEventManageDTO updateCourseCalendarEventOrThrow(CourseCalendarEvent courseCalendarEvent, CalendarEventManageDTO calendarEventManageDTO) {
        checkThatEventHasNoCourseNameOrThrow(calendarEventManageDTO);
        checkThatEventIsAtLeastVisibleToOneUserGroupOrThrow(calendarEventManageDTO);

        courseCalendarEvent.setTitle(calendarEventManageDTO.title());
        courseCalendarEvent.setStartDate(calendarEventManageDTO.startDate());
        courseCalendarEvent.setEndDate(calendarEventManageDTO.endDate());
        courseCalendarEvent.setLocation(calendarEventManageDTO.location());
        courseCalendarEvent.setFacilitator(calendarEventManageDTO.facilitator());
        courseCalendarEvent.setVisibleToStudents(calendarEventManageDTO.visibleToStudents());
        courseCalendarEvent.setVisibleToTutors(calendarEventManageDTO.visibleToTutors());
        courseCalendarEvent.setVisibleToEditors(calendarEventManageDTO.visibleToEditors());
        courseCalendarEvent.setVisibleToInstructors(calendarEventManageDTO.visibleToInstructors());
        courseCalendarEventRepository.save(courseCalendarEvent);

        return new CalendarEventManageDTO(courseCalendarEvent);
    }

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

    private void checkThatNoEventHasIdOrThrow(List<CalendarEventManageDTO> calendarEventManageDTOS) {
        boolean anyHasId = calendarEventManageDTOS.stream().anyMatch(dto -> dto.id() != null);
        if (anyHasId) {
            throw new BadRequestException("New calendar events must not have an id, since ids are assigned automatically.");
        }
    }

    private void checkThatNoEventHasACourseNameOrThrow(List<CalendarEventManageDTO> calendarEventManageDTOS) {
        calendarEventManageDTOS.forEach(this::checkThatEventHasNoCourseNameOrThrow);
    }

    private void checkThatEventHasNoCourseNameOrThrow(CalendarEventManageDTO calendarEventManageDTO) {
        boolean hasCourseName = calendarEventManageDTO.courseName() != null;
        if (hasCourseName) {
            throw new BadRequestException("Each calendar events must not have a courseName, since it is assigned automatically.");
        }
    }

    private void checkThatAllEventsAreAtLeastVisibleToOneUserGroupOrThrow(List<CalendarEventManageDTO> calendarEventManageDTOS) {
        calendarEventManageDTOS.forEach(this::checkThatEventIsAtLeastVisibleToOneUserGroupOrThrow);
    }

    private void checkThatEventIsAtLeastVisibleToOneUserGroupOrThrow(CalendarEventManageDTO calendarEventManageDTO) {
        boolean isVisibleToNoUserGroup = !calendarEventManageDTO.visibleToStudents() && !calendarEventManageDTO.visibleToTutors() && !calendarEventManageDTO.visibleToEditors()
                && !calendarEventManageDTO.visibleToInstructors();
        if (isVisibleToNoUserGroup) {
            throw new BadRequestException("Each calendar event must be visible to at least one user group.");
        }
    }
}
