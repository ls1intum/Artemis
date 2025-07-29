package de.tum.cit.aet.artemis.core.dto.calendar;

import java.time.ZonedDateTime;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import de.tum.cit.aet.artemis.core.util.CalendarEventRelatedEntity;
import de.tum.cit.aet.artemis.core.util.CalendarEventSemantics;

/**
 * A DTO used to display calendar events in the calendar feature.
 */
@JsonInclude(Include.NON_EMPTY)
public record CalendarEventDTO(@NotNull CalendarEventRelatedEntity type, @NotNull CalendarEventSemantics subtype, @NotNull String title, @NotNull ZonedDateTime startDate,
        @Nullable ZonedDateTime endDate, @Nullable String location, @Nullable String facilitator) {
}
