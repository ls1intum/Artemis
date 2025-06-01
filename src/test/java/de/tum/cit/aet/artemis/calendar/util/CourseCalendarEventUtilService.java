package de.tum.cit.aet.artemis.calendar.util;

import static tech.jhipster.config.JHipsterConstants.SPRING_PROFILE_TEST;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.calendar.domain.CourseCalendarEvent;
import de.tum.cit.aet.artemis.calendar.repository.CourseCalendarEventRepository;
import de.tum.cit.aet.artemis.core.domain.Course;

@Service
@Profile(SPRING_PROFILE_TEST)
public class CourseCalendarEventUtilService {

    @Autowired
    private CourseCalendarEventRepository courseCalendarEventRepository;

    /**
     * Creates and persists one weekly course calendar event starting 5 days after the course's start date.
     * The events span the full duration of the course.
     *
     * @param course the course for which to create calendar events
     * @return the list of persisted {@link CourseCalendarEvent}s events
     * @throws IllegalArgumentException if the course has no start or end date
     */
    public List<CourseCalendarEvent> createCourseCalendarEvents(Course course) {
        if (course.getStartDate() == null || course.getEndDate() == null) {
            throw new IllegalArgumentException("Course must have a start and end date");
        }
        List<CourseCalendarEvent> events = new ArrayList<>();
        ZonedDateTime firstSessionStart = course.getStartDate().plusDays(5).withHour(10).withMinute(0);
        ZonedDateTime endDate = course.getEndDate();
        for (int i = 0; !firstSessionStart.plusWeeks(i).isAfter(endDate); i++) {
            ZonedDateTime eventStart = firstSessionStart.plusWeeks(i);
            ZonedDateTime eventEnd = eventStart.plusHours(2);
            CourseCalendarEvent event = new CourseCalendarEvent();
            event.setCourse(course);
            event.setTitle("Weekly Session " + (i + 1));
            event.setStartDate(eventStart);
            event.setEndDate(eventEnd);
            event.setVisibleToStudents(true);
            event.setVisibleToTutors(true);
            event.setVisibleToEditors(true);
            event.setVisibleToInstructors(true);
            events.add(event);
        }
        return courseCalendarEventRepository.saveAll(events);
    }

    public List<CourseCalendarEvent> createCourseCalendarEventsWithMutualExclusiveVisibility(Course course) {
        if (course.getStartDate() == null || course.getEndDate() == null) {
            throw new IllegalArgumentException("Course must have a start and end date");
        }
        List<CourseCalendarEvent> events = new ArrayList<>();
        ZonedDateTime firstSessionStart = course.getStartDate().plusDays(5).withHour(10).withMinute(0);
        ZonedDateTime endDate = course.getEndDate();
        for (int i = 0; !firstSessionStart.plusWeeks(i).isAfter(endDate); i++) {
            ZonedDateTime eventStart = firstSessionStart.plusWeeks(i);
            ZonedDateTime eventEnd = eventStart.plusHours(2);
            CourseCalendarEvent event = new CourseCalendarEvent();
            event.setCourse(course);
            event.setTitle("Weekly Session " + (i + 1));
            event.setStartDate(eventStart);
            event.setEndDate(eventEnd);
            event.setVisibleToStudents(i % 4 == 0);
            event.setVisibleToTutors(i % 4 == 1);
            event.setVisibleToEditors(i % 4 == 2);
            event.setVisibleToInstructors(i % 4 == 3);
            events.add(event);
        }
        return courseCalendarEventRepository.saveAll(events);
    }
}
