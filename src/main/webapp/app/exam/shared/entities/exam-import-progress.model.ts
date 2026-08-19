/**
 * Overall state of an exam (or exercise-group) import, received as live progress over a websocket while the import runs.
 * Mirrors the server-side {@code ExamImportProgressState}.
 */
export enum ExamImportProgressState {
    RUNNING = 'RUNNING',
    COMPLETED = 'COMPLETED',
    COMPLETED_WITH_ISSUES = 'COMPLETED_WITH_ISSUES',
}

/**
 * Status of a single exercise during an import. Mirrors the server-side {@code ExerciseImportStatus}.
 */
export enum ExerciseImportStatus {
    IMPORTING = 'IMPORTING',
    IMPORTED = 'IMPORTED',
    SKIPPED = 'SKIPPED',
    INCOMPLETE = 'INCOMPLETE',
}

/**
 * Live progress of an exam (or exercise-group) import. Mirrors the server-side {@code ExamImportProgressDTO}.
 * The arrays are omitted by the server when empty (NON_EMPTY serialization), hence optional.
 */
export interface ExamImportProgress {
    state: ExamImportProgressState;
    totalExercises: number;
    processedExercises: number;
    currentExerciseTitle?: string;
    currentStatus?: ExerciseImportStatus;
    skippedExercises?: string[];
    incompleteExercises?: string[];
}
