package de.tum.cit.aet.artemis.calendar.util;

import static tech.jhipster.config.JHipsterConstants.SPRING_PROFILE_TEST;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.calendar.domain.CoursewideCalendarEvent;
import de.tum.cit.aet.artemis.calendar.repository.CoursewideCalendarEventRepository;
import de.tum.cit.aet.artemis.core.domain.Course;

@Service
@Profile(SPRING_PROFILE_TEST)
public class CoursewideCalendarEventUtilService {

    private final CoursewideCalendarEventRepository coursewideCalendarEventRepository;

    public CoursewideCalendarEventUtilService(CoursewideCalendarEventRepository coursewideCalendarEventRepository) {
        this.coursewideCalendarEventRepository = coursewideCalendarEventRepository;
    }

    /**
     * Creates and persists weekly {@link CoursewideCalendarEvent}s starting 5 days after the course's start date.
     * The events repeat during the full duration of the course.
     *
     * @param course the course for which to create the events
     * @return the list of persisted events
     * @throws IllegalArgumentException if the course has no start or end date
     */
    public List<CoursewideCalendarEvent> createCoursewideCalendarEvents(Course course) {
        if (course.getStartDate() == null || course.getEndDate() == null) {
            throw new IllegalArgumentException("Course must have a start and end date");
        }
        List<CoursewideCalendarEvent> events = new ArrayList<>();
        ZonedDateTime firstSessionStart = course.getStartDate().plusDays(5).withHour(10).withMinute(0);
        ZonedDateTime endDate = course.getEndDate();
        for (int i = 0; !firstSessionStart.plusWeeks(i).isAfter(endDate); i++) {
            ZonedDateTime eventStart = firstSessionStart.plusWeeks(i);
            ZonedDateTime eventEnd = eventStart.plusHours(2);
            CoursewideCalendarEvent event = new CoursewideCalendarEvent();
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
        return coursewideCalendarEventRepository.saveAll(events);
    }

    /**
     * Creates and persists weekly {@link CoursewideCalendarEvent}s starting 5 days after the course's start date.
     * The events repeat during the full duration of the course. Each event is visible to either students,
     * tutors, editors or instructors.
     *
     * @param course the course for which to create the events
     * @return the list of persisted events
     * @throws IllegalArgumentException if the course has no start or end date or spans less than 26 days (such that at least one event visible to each user group is created)
     */
    public List<CoursewideCalendarEvent> createCoursewideCalendarEventsWithMutuallyExclusiveVisibility(Course course) {
        if (course.getStartDate() == null || course.getEndDate() == null) {
            throw new IllegalArgumentException("Course must have a start and end date");
        }
        if (ChronoUnit.DAYS.between(course.getStartDate(), course.getEndDate()) < 26) {
            throw new IllegalArgumentException("Course must span at least 26 days to create four unique visibility events.");
        }
        List<CoursewideCalendarEvent> events = new ArrayList<>();
        ZonedDateTime firstSessionStart = course.getStartDate().plusDays(5).withHour(10).withMinute(0);
        ZonedDateTime endDate = course.getEndDate();
        for (int i = 0; !firstSessionStart.plusWeeks(i).isAfter(endDate); i++) {
            ZonedDateTime eventStart = firstSessionStart.plusWeeks(i);
            ZonedDateTime eventEnd = eventStart.plusHours(2);
            CoursewideCalendarEvent event = new CoursewideCalendarEvent();
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
        return coursewideCalendarEventRepository.saveAll(events);
    }
}
