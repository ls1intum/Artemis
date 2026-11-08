import { DestroyRef } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { EntityDeletionProgress, EntityDeletionStatus, EntityDeletionTarget, EntityDeletionType } from 'app/shared-ui/delete-dialog/entity-deletion-progress.model';

/** Success alert of each exercise type; the server used to send the same keys as an alert header with the synchronous delete. */
const EXERCISE_DELETED_ALERT_KEYS: Record<ExerciseType, string> = {
    [ExerciseType.PROGRAMMING]: 'artemisApp.programmingExercise.deleted',
    [ExerciseType.QUIZ]: 'artemisApp.quizExercise.deleted',
    [ExerciseType.TEXT]: 'artemisApp.textExercise.deleted',
    [ExerciseType.MODELING]: 'artemisApp.modelingExercise.deleted',
    [ExerciseType.FILE_UPLOAD]: 'artemisApp.fileUploadExercise.deleted',
};

/**
 * Target for deleting the given exercise, no matter whether it belongs to a course or an exam.
 */
export function exerciseDeletionTarget(exercise: Pick<Exercise, 'id' | 'title' | 'type'>): EntityDeletionTarget {
    return {
        entityType: EntityDeletionType.EXERCISE,
        entityId: exercise.id!,
        title: exercise.title ?? '',
        successAlertKey: EXERCISE_DELETED_ALERT_KEYS[exercise.type!],
    };
}

/**
 * Target for deleting an exam.
 */
export function examDeletionTarget(courseId: number, examId: number, title: string | undefined): EntityDeletionTarget {
    return { entityType: EntityDeletionType.EXAM, entityId: examId, courseId, examId, title: title ?? '', successAlertKey: 'artemisApp.exam.deleted' };
}

/**
 * Target for deleting an exercise group of an exam.
 */
export function exerciseGroupDeletionTarget(courseId: number, examId: number, exerciseGroupId: number, title: string | undefined): EntityDeletionTarget {
    return { entityType: EntityDeletionType.EXERCISE_GROUP, entityId: exerciseGroupId, courseId, examId, title: title ?? '', successAlertKey: 'artemisApp.exerciseGroup.deleted' };
}

/**
 * Tells whether the component that owns the given {@link DestroyRef} is still on screen. A deletion can end after the instructor chose
 * "Continue in background" and left the page; the owner must then not navigate, because that would pull the instructor away from whatever they
 * are doing now. Refreshing lists (broadcasting events) is still fine.
 */
export function trackAlive(destroyRef: DestroyRef): () => boolean {
    let alive = true;
    destroyRef.onDestroy(() => (alive = false));
    return () => alive;
}

/**
 * Whether the global error handling already shows an alert for the given rejected request, or deliberately stays silent about it. Callers
 * that report a rejection themselves use this to avoid showing the same failure twice. It mirrors {@code AlertService}: it alerts when the
 * server is not reachable, on 403, on a 400 with an error header, field errors or a title, and on any other error that carries a title. It
 * never alerts on 404, and not for an error without a title such as the plain 502 page of a proxy.
 */
export function isRejectionReportedByGlobalHandler(error: unknown): boolean {
    if (!(error instanceof HttpErrorResponse)) {
        return false;
    }
    if (error.error?.skipAlert) {
        // The server asked for no alert.
        return true;
    }
    if (error.status === 0 || error.status === 403) {
        return true;
    }
    if (error.status === 404) {
        return false;
    }
    const hasAppErrorHeader = error.headers?.keys().some((key) => key.toLowerCase().endsWith('app-error')) ?? false;
    return hasAppErrorHeader || !!error.error?.fieldErrors || !!error.error?.title;
}

/** Whether the deletion is confirmed to be done. An {@link isDeletionOutcomeUnknown unknown} outcome is not a confirmation. */
export function isDeletionCompleted(progress: EntityDeletionProgress): boolean {
    return progress.status === EntityDeletionStatus.COMPLETED;
}

/**
 * Whether the deletion is over for this client without it being known how it ended. Callers must neither announce a deletion nor leave the
 * page of the entity, they only refresh what they show from the server.
 */
export function isDeletionOutcomeUnknown(progress: EntityDeletionProgress): boolean {
    return progress.status === EntityDeletionStatus.UNKNOWN;
}
