import { describe, expect, it } from 'vitest';
import { Navigation, Router } from '@angular/router';
import { Course } from 'app/course/shared/entities/course.model';
import { ExerciseGroup } from 'app/exam/shared/entities/exercise-group.model';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { EXERCISE_FOR_IMPORT_FROM_FILE, exerciseForImportFromFile, importFromFileNavigation } from 'app/exercise/import/import-from-file-navigation';

describe('importFromFileNavigation', () => {
    it('opens the import from file page for a programming exercise, which also uploads the repositories', () => {
        const exercise = { type: ExerciseType.PROGRAMMING } as Exercise;
        expect(importFromFileNavigation(exercise)).toEqual({
            segments: ['programming-exercises', 'import-from-file'],
            extras: { state: { programmingExerciseForImportFromFile: exercise } },
        });
    });

    it.each([ExerciseType.TEXT, ExerciseType.MODELING, ExerciseType.FILE_UPLOAD, ExerciseType.QUIZ])('opens the editor of a new %s exercise', (type) => {
        const exercise = { type } as Exercise;
        expect(importFromFileNavigation(exercise)).toEqual({ segments: [`${type}-exercises`, 'new'], extras: { state: { [EXERCISE_FOR_IMPORT_FROM_FILE]: exercise } } });
    });
});

describe('exerciseForImportFromFile', () => {
    function routerWithState(state?: Record<string, unknown>): Router {
        return { currentNavigation: () => (state ? ({ extras: { state } } as unknown as Navigation) : null) } as unknown as Router;
    }

    it('returns undefined when the navigation carries no exercise', () => {
        expect(exerciseForImportFromFile(routerWithState(), { id: 1 } as Course, undefined)).toBeUndefined();
        expect(exerciseForImportFromFile(routerWithState({ other: 1 }), { id: 1 } as Course, undefined)).toBeUndefined();
    });

    it('places the exercise into the target course and drops the exam of the source', () => {
        const exercise = { type: ExerciseType.TEXT, exerciseGroup: { id: 9 } } as Exercise;
        const course = { id: 1 } as Course;

        const result = exerciseForImportFromFile(routerWithState({ [EXERCISE_FOR_IMPORT_FROM_FILE]: exercise }), course, undefined);

        expect(result).toBe(exercise);
        expect(result?.course).toBe(course);
        expect(result?.exerciseGroup).toBeUndefined();
    });

    it('places the exercise into the target exercise group and drops the course of the source', () => {
        const exercise = { type: ExerciseType.TEXT, course: { id: 5 } } as Exercise;
        const group = { id: 3 } as ExerciseGroup;

        const result = exerciseForImportFromFile(routerWithState({ [EXERCISE_FOR_IMPORT_FROM_FILE]: exercise }), undefined, group);

        expect(result?.course).toBeUndefined();
        expect(result?.exerciseGroup).toBe(group);
    });
});
