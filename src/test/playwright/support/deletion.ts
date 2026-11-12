import { APIRequestContext } from '@playwright/test';
import { DELETION_COMPLETION_TIMEOUT } from './timeouts';

/** What is being deleted; mirrors the entity types of the server-side deletion progress. */
export type DeletionTarget =
    | { entityType: 'EXERCISE'; entityId: number }
    | { entityType: 'EXAM'; entityId: number; courseId: number }
    | { entityType: 'EXERCISE_GROUP'; entityId: number; courseId: number; examId: number };

/** How a background deletion ended. */
export type DeletionOutcome = { failed: false } | { failed: true; errorMessage: string };

type DeletionProgressBody = { entityType?: string; entityId?: number; status?: string; errorMessage?: string };

const POLL_INTERVALS_MS = [250, 500, 1000, 2000];

function statusUrl(target: DeletionTarget): string {
    switch (target.entityType) {
        case 'EXERCISE':
            return `api/exercise/exercises/${target.entityId}/deletion-progress`;
        case 'EXAM':
            return `api/exam/courses/${target.courseId}/exams/${target.entityId}/deletion-progress`;
        case 'EXERCISE_GROUP':
            return `api/exam/courses/${target.courseId}/exams/${target.examId}/deletion-progress`;
    }
}

/** URL that answers 404 once the entity does not exist any more. Used when the progress endpoint knows nothing (204). */
function entityUrl(target: DeletionTarget): string {
    switch (target.entityType) {
        case 'EXERCISE':
            return `api/exercise/exercises/${target.entityId}`;
        case 'EXAM':
            return `api/exam/courses/${target.courseId}/exams/${target.entityId}`;
        case 'EXERCISE_GROUP':
            return `api/exam/courses/${target.courseId}/exams/${target.examId}/exercise-groups/${target.entityId}`;
    }
}

/**
 * Waits until no deletion of the given exam or of one of its exercise groups is running, however it ends.
 * <p>
 * A delete request that was answered with 409 collided with such a deletion. Its own progress will never show up, because the exam status
 * describes the deletion that holds the exam, so the caller has to wait for the conflicting job to end and then send its request again.
 *
 * @param request the API context of a user who may delete in the course
 * @param courseId the course of the exam
 * @param examId the exam whose deletions are awaited
 * @param timeoutMs how long a conflicting deletion may stay in progress
 * @return how long was waited, in milliseconds
 */
export async function awaitNoRunningExamDeletion(request: APIRequestContext, courseId: number, examId: number, timeoutMs = DELETION_COMPLETION_TIMEOUT): Promise<number> {
    const start = Date.now();
    const deadline = start + timeoutMs;
    for (let attempt = 0; ; attempt++) {
        const statusResponse = await request.get(statusUrl({ entityType: 'EXAM', entityId: examId, courseId }));
        const status = statusResponse.status();
        if (status === 200) {
            const progress = (await statusResponse.json()) as DeletionProgressBody;
            if (progress.status !== 'IN_PROGRESS') {
                return Date.now() - start;
            }
        } else if (status === 204 || status === 404) {
            // Nothing is known about a deletion (any more), or the exam itself is gone.
            return Date.now() - start;
        } else {
            throw new Error(`Could not read the deletion progress of exam ${examId}: ${status} ${await statusResponse.text()}`);
        }
        if (Date.now() > deadline) {
            throw new Error(`A deletion in exam ${examId} did not finish within ${timeoutMs} ms`);
        }
        await new Promise((resolve) => setTimeout(resolve, POLL_INTERVALS_MS[Math.min(attempt, POLL_INTERVALS_MS.length - 1)]));
    }
}

/**
 * Waits until an accepted (202) deletion finished in the background, by following the progress the server reports instead of sleeping.
 * <p>
 * The status endpoint of an exercise or an exam answers 404 once the entity is gone, which is the usual way a successful deletion ends here.
 * A deletion that failed on the server is reported as a failure and not as a timeout, so callers can retry or fail with the server's reason.
 *
 * @param request the API context of a user who may delete the entity
 * @param target the entity whose deletion was started
 * @param timeoutMs how long a deletion may stay in progress
 */
