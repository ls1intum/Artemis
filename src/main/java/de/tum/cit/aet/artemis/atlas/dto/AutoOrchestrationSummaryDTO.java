package de.tum.cit.aet.artemis.atlas.dto;

import java.time.Instant;

/**
 * WebSocket payload broadcast after the automatic orchestrator finishes draining a course's
 * accumulated batch. One message per scheduler tick that actually fired a run; subscribers (the
 * instructor's browser) render a toast linking back to the orchestrator audit dialog.
 *
 * @param courseId      the course whose batch was drained
 * @param runId         opaque identifier matching scheduler logs for traceability
 * @param exerciseCount total number of exercises in the batch
 * @param successCount  exercises whose orchestrator run returned {@code SUCCESS}
 * @param failureCount  exercises whose orchestrator run returned {@code FAILED} (or threw)
 * @param completedAt   wall-clock time the broadcast was generated
 */
public record AutoOrchestrationSummaryDTO(long courseId, String runId, int exerciseCount, int successCount, int failureCount, Instant completedAt) {
}
