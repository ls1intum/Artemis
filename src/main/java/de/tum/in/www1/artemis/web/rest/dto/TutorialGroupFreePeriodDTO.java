package de.tum.in.www1.artemis.web.rest.dto;

import java.time.LocalDateTime;

import jakarta.annotation.Nonnull;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Used because we want to interpret the date in the time zone of the tutorial groups configuration
 *
 * @param startDate
 * @param endDate
 * @param reason
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TutorialGroupFreePeriodDTO(@Nonnull LocalDateTime startDate, @Nonnull LocalDateTime endDate, String reason) {
}