export async function awaitDeletion(request: APIRequestContext, target: DeletionTarget, timeoutMs = DELETION_COMPLETION_TIMEOUT): Promise<DeletionOutcome> {
    const deadline = Date.now() + timeoutMs;
    for (let attempt = 0; ; attempt++) {
        const statusResponse = await request.get(statusUrl(target));
        const status = statusResponse.status();
        if (status === 404) {
            return { failed: false };
        }
        if (status === 200) {
            const progress = (await statusResponse.json()) as DeletionProgressBody;
            // The status of an exam is shared by the exam and its exercise groups; only a message about this entity counts.
            if (progress.entityType === target.entityType && progress.entityId === target.entityId) {
                if (progress.status === 'COMPLETED') {
                    return { failed: false };
                }
                if (progress.status === 'FAILED') {
                    return { failed: true, errorMessage: progress.errorMessage ?? 'the server did not report a reason' };
                }
            } else if ((await request.get(entityUrl(target))).status() === 404) {
                // The status describes another operation that replaced ours before its end was seen. The entity itself tells how ours ended: it is gone.
                return { failed: false };
            }
        } else if (status === 204) {
            // Nothing known about a deletion: it either finished long ago or never started. The entity itself tells which.
            const entityResponse = await request.get(entityUrl(target));
            if (entityResponse.status() === 404) {
                return { failed: false };
            }
        } else if (status >= 400) {
            throw new Error(`Could not read the progress of the deletion of ${target.entityType} ${target.entityId}: ${status} ${await statusResponse.text()}`);
        }
        if (Date.now() > deadline) {
            throw new Error(`The deletion of ${target.entityType} ${target.entityId} did not finish within ${timeoutMs} ms`);
        }
        await new Promise((resolve) => setTimeout(resolve, POLL_INTERVALS_MS[Math.min(attempt, POLL_INTERVALS_MS.length - 1)]));
    }
}

type CourseOperationProgressBody = { operationType?: string; status?: string; errorMessage?: string };

/**
 * Waits until an accepted (202) deletion of a course finished in the background, by following the progress of the course operation.
 * <p>
 * The status endpoint of a course operation is open to administrators and does not depend on the course, so the course itself is only
 * checked when the endpoint knows nothing about a deletion (204, or the status of another operation): then the course is gone when the
 * course endpoint answers 404. A deletion that failed on the server is reported as a failure and not as a timeout.
 *
 * @param request the API context of an administrator
 * @param courseId the course whose deletion was started
 * @param timeoutMs how long a deletion may stay in progress
 */
export async function awaitCourseDeletion(request: APIRequestContext, courseId: number, timeoutMs = DELETION_COMPLETION_TIMEOUT): Promise<DeletionOutcome> {
    const deadline = Date.now() + timeoutMs;
    for (let attempt = 0; ; attempt++) {
        const statusResponse = await request.get(`api/admin/courses/${courseId}/operation-progress`);
        const status = statusResponse.status();
        if (status !== 200 && status !== 204) {
            throw new Error(`Could not read the progress of the deletion of course ${courseId}: ${status} ${await statusResponse.text()}`);
        }
        const progress = status === 200 ? ((await statusResponse.json()) as CourseOperationProgressBody) : undefined;
        if (progress?.operationType === 'DELETE' && progress.status === 'COMPLETED') {
            return { failed: false };
        }
        if (progress?.operationType === 'DELETE' && progress.status === 'FAILED') {
            return { failed: true, errorMessage: progress.errorMessage ?? 'the server did not report a reason' };
        }
        if (progress?.operationType !== 'DELETE' || progress.status !== 'IN_PROGRESS') {
            // Nothing known about a deletion: it either finished long ago or never started. The course itself tells which.
            const courseResponse = await request.get(`api/course/courses/${courseId}/for-instructor`);
            if (courseResponse.status() === 404) {
                return { failed: false };
            }
        }
        if (Date.now() > deadline) {
            throw new Error(`The deletion of course ${courseId} did not finish within ${timeoutMs} ms`);
        }
        await new Promise((resolve) => setTimeout(resolve, POLL_INTERVALS_MS[Math.min(attempt, POLL_INTERVALS_MS.length - 1)]));
    }
}
