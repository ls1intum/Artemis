package de.tum.cit.aet.artemis.calendar.service;

import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.ws.rs.BadRequestException;

import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.calendar.domain.CourseCalendarEvent;
import de.tum.cit.aet.artemis.calendar.dto.CalendarEventDTO;
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
    public Set<CalendarEventDTO> getCourseEventsForUser(User user, ZoneId clientTimeZone) {
        ZonedDateTime now = ZonedDateTime.now(clientTimeZone).withZoneSameInstant(ZoneOffset.UTC);
        Set<Long> activeCourseIds = courseRepository.findActiveCourseIdsForUserGroups(user.getGroups(), now);
        if (activeCourseIds.isEmpty()) {
            return Set.of();
        }

        Set<CourseCalendarEvent> courseCalendarEvents = courseCalendarEventRepository.findAllByCourseIdsWithCourse(activeCourseIds);

        return courseCalendarEvents.stream().map(event -> new CalendarEventDTO(event, clientTimeZone)).collect(Collectors.toSet());
    }

    public Set<CalendarEventDTO> createCourseCalendarEventsOrThrow(List<CalendarEventDTO> calendarEventDTOs, Course course) {
        if (calendarEventDTOs == null || calendarEventDTOs.isEmpty()) {
            throw new BadRequestException("No calendar events provided");
        }

        ZoneId clientTimeZone = calendarEventDTOs.getFirst().startDate().getZone();
        boolean existsInconsistentStartZone = calendarEventDTOs.stream().map(dto -> dto.startDate().getZone()).anyMatch(zone -> !zone.equals(clientTimeZone));
        boolean existsInconsistentEndZone = calendarEventDTOs.stream().map(CalendarEventDTO::endDate).filter(Objects::nonNull).map(ZonedDateTime::getZone)
                .anyMatch(zone -> !zone.equals(clientTimeZone));
        if (existsInconsistentStartZone || existsInconsistentEndZone) {
            throw new BadRequestException("All dates must have a consistent time zone.");
        }

        List<CourseCalendarEvent> courseCalendarEvents = new ArrayList<>();
        for (CalendarEventDTO dto : calendarEventDTOs) {
            if (dto.id() != null) {
                throw new BadRequestException("A new CourseCalendarEvent must not have an id");
            }

            CourseCalendarEvent event = new CourseCalendarEvent();
            event.setCourse(course);
            event.setTitle(dto.title());
            event.setStartDate(dto.startDate());
            event.setEndDate(dto.endDate());
            event.setLocation(dto.location());
            event.setFacilitator(dto.facilitator());
            courseCalendarEvents.add(event);
        }
        List<CourseCalendarEvent> savedEvents = courseCalendarEventRepository.saveAll(courseCalendarEvents);

        return savedEvents.stream().map(event -> new CalendarEventDTO(event, clientTimeZone)).collect(Collectors.toSet());
    }

    public CalendarEventDTO updateCourseCalendarEventOrThrow(CalendarEventDTO calendarEventDTO) {
        Long courseCalendarEventId = checkIfValidIdAndExtractCourseCalendarEventIdOrThrow(calendarEventDTO.id());
        CourseCalendarEvent courseCalendarEvent = courseCalendarEventRepository.findByIdElseThrow(courseCalendarEventId);
        String title = calendarEventDTO.title();
        if (title == null) {
            throw new BadRequestException("A CourseCalendarEvent must have a title");
        }
        ZonedDateTime startDate = calendarEventDTO.startDate();
        if (startDate == null) {
            throw new BadRequestException("A CourseCalendarEvent must have a startDate");
        }
        ZoneId clientTimeZone = calendarEventDTO.startDate().getZone();
        ZonedDateTime endDate = calendarEventDTO.endDate();
        if (endDate != null && !endDate.getZone().equals(clientTimeZone)) {
            throw new BadRequestException("The startDate and endDate of a CourseCalendarEvent must be in the same time zone");
        }
        courseCalendarEvent.setTitle(calendarEventDTO.title());
        courseCalendarEvent.setStartDate(calendarEventDTO.startDate());
        courseCalendarEvent.setEndDate(calendarEventDTO.endDate());
        courseCalendarEvent.setLocation(calendarEventDTO.location());
        courseCalendarEvent.setFacilitator(calendarEventDTO.facilitator());
        courseCalendarEventRepository.save(courseCalendarEvent);
        return new CalendarEventDTO(courseCalendarEvent, clientTimeZone);
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
}
