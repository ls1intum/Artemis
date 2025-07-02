package de.tum.cit.aet.artemis.core.dto;

import java.time.ZonedDateTime;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

/**
 * A DTO used to display calendar events in the calendar feature.
 */
@JsonInclude(Include.NON_EMPTY)
public record CalendarEventDTO(@NotNull String id, @NotNull String title, @NotNull ZonedDateTime startDate, @Nullable ZonedDateTime endDate, @Nullable String location,
        @Nullable String facilitator) {
}
