import dayjs from 'dayjs/esm';

/**
 * Kind of entity whose asynchronous deletion reports progress. Mirrors the server-side {@code EntityDeletionType}.
 */
export enum EntityDeletionType {
    EXERCISE = 'EXERCISE',
    EXAM = 'EXAM',
    EXERCISE_GROUP = 'EXERCISE_GROUP',
}

/**
 * Status of an asynchronous deletion. Mirrors the server-side {@code EntityDeletionStatus}, except for {@link UNKNOWN}.
 */
export enum EntityDeletionStatus {
    IN_PROGRESS = 'IN_PROGRESS',
    COMPLETED = 'COMPLETED',
    FAILED = 'FAILED',
    /**
     * Only ever produced by the client, never sent by the server: the deletion is over for this client, but it could not read how it ended,
     * for example because another operation replaced the status of the exam or because the status was not readable any more. It is neither a
     * confirmed deletion nor a failure, so callers refresh their data instead of announcing one of them.
     */
    UNKNOWN = 'UNKNOWN',
}

/**
 * Live progress of an exercise, exam or exercise group deletion, received over a websocket while the server deletes in the background.
 * Mirrors the server-side {@code EntityDeletionProgressDTO}; the server omits empty optional fields.
 */
export interface EntityDeletionProgress {
    entityType: EntityDeletionType;
    entityId: number;
    status: EntityDeletionStatus;
    /** Stable step key, translated with {@code artemisApp.deletionProgress.step.<key>}. */
    currentStep?: string;
    stepsCompleted: number;
    totalSteps: number;
    /** Item progress inside the current step, e.g. exercises deleted so far inside an exam. */
    itemsProcessed: number;
    totalItems: number;
    currentItemTitle?: string;
    /** Weighted progress (0-100) that accounts for the cost of the individual steps. */
    weightedProgressPercent: number;
    startedAt?: dayjs.Dayjs;
    /** When the server last reported progress. Informational only: the server itself ends a deletion that stopped reporting, as FAILED. */
    updatedAt?: dayjs.Dayjs;
    errorMessage?: string;
}

/**
 * Identifies the entity that is being deleted. The ids select the websocket topic and the status endpoint.
 */
export interface EntityDeletionTarget {
    entityType: EntityDeletionType;
    entityId: number;
    /** Required for exams and exercise groups. */
    courseId?: number;
    /** Required for exercise groups; for an exam it is the exam id itself. */
    examId?: number;
    /** Title of the entity, shown in the progress dialog and the success alert. */
    title: string;
    /** Translation key of the success alert, which receives the title as {@code param}. */
    successAlertKey: string;
}

/** Data handed to the progress dialog. */
export interface EntityDeletionProgressDialogData {
    target: EntityDeletionTarget;
    /** Latest progress; undefined until the first message arrives. */
    progress: () => EntityDeletionProgress | undefined;
    /** Called when the instructor closes the dialog while the deletion still runs ("Continue in background"). */
    continueInBackground: () => void;
}

export function isTerminalDeletionStatus(status: EntityDeletionStatus | undefined): boolean {
    return status === EntityDeletionStatus.COMPLETED || status === EntityDeletionStatus.FAILED || status === EntityDeletionStatus.UNKNOWN;
}
