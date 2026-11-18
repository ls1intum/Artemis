import dayjs from 'dayjs/esm';

/**
 * Type of course operation being performed.
 */
export enum CourseOperationType {
    DELETE = 'DELETE',
    RESET = 'RESET',
    ARCHIVE = 'ARCHIVE',
    IMPORT = 'IMPORT',
}

/**
 * Current status of the operation.
 */
export enum CourseOperationStatus {
    IN_PROGRESS = 'IN_PROGRESS',
    COMPLETED = 'COMPLETED',
    FAILED = 'FAILED',
}

/**
 * DTO for tracking progress of long-running course operations (delete, reset, archive).
 * Used for real-time progress updates via WebSocket.
 *
 * The weightedProgressPercent field provides a more accurate progress indicator than
 * simple step counting. It accounts for the complexity of different operations:
 * - Programming exercises have higher weight due to repository operations
 * - Exercises with more participations/submissions take longer
 * - Exams with more student exams take longer
 * - Large numbers of posts/messages take longer to delete
 */
export interface CourseOperationProgressDTO {
    operationType: CourseOperationType;
    currentStep?: string;
    stepsCompleted: number;
    totalSteps: number;
    itemsProcessed: number;
    totalItems: number;
    failed: number;
    startedAt?: dayjs.Dayjs;
    status: CourseOperationStatus;
    errorMessage?: string;
    /** Weighted progress percentage (0-100) based on operation complexity */
    weightedProgressPercent: number;
}
