package de.tum.cit.aet.artemis.atlas.dto;

import java.time.Instant;

import org.jspecify.annotations.NonNull;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * WebSocket payload broadcast after the automatic orchestrator finishes draining a course's
 * accumulated batch. One message per scheduler tick that actually fired a run; subscribers (the
 * instructor's browser) render a toast linking back to the orchestrator audit dialog.
 * <p>
 * {@code exerciseCount} counts the total number of changed learning objects in the batch — exercises
 * <em>and</em> lecture units. The field name is kept for wire compatibility with the existing client
 * ({@code AutoOrchestrationSummary.exerciseCount}); a batch reports a single, batch-level outcome, so
 * {@code successCount + failureCount == exerciseCount} always holds. The counts cannot distinguish a run
 * that committed some changes before stopping from one that committed none, so {@code outcome} carries
 * that distinction for the toast.
 *
 * @param courseId      the course whose batch was drained
 * @param runId         opaque identifier matching scheduler logs for traceability
 * @param exerciseCount total number of changed learning objects (exercises + lecture units) in the batch
 * @param successCount  {@code exerciseCount} when the run returned {@code SUCCESS}, otherwise {@code 0}
 * @param failureCount  {@code exerciseCount} when the run failed / threw / was partial, otherwise {@code 0}
 * @param outcome       batch-level outcome that selects the instructor toast
 * @param completedAt   wall-clock time the broadcast was generated
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record AutoOrchestrationSummaryDTO(long courseId, @NonNull String runId, int exerciseCount, int successCount, int failureCount, @NonNull Outcome outcome,
        @NonNull Instant completedAt) {

    /** Batch-level outcome of an automatic run. */
    public enum Outcome {
        /** The run completed. */
        SUCCESS,
        /** The run committed some changes before it stopped; the batch is not retried. */
        PARTIAL,
        /** The run committed no changes or ended in an unknown state. */
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
