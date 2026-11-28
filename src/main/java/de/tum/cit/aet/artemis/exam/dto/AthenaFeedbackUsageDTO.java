package de.tum.cit.aet.artemis.exam.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Reports how many successful Athena AI feedback requests the current user has accumulated across all attempts
 * of a given test exam, and the configured cap, so the client can display progress toward the limit.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record AthenaFeedbackUsageDTO(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) long used, @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int limit) {
}
