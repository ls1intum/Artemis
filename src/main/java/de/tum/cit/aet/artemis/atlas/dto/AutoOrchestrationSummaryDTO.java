package de.tum.cit.aet.artemis.atlas.dto;

import java.time.Instant;

import org.jspecify.annotations.NonNull;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * WebSocket payload broadcast after the automatic orchestrator finishes draining a course's
 * accumulated batch. One message per scheduler tick that actually fired a run; subscribers (the
 * instructor's browser) render a toast linking back to the orchestrator audit dialog. A batch whose
 * claimed exercises were all inapplicable never fires a run and therefore broadcasts nothing.
 * <p>
 * A batch reports a single, batch-level outcome, so {@code successCount + failureCount == exerciseCount}
 * always holds. The counts cannot distinguish a completed run that changed competencies from one that
 * verified nothing needed to change, so {@code outcome} carries that distinction for the toast.
 *
 * @param courseId      the course whose batch was drained
 * @param runId         opaque identifier matching scheduler logs for traceability
 * @param exerciseCount total number of exercises in the batch
 * @param successCount  {@code exerciseCount} when the run completed (with or without changes), otherwise {@code 0}
 * @param failureCount  {@code exerciseCount} when the run failed, threw or was partial, otherwise {@code 0}
 * @param outcome       batch-level outcome that selects the instructor toast
 * @param completedAt   wall-clock time the broadcast was generated
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record AutoOrchestrationSummaryDTO(long courseId, @NonNull String runId, int exerciseCount, int successCount, int failureCount, @NonNull Outcome outcome,
        @NonNull Instant completedAt) {

    /** Batch-level outcome of an automatic run. */
    public enum Outcome {
        /** The run completed and applied at least one competency change. */
        SUCCESS,
        /** The run completed and verified that no competency change was needed. */
        NO_CHANGES,
        /** The run committed no verified outcome, or ended in an unknown or partial state. */
        FAILED
    }

    public AutoOrchestrationSummaryDTO {
        if (exerciseCount < 0 || successCount < 0 || failureCount < 0) {
            throw new IllegalArgumentException("counts must be non-negative");
        }
        if (successCount + failureCount != exerciseCount) {
            throw new IllegalArgumentException("successCount + failureCount must equal exerciseCount");
        }
    }
}
