package de.tum.cit.aet.artemis.calendar.dto;

import java.time.ZoneId;
import java.time.ZonedDateTime;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

import de.tum.cit.aet.artemis.calendar.domain.CourseCalendarEvent;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupSession;

/**
 * A DTO used to display calendar events in the calendar feature
 */
public record CalendarEventDTO(@Nullable String id, @NotNull String title, @NotNull String courseName, @NotNull ZonedDateTime startDate, @Nullable ZonedDateTime endDate,
        @Nullable String location, @Nullable String facilitator) {

    public CalendarEventDTO(TutorialGroupSession session, ZoneId clientTimeZone) {
        this("tutorial-" + session.getId(), session.getTutorialGroup().getTitle(), session.getTutorialGroup().getCourse().getTitle(),
                session.getStart().withZoneSameInstant(clientTimeZone), session.getEnd().withZoneSameInstant(clientTimeZone), session.getLocation(),
                session.getTutorialGroup().getTeachingAssistantName());
    }

    public CalendarEventDTO(CourseCalendarEvent courseCalendarEvent, ZoneId clientTimeZone) {
        this("course-" + courseCalendarEvent.getId(), courseCalendarEvent.getTitle(), courseCalendarEvent.getCourse().getTitle(),
                courseCalendarEvent.getStartDate().withZoneSameInstant(clientTimeZone),
                courseCalendarEvent.getEndDate() == null ? null : courseCalendarEvent.getEndDate().withZoneSameInstant(clientTimeZone), courseCalendarEvent.getLocation(),
                courseCalendarEvent.getFacilitator());
    }
}
