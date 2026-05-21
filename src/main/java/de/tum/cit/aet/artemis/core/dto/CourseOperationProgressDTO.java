package de.tum.cit.aet.artemis.core.dto;

import java.io.Serializable;
import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.CourseOperationStatus;
import de.tum.cit.aet.artemis.core.domain.CourseOperationType;

/**
 * DTO for tracking progress of long-running course operations (delete, reset, archive).
 * Used for real-time progress updates via WebSocket.
 * <p>
 * Progress is tracked using a weighted system where different operations have different
 * costs based on their complexity. For example, deleting a programming exercise with
 * repositories takes longer than deleting a simple FAQ entry.
 *
 * @param operationType           the type of operation (DELETE, RESET, ARCHIVE)
 * @param currentStep             the name of the current operation step (e.g., "Deleting exercises")
 * @param stepsCompleted          number of completed steps (for display)
 * @param totalSteps              total number of steps in the operation (for display)
 * @param itemsProcessed          number of items processed in the current step
 * @param totalItems              total items to process in the current step
 * @param failed                  number of failed operations
 * @param startedAt               timestamp when the operation started
 * @param status                  the current status (IN_PROGRESS, COMPLETED, FAILED)
 * @param errorMessage            error message if the operation failed
 * @param weightedProgressPercent weighted progress (0-100) based on actual work done, not just step count
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseOperationProgressDTO(CourseOperationType operationType, String currentStep, int stepsCompleted, int totalSteps, int itemsProcessed, int totalItems, int failed,
        ZonedDateTime startedAt, CourseOperationStatus status, String errorMessage, double weightedProgressPercent) implements Serializable {

    /**
     * Creates a new in-progress status with weighted progress.
     */
    public static CourseOperationProgressDTO inProgress(CourseOperationType type, String step, int stepsCompleted, int totalSteps, int itemsProcessed, int totalItems, int failed,
            ZonedDateTime startedAt, double weightedProgressPercent) {
        return new CourseOperationProgressDTO(type, step, stepsCompleted, totalSteps, itemsProcessed, totalItems, failed, startedAt, CourseOperationStatus.IN_PROGRESS, null,
                weightedProgressPercent);
    }

    /**
     * Creates a completed status.
     */
    public static CourseOperationProgressDTO completed(CourseOperationType type, int totalSteps, int failed, ZonedDateTime startedAt) {
        return new CourseOperationProgressDTO(type, null, totalSteps, totalSteps, 0, 0, failed, startedAt, CourseOperationStatus.COMPLETED, null, 100.0);
    }

    /**
     * Creates a failed status.
     */
    public static CourseOperationProgressDTO failed(CourseOperationType type, String step, int stepsCompleted, int totalSteps, int failed, ZonedDateTime startedAt,
            String errorMessage, double weightedProgressPercent) {
        return new CourseOperationProgressDTO(type, step, stepsCompleted, totalSteps, 0, 0, failed, startedAt, CourseOperationStatus.FAILED, errorMessage, weightedProgressPercent);
    }
}
