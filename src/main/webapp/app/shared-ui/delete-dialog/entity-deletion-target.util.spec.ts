import { describe, expect, it } from 'vitest';
import { HttpErrorResponse, HttpHeaders } from '@angular/common/http';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import {
    examDeletionTarget,
    exerciseDeletionTarget,
    exerciseGroupDeletionTarget,
    isDeletionCompleted,
    isDeletionOutcomeUnknown,
    isRejectionReportedByGlobalHandler,
} from 'app/shared-ui/delete-dialog/entity-deletion-target.util';
import { EntityDeletionProgress, EntityDeletionStatus, EntityDeletionType, isTerminalDeletionStatus } from 'app/shared-ui/delete-dialog/entity-deletion-progress.model';

describe('entity deletion target util', () => {
    it.each([
        [ExerciseType.PROGRAMMING, 'artemisApp.programmingExercise.deleted'],
        [ExerciseType.QUIZ, 'artemisApp.quizExercise.deleted'],
        [ExerciseType.TEXT, 'artemisApp.textExercise.deleted'],
        [ExerciseType.MODELING, 'artemisApp.modelingExercise.deleted'],
        [ExerciseType.FILE_UPLOAD, 'artemisApp.fileUploadExercise.deleted'],
    ])('targets a %s exercise with the alert the server used to send', (type, alertKey) => {
        expect(exerciseDeletionTarget({ id: 5, title: 'Sorting', type })).toEqual({
            entityType: EntityDeletionType.EXERCISE,
            entityId: 5,
            title: 'Sorting',
            successAlertKey: alertKey,
        });
    });

    it('falls back to an empty title', () => {
        expect(exerciseDeletionTarget({ id: 5, type: ExerciseType.TEXT }).title).toBe('');
    });

    it('targets an exam by its course and its own id', () => {
        expect(examDeletionTarget(1, 3, 'Final')).toEqual({
            entityType: EntityDeletionType.EXAM,
            entityId: 3,
            courseId: 1,
            examId: 3,
            title: 'Final',
            successAlertKey: 'artemisApp.exam.deleted',
        });
        expect(examDeletionTarget(1, 3, undefined).title).toBe('');
    });

    it('targets an exercise group by its exam', () => {
        expect(exerciseGroupDeletionTarget(1, 3, 9, 'Group 1')).toEqual({
            entityType: EntityDeletionType.EXERCISE_GROUP,
            entityId: 9,
            courseId: 1,
            examId: 3,
            title: 'Group 1',
            successAlertKey: 'artemisApp.exerciseGroup.deleted',
        });
        expect(exerciseGroupDeletionTarget(1, 3, 9, undefined).title).toBe('');
    });

    it('tells completed deletions from the others', () => {
        const progress = (status: EntityDeletionStatus) => ({ status }) as EntityDeletionProgress;

        expect(isDeletionCompleted(progress(EntityDeletionStatus.COMPLETED))).toBe(true);
        expect(isDeletionCompleted(progress(EntityDeletionStatus.FAILED))).toBe(false);
        expect(isDeletionCompleted(progress(EntityDeletionStatus.IN_PROGRESS))).toBe(false);
    });

    it('keeps an unknown outcome apart from a confirmed deletion', () => {
        const unknown = { status: EntityDeletionStatus.UNKNOWN } as EntityDeletionProgress;

        expect(isDeletionOutcomeUnknown(unknown)).toBe(true);
        expect(isDeletionCompleted(unknown)).toBe(false);
        expect(isDeletionOutcomeUnknown({ status: EntityDeletionStatus.COMPLETED } as EntityDeletionProgress)).toBe(false);
        expect(isDeletionOutcomeUnknown({ status: EntityDeletionStatus.FAILED } as EntityDeletionProgress)).toBe(false);
    });

    it('treats completed, failed and unknown as the end of a deletion', () => {
        expect(isTerminalDeletionStatus(EntityDeletionStatus.COMPLETED)).toBe(true);
        expect(isTerminalDeletionStatus(EntityDeletionStatus.FAILED)).toBe(true);
        expect(isTerminalDeletionStatus(EntityDeletionStatus.UNKNOWN)).toBe(true);
        expect(isTerminalDeletionStatus(EntityDeletionStatus.IN_PROGRESS)).toBe(false);
        expect(isTerminalDeletionStatus(undefined)).toBe(false);
    });

    describe('isRejectionReportedByGlobalHandler', () => {
        const rejection = (status: number, body?: unknown, headers?: Record<string, string>) => new HttpErrorResponse({ status, error: body, headers: new HttpHeaders(headers) });

        it.each([
            ['the server is not reachable', rejection(0)],
            ['access is denied', rejection(403)],
            ['a bad request carries an error header', rejection(400, undefined, { 'x-artemisapp-error': 'error.something' })],
            ['a bad request carries field errors', rejection(400, { fieldErrors: [{ field: 'a', message: 'b' }] })],
            ['an error carries a title', rejection(409, { title: 'Another deletion is already in progress' })],
            ['the server asked for no alert', rejection(500, { skipAlert: true })],
        ])('is true when %s, because the global handler already shows it or deliberately stays silent', (_description, error) => {
            expect(isRejectionReportedByGlobalHandler(error)).toBe(true);
        });

        it.each([
            ['an unstructured 502 of a proxy', rejection(502, '<html>Bad Gateway</html>')],
            ['an unstructured 500', rejection(500)],
            ['a 404, which is never alerted', rejection(404, { title: 'Not found' })],
            ['something that is no HTTP error', new Error('boom')],
        ])('is false for %s, so the caller has to report it', (_description, error) => {
            expect(isRejectionReportedByGlobalHandler(error)).toBe(false);
        });
    });
});
