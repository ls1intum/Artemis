package de.tum.cit.aet.artemis.exam.dto;

/**
 * Reports how many successful Athena AI feedback requests the current user has accumulated across all attempts
 * of a given test exam, and the configured cap, so the client can display progress toward the limit.
 */
public record AthenaFeedbackUsageDTO(long used, int limit) {
}
