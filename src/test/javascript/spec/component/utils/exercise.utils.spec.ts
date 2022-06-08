import dayjs from 'dayjs/esm';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { getExerciseDueDate, hasExerciseDueDatePassed } from 'app/exercises/shared/exercise/exercise.utils';

describe('ExerciseUtils', () => {
    const exerciseWithDueDate = (dueDate?: dayjs.Dayjs) => {
        const exercise = new ProgrammingExercise(undefined, undefined);
        exercise.dueDate = dueDate;
        return exercise;
    };

    const participationWithDueDate = (dueDate?: dayjs.Dayjs) => {
        const participation = new StudentParticipation();
        participation.individualDueDate = dueDate;
        return participation;
    };

    it('should return no due date if the exercise has no due date', () => {
        const exercise = exerciseWithDueDate(undefined);
        const individualDueDate = dayjs().add(1, 'hour');
        const participation = participationWithDueDate(individualDueDate);

        expect(getExerciseDueDate(exercise, participation)).toBe(undefined);
    });

    it('should return the exercise due date if no individual due date exists', () => {
        const dueDate = dayjs();
        const exercise = exerciseWithDueDate(dueDate);
        const participation = participationWithDueDate(undefined);

        expect(getExerciseDueDate(exercise)).toEqual(dueDate);
        expect(getExerciseDueDate(exercise, participation)).toEqual(dueDate);
    });

    it('should return the individual due date instead of the exercise one if both exist', () => {
        const exercise = exerciseWithDueDate(dayjs());
        const individualDueDate = dayjs().add(1, 'hour');
        const participation = participationWithDueDate(individualDueDate);

        expect(getExerciseDueDate(exercise, participation)).toEqual(individualDueDate);
    });

    it('the due date should not have passed if the exercise has no due date', () => {
        const exercise = exerciseWithDueDate(undefined);
        expect(hasExerciseDueDatePassed(exercise)).toBeFalse();

        const participation = participationWithDueDate(dayjs().subtract(1, 'hour'));
        expect(hasExerciseDueDatePassed(exercise, participation)).toBeFalse();
    });

    it('the due date should have passed if the exercise due date has passed and no individual due date exists', () => {
        const exercise = exerciseWithDueDate(dayjs().subtract(1, 'hour'));
        const participation = participationWithDueDate(undefined);

        expect(hasExerciseDueDatePassed(exercise)).toBeTrue();
        expect(hasExerciseDueDatePassed(exercise, participation)).toBeTrue();
    });

    it('the due date should not have passed if the individual due date is in the future', () => {
        const exercise = exerciseWithDueDate(dayjs().subtract(1, 'hour'));
        const participation = participationWithDueDate(dayjs().add(1, 'hour'));

        expect(hasExerciseDueDatePassed(exercise, participation)).toBeFalse();
    });
});
