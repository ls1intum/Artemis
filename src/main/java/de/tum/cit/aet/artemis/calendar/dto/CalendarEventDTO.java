package de.tum.cit.aet.artemis.calendar.dto;

import java.time.ZonedDateTime;

import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupSession;

public record CalendarEventDTO(String title, String courseName, ZonedDateTime start, ZonedDateTime end, String location, String facilitator) {

    public CalendarEventDTO(TutorialGroupSession session) {
        this(session.getTutorialGroup().getTitle(), session.getTutorialGroup().getCourse().getTitle(), session.getStart(), session.getEnd(), session.getLocation(),
                session.getTutorialGroup().getTeachingAssistantName());
    }
}
