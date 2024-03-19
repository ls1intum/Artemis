package de.tum.in.www1.artemis.web.rest.dto;

import java.time.LocalDateTime;

import javax.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Used because we want to interpret the date in the time zone of the tutorial groups configuration
 *
 * @param startDate
 * @param endDate
 * @param reason
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TutorialGroupFreePeriodDTO(@NotNull LocalDateTime startDate, @NotNull LocalDateTime endDate, String reason) {
}
