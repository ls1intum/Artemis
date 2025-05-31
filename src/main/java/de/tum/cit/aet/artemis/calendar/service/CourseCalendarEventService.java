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
import de.tum.cit.aet.artemis.calendar.dto.CalendarEventReadDTO;
import de.tum.cit.aet.artemis.calendar.dto.CalendarEventWriteDTO;
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
     *
     * <ol>
     * <li>User is registered for the course of the event</li>
     * <li>The course of the event is active</li>
     * </ol>
     *
     * @param user           the user for which the DTOs should be retrieved
     * @param clientTimeZone the client's time zone
     * @return a set of {@code CalendarEventDTO}s representing {@code CourseCalendarEvent}s relevant for the user
     */
    public Set<CalendarEventReadDTO> getCourseEventsForUser(User user, ZoneId clientTimeZone) {
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

        return visibleEvents.stream().map(event -> new CalendarEventReadDTO(event, clientTimeZone)).collect(Collectors.toSet());
    }

    public Set<CalendarEventWriteDTO> createCourseCalendarEventsOrThrow(List<CalendarEventWriteDTO> calendarEventWriteDTOs, Course course) {
        checkThatNoEventHasIdOrThrow(calendarEventWriteDTOs);

        List<CourseCalendarEvent> courseCalendarEvents = new ArrayList<>();
        for (CalendarEventWriteDTO dto : calendarEventWriteDTOs) {
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

        return savedEvents.stream().map(CalendarEventWriteDTO::new).collect(Collectors.toSet());
    }

    public CalendarEventWriteDTO updateCourseCalendarEventOrThrow(CalendarEventWriteDTO calendarEventWriteDTO) {
        Long courseCalendarEventId = checkIfValidIdAndExtractCourseCalendarEventIdOrThrow(calendarEventWriteDTO.id());
        CourseCalendarEvent courseCalendarEvent = courseCalendarEventRepository.findByIdElseThrow(courseCalendarEventId);

        courseCalendarEvent.setTitle(calendarEventWriteDTO.title());
        courseCalendarEvent.setStartDate(calendarEventWriteDTO.startDate());
        courseCalendarEvent.setEndDate(calendarEventWriteDTO.endDate());
        courseCalendarEvent.setLocation(calendarEventWriteDTO.location());
        courseCalendarEvent.setFacilitator(calendarEventWriteDTO.facilitator());
        courseCalendarEvent.setVisibleToStudents(calendarEventWriteDTO.visibleToStudents());
        courseCalendarEvent.setVisibleToTutors(calendarEventWriteDTO.visibleToTutors());
        courseCalendarEvent.setVisibleToEditors(calendarEventWriteDTO.visibleToEditors());
        courseCalendarEvent.setVisibleToInstructors(calendarEventWriteDTO.visibleToInstructors());
        courseCalendarEventRepository.save(courseCalendarEvent);

        return new CalendarEventWriteDTO(courseCalendarEvent);
    }

    public void deleteCourseCalendarEventOrThrow(Long courseCalendarEventId, Course course) {
        CourseCalendarEvent courseCalendarEvent = courseCalendarEventRepository.findByIdElseThrow(courseCalendarEventId);
        if (!courseCalendarEvent.getCourse().equals(course)) {
            throw new BadRequestException("The calendar event does not belong to the specified course.");
        }

        courseCalendarEventRepository.delete(courseCalendarEvent);
    }

    private Long checkIfValidIdAndExtractCourseCalendarEventIdOrThrow(String calendarEventDtoId) {
        String prefix = "course-";
        if (calendarEventDtoId == null || !calendarEventDtoId.startsWith(prefix)) {
            throw new BadRequestException("Invalid ID format for CourseCalendarEvent: must start with 'course-'");
        }
        String numericPart = calendarEventDtoId.substring(prefix.length());
        try {
            return Long.parseLong(numericPart);
        }
        catch (NumberFormatException ex) {
            throw new BadRequestException("Invalid ID format for CourseCalendarEvent: After 'course-' must follow a valid number");
        }
    }

    private void checkThatNoEventHasIdOrThrow(List<CalendarEventWriteDTO> calendarEventWriteDTOS) {
        boolean anyHasId = calendarEventWriteDTOS.stream().anyMatch(dto -> dto.id() != null);
        if (anyHasId) {
            throw new BadRequestException("New calendar events must not have an id.");
        }
    }
}
