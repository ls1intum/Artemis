import { describe, expect, it } from 'vitest';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { participationChildRouteSegments } from 'app/course/overview/exercise-details/participation-child-route';

function exerciseOfType(type: ExerciseType, allowOnlineEditor?: boolean): Exercise {
    return { id: 42, type, allowOnlineEditor } as unknown as Exercise;
}

const participation = { id: 7 } as StudentParticipation;

describe('participationChildRouteSegments', () => {
    it.each([
        [ExerciseType.TEXT, ['text-exercises', 42, 'participate', 7]],
        [ExerciseType.MODELING, ['modeling-exercises', 42, 'participate', 7]],
        [ExerciseType.FILE_UPLOAD, ['file-upload-exercises', 42, 'participate', 7]],
    ])('routes a %s exercise to its participation route', (type, expected) => {
        expect(participationChildRouteSegments(exerciseOfType(type), participation)).toEqual(expected);
    });

    // A UserStoryExercise is a ProgrammingExercise sharing its milestone group's repository, so both must resolve to
    // the same code editor route — otherwise the split panel never activates its outlet and shows a blank editor.
    it.each([ExerciseType.PROGRAMMING, ExerciseType.USER_STORY])('routes a %s exercise with the online editor to the code editor', (type) => {
        expect(participationChildRouteSegments(exerciseOfType(type, true), participation)).toEqual(['programming-exercises', 42, 'code-editor', 7]);
    });

    it.each([ExerciseType.PROGRAMMING, ExerciseType.USER_STORY])('returns undefined for a %s exercise without the online editor', (type) => {
        expect(participationChildRouteSegments(exerciseOfType(type, false), participation)).toBeUndefined();
    });

    it('returns undefined for a quiz, which routes by mode rather than by participation', () => {
        expect(participationChildRouteSegments(exerciseOfType(ExerciseType.QUIZ), participation)).toBeUndefined();
    });

    it('returns undefined when either id is missing', () => {
        expect(participationChildRouteSegments({ type: ExerciseType.TEXT } as Exercise, participation)).toBeUndefined();
        expect(participationChildRouteSegments(exerciseOfType(ExerciseType.TEXT), {} as StudentParticipation)).toBeUndefined();
    });
});
