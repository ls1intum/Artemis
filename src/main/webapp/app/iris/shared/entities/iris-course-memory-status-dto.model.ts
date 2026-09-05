/**
 * Mirrors the server IrisCourseMemoryStatusDTO record.
 * Wire format for Course Memory progress pushed to the user who triggered a run.
 */
export interface IrisCourseMemoryStatusDTO {
    operation: CourseMemoryOperation;
    stage: CourseMemoryStage;
    courseId: number;
    postId: string;
    errorMessage?: string;
}

export enum CourseMemoryOperation {
    INGEST = 'INGEST',
    DELETE = 'DELETE',
}

export enum CourseMemoryStage {
    /** Artemis dispatched the webhook. Sent server-side, since ingestion may be skipped entirely. */
    TRIGGERED = 'TRIGGERED',
    COMPLETED = 'COMPLETED',
    FAILED = 'FAILED',
}
