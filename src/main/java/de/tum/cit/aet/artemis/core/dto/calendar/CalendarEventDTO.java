package de.tum.cit.aet.artemis.core.dto.calendar;

import java.time.ZonedDateTime;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import de.tum.cit.aet.artemis.core.util.CalendarEventType;

/**
 * A DTO used to display calendar events in the calendar feature.
 */
@JsonInclude(Include.NON_EMPTY)
public record CalendarEventDTO(@JsonIgnore @NotNull String id, @NotNull CalendarEventType type, @NotNull String title, @NotNull ZonedDateTime startDate,
        @Nullable ZonedDateTime endDate, @Nullable String location, @Nullable String facilitator) {
}
