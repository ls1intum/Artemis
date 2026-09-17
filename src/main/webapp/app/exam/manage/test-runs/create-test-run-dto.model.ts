/**
 * Request DTO for creating a test run (`POST .../test-runs`).
 * Matches the server-side `CreateTestRunDTO` record structure.
 * <p>
 * `exerciseIds` must be ordered: the server persists the exercises in exactly this order (the
 * `StudentExam.exercises` association is an `@OrderColumn` list), so callers must build this list by iterating
 * the exam's exercise groups in order and pushing one exercise id per group.
 */
export interface CreateTestRunDTO {
    examId: number;
    exerciseIds: number[];
    workingTime?: number;
}
