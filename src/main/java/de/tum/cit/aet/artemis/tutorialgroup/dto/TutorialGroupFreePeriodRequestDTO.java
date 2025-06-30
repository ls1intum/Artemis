package de.tum.cit.aet.artemis.tutorialgroup.dto;

import java.time.LocalDateTime;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Used because we want to interpret the date in the time zone of the tutorial groups configuration
 *
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TutorialGroupFreePeriodRequestDTO(@NotNull LocalDateTime startDate, @NotNull LocalDateTime endDate, @Nullable String reason) {
}
