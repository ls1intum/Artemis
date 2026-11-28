package de.tum.cit.aet.artemis.core.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Wrapper class for a two-component statistic
 * depending on the due-date of an exercise.
 * <p>
 * The schema name carries the DTO suffix only so that the OpenAPI configuration, which keeps schemas named like DTOs and strips the
 * suffix, publishes this record as {@code DueDateStat}.
 *
 * @param inTime the count of items before the due date
 * @param late   the count of items after the due date
 */
@Schema(name = "DueDateStatDTO")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record DueDateStat(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) long inTime, @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long late) {
}
