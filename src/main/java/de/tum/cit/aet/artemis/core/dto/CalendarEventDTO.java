package de.tum.cit.aet.artemis.core.dto;

import java.time.ZoneId;
import java.time.ZonedDateTime;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupSession;

/**
 * A DTO used to display calendar events in the calendar feature.
 */
@JsonInclude(Include.NON_EMPTY)
public record CalendarEventDTO(@Nullable String id, @NotNull String title, @NotNull String courseName, @NotNull ZonedDateTime startDate, @Nullable ZonedDateTime endDate,
        @Nullable String location, @Nullable String facilitator) {

    public CalendarEventDTO(TutorialGroupSession session, ZoneId clientTimeZone) {
        this("tutorial-" + session.getId(), session.getTutorialGroup().getTitle(), session.getTutorialGroup().getCourse().getTitle(),
                session.getStart().withZoneSameInstant(clientTimeZone), session.getEnd().withZoneSameInstant(clientTimeZone), session.getLocation(),
                session.getTutorialGroup().getTeachingAssistantName());
    }
}
