package de.tum.cit.aet.artemis.calendar.dto;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupSession;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CalendarEventDTO(@NotNull String title, @NotNull String courseName, @NotNull ZonedDateTime start, @Nullable ZonedDateTime end, @Nullable String location,
        @Nullable String facilitator) {

    public CalendarEventDTO(TutorialGroupSession session) {
        this(session.getTutorialGroup().getTitle(), session.getTutorialGroup().getCourse().getTitle(), session.getStart().withZoneSameInstant(ZoneOffset.UTC),
                session.getEnd().withZoneSameInstant(ZoneOffset.UTC), session.getLocation(), session.getTutorialGroup().getTeachingAssistantName());
    }
}
