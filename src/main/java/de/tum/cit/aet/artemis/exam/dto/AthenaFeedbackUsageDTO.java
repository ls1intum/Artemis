package de.tum.cit.aet.artemis.exam.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Reports how many successful Athena AI feedback requests the current user has accumulated across all attempts
 * of a given test exam, and the configured cap, so the client can display progress toward the limit.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record AthenaFeedbackUsageDTO(long used, int limit) {
}
