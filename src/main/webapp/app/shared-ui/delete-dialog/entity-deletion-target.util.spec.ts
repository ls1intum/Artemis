import { describe, expect, it } from 'vitest';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { examDeletionTarget, exerciseDeletionTarget, exerciseGroupDeletionTarget, isDeletionCompleted } from 'app/shared-ui/delete-dialog/entity-deletion-target.util';
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

    it('treats completed and failed as the end of a deletion', () => {
        expect(isTerminalDeletionStatus(EntityDeletionStatus.COMPLETED)).toBe(true);
        expect(isTerminalDeletionStatus(EntityDeletionStatus.FAILED)).toBe(true);
        expect(isTerminalDeletionStatus(EntityDeletionStatus.IN_PROGRESS)).toBe(false);
        expect(isTerminalDeletionStatus(undefined)).toBe(false);
    });
});
