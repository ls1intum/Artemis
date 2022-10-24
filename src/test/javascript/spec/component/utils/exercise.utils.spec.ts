import dayjs from 'dayjs/esm';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import {
    getExerciseDueDate,
    hasExerciseDueDatePassed,
    isResumeExerciseAvailable,
    isStartExerciseAvailable,
    isStartPracticeAvailable,
} from 'app/exercises/shared/exercise/exercise.utils';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';

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

        expect(getExerciseDueDate(exercise, participation)).toBeUndefined();
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

    it.each([
        { isOpenForPractice: true, quizEnded: true },
        { isOpenForPractice: true, quizEnded: false },
        { isOpenForPractice: undefined, quizEnded: true },
    ])('should determine correctly if the student can practice a quiz', ({ isOpenForPractice, quizEnded }) => {
        const exercise: QuizExercise = {
            numberOfAssessmentsOfCorrectionRounds: [],
            secondCorrectionEnabled: false,
            studentAssignedTeamIdComputed: false,
            type: ExerciseType.QUIZ,
            isOpenForPractice,
            quizEnded,
        };

        expect(isStartPracticeAvailable(exercise)).toBe(!!isOpenForPractice && !!quizEnded);
    });

    it.each([
        { dueDate: dayjs().subtract(1, 'day'), startPracticeAvailable: true },
        { dueDate: dayjs().add(1, 'day'), startPracticeAvailable: false },
        { dueDate: undefined, startPracticeAvailable: false },
    ])('should determine correctly if the student can practice a programming exercise', ({ dueDate, startPracticeAvailable }) => {
        const exercise: ProgrammingExercise = {
            numberOfAssessmentsOfCorrectionRounds: [],
            secondCorrectionEnabled: false,
            studentAssignedTeamIdComputed: false,
            type: ExerciseType.PROGRAMMING,
            dueDate,
        };

        expect(isStartPracticeAvailable(exercise)).toBe(startPracticeAvailable);
    });

    it.each([ExerciseType.MODELING, ExerciseType.TEXT, ExerciseType.FILE_UPLOAD, undefined])('should not allow practicing for other exercises', (type) => {
        const exercise: Exercise = {
            numberOfAssessmentsOfCorrectionRounds: [],
            secondCorrectionEnabled: false,
            studentAssignedTeamIdComputed: false,
            type,
        };

        expect(isStartPracticeAvailable(exercise)).toBeFalse();
    });

    it('should allow students to start the exercise if there is no due date', () => {
        const exercise = exerciseWithDueDate(undefined);
        expect(isStartExerciseAvailable(exercise)).toBeTrue();
    });

    it('should allow students to start the exercise if the due date is in the future', () => {
        const exercise = exerciseWithDueDate(dayjs().add(1, 'hour'));
        expect(isStartExerciseAvailable(exercise)).toBeTrue();
    });

    it('should not allow students to start the exercise if the due date is in the past', () => {
        const exercise = exerciseWithDueDate(dayjs().subtract(1, 'day'));
        expect(isStartExerciseAvailable(exercise)).toBeFalse();
    });

    it('should allow students to resume the exercise if there is no due date', () => {
        const exercise = exerciseWithDueDate(undefined);
        expect(isResumeExerciseAvailable()).toBeTrue();
    });

    it('should allow students to resume the exercise if the exercise due date is in the future', () => {
        const exercise = exerciseWithDueDate(dayjs().add(1, 'hour'));
        const participation = participationWithDueDate(undefined);

        expect(isResumeExerciseAvailable(participation)).toBeTrue();
    });

    it('should not allow students to resume the exercise if the exercise due date is in the past', () => {
        const exercise = exerciseWithDueDate(dayjs().subtract(1, 'hour'));
        const participation = participationWithDueDate(undefined);

        expect(isResumeExerciseAvailable(participation)).toBeFalse();
    });

    it('should allow students to resume the exercise if the individual due date is in the future', () => {
        const exercise = exerciseWithDueDate(dayjs().subtract(1, 'hour'));
        const participation = participationWithDueDate(dayjs().add(1, 'hour'));

        expect(isResumeExerciseAvailable(participation)).toBeTrue();
    });

    it('should not allow students to resume the exercise if the individual due date is in the past', () => {
        const exercise = exerciseWithDueDate(dayjs().add(1, 'hour'));
        const participation = participationWithDueDate(dayjs().subtract(1, 'hour'));

        expect(isResumeExerciseAvailable(participation)).toBeFalse();
    });
});
