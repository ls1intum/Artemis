import dayjs from 'dayjs/esm';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import {
    areManualResultsAllowed,
    getExerciseDueDate,
    hasExerciseDueDatePassed,
    isResumeExerciseAvailable,
    isStartExerciseAvailable,
    isStartPracticeAvailable,
} from 'app/exercises/shared/exercise/exercise.utils';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { InitializationState } from 'app/entities/participation/participation.model';
import { AssessmentType } from 'app/entities/assessment-type.model';

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

    describe('getExerciseDueDate()', () => {
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
    });

    describe('hasExerciseDueDatePassed()', () => {
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

    describe('isStartPracticeAvailable()', () => {
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
            { dueDate: dayjs().subtract(1, 'day'), participation: undefined, startPracticeAvailable: true },
            { dueDate: dayjs().add(1, 'day'), participation: undefined, startPracticeAvailable: false },
            { dueDate: undefined, participation: undefined, startPracticeAvailable: false },
            { dueDate: dayjs().subtract(1, 'day'), participation: { initializationState: InitializationState.INITIALIZED }, startPracticeAvailable: false },
            { dueDate: dayjs().subtract(1, 'day'), participation: { initializationState: InitializationState.REPO_COPIED }, startPracticeAvailable: true },
        ])('should determine correctly if the student can practice a programming exercise', ({ dueDate, participation, startPracticeAvailable }) => {
            const exercise: ProgrammingExercise = {
                numberOfAssessmentsOfCorrectionRounds: [],
                secondCorrectionEnabled: false,
                studentAssignedTeamIdComputed: false,
                type: ExerciseType.PROGRAMMING,
                dueDate,
            };

            expect(isStartPracticeAvailable(exercise, participation)).toBe(startPracticeAvailable);
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

        it.each([
            [{ dueDate: undefined } as Exercise, undefined, true],
            [{ dueDate: dayjs().add(1, 'hour') } as Exercise, undefined, true],
            [{ dueDate: dayjs().subtract(1, 'hour') } as Exercise, undefined, true],
            [{ dueDate: undefined, type: ExerciseType.PROGRAMMING } as Exercise, undefined, true],
            [{ dueDate: dayjs().add(1, 'hour'), type: ExerciseType.PROGRAMMING } as Exercise, undefined, true],
            [{ dueDate: dayjs().subtract(1, 'hour'), type: ExerciseType.PROGRAMMING } as Exercise, undefined, false],
            [{ dueDate: dayjs().add(1, 'hour'), type: ExerciseType.PROGRAMMING } as Exercise, { initializationState: InitializationState.INITIALIZED }, false],
            [{ dueDate: dayjs().add(1, 'hour'), type: ExerciseType.PROGRAMMING } as Exercise, { initializationState: InitializationState.REPO_COPIED }, true],
            [{ dueDate: dayjs().subtract(1, 'hour'), type: ExerciseType.PROGRAMMING } as Exercise, { initializationState: InitializationState.REPO_COPIED }, false],
        ])('should correctly determine if starting an exercise is available', (exercise: Exercise, participation: StudentParticipation | undefined, expected: boolean) => {
            expect(isStartExerciseAvailable(exercise, participation)).toBe(expected);
        });
    });

    describe('isResumeExerciseAvailable()', () => {
        it.each([
            [{ dueDate: undefined } as Exercise, {}, true],
            [{ dueDate: dayjs().add(1, 'hour') } as Exercise, {}, true],
            [{ dueDate: dayjs().subtract(1, 'hour') } as Exercise, {}, false],
            [{ dueDate: undefined } as Exercise, { testRun: true }, false],
            [{ dueDate: dayjs().add(1, 'hour') } as Exercise, { testRun: true }, false],
            [{ dueDate: dayjs().subtract(1, 'hour') } as Exercise, { testRun: true }, true],
        ])('should correctly determine if resuming an exercise is available', (exercise: Exercise, participation: StudentParticipation | undefined, expected: boolean) => {
            expect(isResumeExerciseAvailable(exercise, participation)).toBe(expected);
        });
    });

    describe('areManualResultsAllowed()', () => {
        it.each([
            [{ type: ExerciseType.MODELING } as Exercise, true],
            [{ type: ExerciseType.MODELING, dueDate: dayjs().subtract(1, 'hour') } as Exercise, true],
            [{ type: ExerciseType.MODELING, dueDate: dayjs().add(1, 'hour') } as Exercise, false],
            [{ type: ExerciseType.PROGRAMMING } as Exercise, true],
            [{ type: ExerciseType.PROGRAMMING, dueDate: dayjs().subtract(1, 'hour') } as Exercise, true],
            [{ type: ExerciseType.PROGRAMMING, dueDate: dayjs().subtract(1, 'hour'), assessmentType: AssessmentType.AUTOMATIC } as Exercise, false],
            [{ type: ExerciseType.PROGRAMMING, dueDate: dayjs().add(1, 'hour') } as Exercise, false],
            [
                {
                    type: ExerciseType.PROGRAMMING,
                    dueDate: dayjs().subtract(2, 'hours'),
                    buildAndTestStudentSubmissionsAfterDueDate: dayjs().subtract(1, 'hour'),
                } as ProgrammingExercise,
                true,
            ],
            [
                {
                    type: ExerciseType.PROGRAMMING,
                    dueDate: dayjs().subtract(2, 'hours'),
                    buildAndTestStudentSubmissionsAfterDueDate: dayjs().add(1, 'hour'),
                } as ProgrammingExercise,
                false,
            ],
            [{ type: ExerciseType.QUIZ, dueDate: dayjs().subtract(1, 'hour') } as Exercise, false],
        ])('should correctly determine if manual results are allowed', (exercise: Exercise, expected: boolean) => {
            expect(areManualResultsAllowed(exercise)).toBe(expected);
        });
    });
});
