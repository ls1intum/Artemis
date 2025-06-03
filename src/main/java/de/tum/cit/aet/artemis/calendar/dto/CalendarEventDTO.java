package de.tum.cit.aet.artemis.calendar.dto;

import java.time.ZoneId;
import java.time.ZonedDateTime;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

import de.tum.cit.aet.artemis.calendar.domain.CoursewideCalendarEvent;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupSession;

/**
 * A DTO used to display calendar events in the calendar feature. It can be derived from both {@link CoursewideCalendarEvent}s and {@link TutorialGroupSession}s.
 */
public record CalendarEventDTO(@Nullable String id, @NotNull String title, @NotNull String courseName, @NotNull ZonedDateTime startDate, @Nullable ZonedDateTime endDate,
        @Nullable String location, @Nullable String facilitator) {

    public CalendarEventDTO(TutorialGroupSession session, ZoneId clientTimeZone) {
        this("tutorial-" + session.getId(), session.getTutorialGroup().getTitle(), session.getTutorialGroup().getCourse().getTitle(),
                session.getStart().withZoneSameInstant(clientTimeZone), session.getEnd().withZoneSameInstant(clientTimeZone), session.getLocation(),
                session.getTutorialGroup().getTeachingAssistantName());
    }

    public CalendarEventDTO(CoursewideCalendarEvent coursewideCalendarEvent, ZoneId clientTimeZone) {
        this("course-" + coursewideCalendarEvent.getId(), coursewideCalendarEvent.getTitle(), coursewideCalendarEvent.getCourse().getTitle(),
                coursewideCalendarEvent.getStartDate().withZoneSameInstant(clientTimeZone),
                coursewideCalendarEvent.getEndDate() == null ? null : coursewideCalendarEvent.getEndDate().withZoneSameInstant(clientTimeZone),
                coursewideCalendarEvent.getLocation(), coursewideCalendarEvent.getFacilitator());
    }
}
