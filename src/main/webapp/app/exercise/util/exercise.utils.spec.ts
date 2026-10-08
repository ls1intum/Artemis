import { describe, expect, it } from 'vitest';
import dayjs from 'dayjs/esm';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import {
    areManualResultsAllowed,
    getExerciseDueDate,
    hasExerciseDueDatePassed,
    isResumeExerciseAvailable,
    isStartExerciseAvailable,
    isStartPracticeAvailable,
    validateStrictDateSequence,
    withPracticeParticipations,
} from 'app/exercise/util/exercise.utils';
import { QuizExercise } from 'app/quiz/shared/entities/quiz-exercise.model';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { InitializationState } from 'app/exercise/shared/entities/participation/participation.model';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';

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

    describe('validateStrictDateSequence()', () => {
        const date = dayjs('2026-01-01T12:00:00Z');

        it('should validate all configured preceding and following dates', () => {
            expect(validateStrictDateSequence([date.subtract(2, 'hour'), date.subtract(1, 'hour')], date, [date.add(1, 'hour'), date.add(2, 'hour')])).toBe(true);
            expect(validateStrictDateSequence([date.subtract(1, 'hour'), date.add(1, 'hour')], date, [])).toBe(false);
            expect(validateStrictDateSequence([], date, [date.add(1, 'hour'), date.subtract(1, 'hour')])).toBe(false);
        });

        it('should ignore missing dates and reject equal dates', () => {
            expect(validateStrictDateSequence([undefined], undefined, [undefined])).toBe(true);
            expect(validateStrictDateSequence([date], date, [])).toBe(false);
            expect(validateStrictDateSequence([], date, [date])).toBe(false);
        });
    });

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
            expect(hasExerciseDueDatePassed(exercise)).toBe(false);

            const participation = participationWithDueDate(dayjs().subtract(1, 'hour'));
            expect(hasExerciseDueDatePassed(exercise, participation)).toBe(false);
        });

        it('the due date should have passed if the exercise due date has passed and no individual due date exists', () => {
            const exercise = exerciseWithDueDate(dayjs().subtract(1, 'hour'));
            const participation = participationWithDueDate(undefined);

            expect(hasExerciseDueDatePassed(exercise)).toBe(true);
            expect(hasExerciseDueDatePassed(exercise, participation)).toBe(true);
        });

        it('the due date should not have passed if the individual due date is in the future', () => {
            const exercise = exerciseWithDueDate(dayjs().subtract(1, 'hour'));
            const participation = participationWithDueDate(dayjs().add(1, 'hour'));

            expect(hasExerciseDueDatePassed(exercise, participation)).toBe(false);
        });
    });

    describe('isStartPracticeAvailable()', () => {
        it.each([{ quizEnded: true }, { quizEnded: false }])('should determine correctly if the student can practice a quiz', ({ quizEnded }) => {
            const exercise: QuizExercise = {
                numberOfAssessmentsOfCorrectionRounds: [],
                secondCorrectionEnabled: false,
                studentAssignedTeamIdComputed: false,
                type: ExerciseType.QUIZ,
                dueDate: quizEnded ? dayjs().subtract(1, 'day') : dayjs().add(1, 'day'),
                quizEnded,
            };

            expect(isStartPracticeAvailable(exercise)).toBe(!!quizEnded);
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

        it.each([
            { type: ExerciseType.PROGRAMMING, teamMode: false },
            { type: ExerciseType.PROGRAMMING, teamMode: true },
            { type: ExerciseType.TEXT, teamMode: false },
            { type: ExerciseType.TEXT, teamMode: true },
            { type: ExerciseType.MODELING, teamMode: false },
            { type: ExerciseType.MODELING, teamMode: true },
        ])('should allow practicing a $type exercise after the due date, also for a team exercise (teamMode: $teamMode)', ({ type, teamMode }) => {
            const exercise: Exercise = {
                numberOfAssessmentsOfCorrectionRounds: [],
                secondCorrectionEnabled: false,
                studentAssignedTeamIdComputed: false,
                type,
                teamMode,
                dueDate: dayjs().subtract(1, 'day'),
            };

            expect(isStartPracticeAvailable(exercise)).toBe(true);
        });

        it.each([ExerciseType.PROGRAMMING, ExerciseType.TEXT, ExerciseType.MODELING])('should not allow practicing a team %s exercise before the due date', (type) => {
            const exercise: Exercise = {
                numberOfAssessmentsOfCorrectionRounds: [],
                secondCorrectionEnabled: false,
                studentAssignedTeamIdComputed: false,
                type,
                teamMode: true,
                dueDate: dayjs().add(1, 'day'),
            };

            expect(isStartPracticeAvailable(exercise)).toBe(false);
        });

        it('should not offer starting practice again once the practice participation of a team text exercise exists', () => {
            const exercise: Exercise = {
                numberOfAssessmentsOfCorrectionRounds: [],
                secondCorrectionEnabled: false,
                studentAssignedTeamIdComputed: false,
                type: ExerciseType.TEXT,
                teamMode: true,
                dueDate: dayjs().subtract(1, 'day'),
            };

            expect(isStartPracticeAvailable(exercise, { testRun: true })).toBe(false);
        });

        describe('with an individual due date of the graded participation', () => {
            const exerciseOfType = (type: ExerciseType, dueDate: dayjs.Dayjs | undefined, teamMode = false): Exercise => ({
                numberOfAssessmentsOfCorrectionRounds: [],
                secondCorrectionEnabled: false,
                studentAssignedTeamIdComputed: false,
                type,
                teamMode,
                dueDate,
            });
            const gradedParticipation = (individualDueDate?: dayjs.Dayjs): StudentParticipation => ({ testRun: false, individualDueDate }) as StudentParticipation;
            const practiceParticipation = { testRun: true, initializationState: InitializationState.REPO_COPIED } as StudentParticipation;
            const practiceTypes = [ExerciseType.PROGRAMMING, ExerciseType.TEXT, ExerciseType.MODELING];

            it.each(practiceTypes)('should not offer practice for a %s exercise while an extension lies in the future', (type) => {
                const exercise = exerciseOfType(type, dayjs().subtract(1, 'day'));

                expect(isStartPracticeAvailable(exercise, undefined, gradedParticipation(dayjs().add(1, 'hour')))).toBe(false);
            });

            it.each(practiceTypes)('should offer practice for a %s exercise once the extension has passed', (type) => {
                const exercise = exerciseOfType(type, dayjs().subtract(2, 'day'));

                expect(isStartPracticeAvailable(exercise, undefined, gradedParticipation(dayjs().subtract(1, 'hour')))).toBe(true);
            });

            it.each(practiceTypes)('should offer practice for a %s exercise if the graded participation has no extension', (type) => {
                const exercise = exerciseOfType(type, dayjs().subtract(1, 'day'));

                expect(isStartPracticeAvailable(exercise, undefined, gradedParticipation())).toBe(true);
            });

            it.each(practiceTypes)('should wait for the exercise due date of a %s exercise if the extension is earlier than that', (type) => {
                const exerciseDueDateInFuture = exerciseOfType(type, dayjs().add(1, 'day'));
                const exerciseDueDatePassed = exerciseOfType(type, dayjs().subtract(1, 'hour'));
                const earlierExtension = gradedParticipation(dayjs().subtract(1, 'day'));

                // the server answers 403 until the exercise due date has passed, whatever the individual due date says
                expect(isStartPracticeAvailable(exerciseDueDateInFuture, undefined, earlierExtension)).toBe(false);
                expect(isStartPracticeAvailable(exerciseDueDatePassed, undefined, earlierExtension)).toBe(true);
            });

            it.each(practiceTypes)('should not offer practice without a due date for a %s exercise whatever the extension is', (type) => {
                const exercise = exerciseOfType(type, undefined);

                expect(isStartPracticeAvailable(exercise, undefined, gradedParticipation(dayjs().subtract(1, 'day')))).toBe(false);
            });

            it('should keep using the practice participation for the practice checks and the deadline participation for the deadline', () => {
                const exercise = exerciseOfType(ExerciseType.PROGRAMMING, dayjs().subtract(1, 'day'));

                // a practice participation that still needs its setup does not hide the button, the extension of the graded one does
                expect(isStartPracticeAvailable(exercise, practiceParticipation, gradedParticipation())).toBe(true);
                expect(isStartPracticeAvailable(exercise, practiceParticipation, gradedParticipation(dayjs().add(1, 'hour')))).toBe(false);
                // a finished setup of the practice participation hides the button even without an extension
                expect(
                    isStartPracticeAvailable(exercise, { testRun: true, initializationState: InitializationState.INITIALIZED } as StudentParticipation, gradedParticipation()),
                ).toBe(false);
            });

            it.each([ExerciseType.TEXT, ExerciseType.MODELING])('should not offer practice for a %s exercise that already has a practice participation', (type) => {
                const exercise = exerciseOfType(type, dayjs().subtract(1, 'day'));

                expect(isStartPracticeAvailable(exercise, { testRun: true } as StudentParticipation, gradedParticipation())).toBe(false);
            });

            it.each(practiceTypes)('should evaluate the extension of the team for a team %s exercise', (type) => {
                const exercise = exerciseOfType(type, dayjs().subtract(1, 'day'), true);
                const teamParticipation = gradedParticipation(dayjs().add(1, 'hour'));

                expect(isStartPracticeAvailable(exercise, undefined, teamParticipation)).toBe(false);
                expect(isStartPracticeAvailable(exercise, undefined, gradedParticipation(dayjs().subtract(1, 'hour')))).toBe(true);
            });

            it('should default the deadline participation to the participation for backward compatibility', () => {
                const exercise = exerciseOfType(ExerciseType.TEXT, dayjs().subtract(1, 'day'));

                expect(isStartPracticeAvailable(exercise, gradedParticipation(dayjs().add(1, 'hour')))).toBe(false);
                expect(isStartPracticeAvailable(exercise, gradedParticipation(dayjs().subtract(1, 'hour')))).toBe(true);
            });
        });

        it.each([ExerciseType.MODELING, ExerciseType.TEXT, ExerciseType.FILE_UPLOAD, undefined])('should not allow practicing for other exercises', (type) => {
            const exercise: Exercise = {
                numberOfAssessmentsOfCorrectionRounds: [],
                secondCorrectionEnabled: false,
                studentAssignedTeamIdComputed: false,
                type,
            };

            expect(isStartPracticeAvailable(exercise)).toBe(false);
        });

        it.each([
            [{ dueDate: undefined } as Exercise, undefined, true],
            [{ dueDate: dayjs().add(1, 'hour') } as Exercise, undefined, true],
            [{ dueDate: dayjs().subtract(1, 'hour') } as Exercise, undefined, false],
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

    describe('withPracticeParticipations()', () => {
        const teamParticipation = { id: 1, testRun: false } as StudentParticipation;
        const practiceParticipation = { id: 2, testRun: true } as StudentParticipation;

        it('should keep the own practice participation when the team assignment replaces the participations of the team', () => {
            const newTeamParticipation = { id: 3, testRun: false } as StudentParticipation;

            const result = withPracticeParticipations([teamParticipation, practiceParticipation], [newTeamParticipation]);

            expect(result).toEqual([newTeamParticipation, practiceParticipation]);
        });

        it('should not duplicate a practice participation that the assignment delivers as well', () => {
            const delivered = { id: 2, testRun: true } as StudentParticipation;

            expect(withPracticeParticipations([teamParticipation, practiceParticipation], [teamParticipation, delivered])).toEqual([teamParticipation, delivered]);
        });

        it('should return the delivered participations when no practice participation exists', () => {
            expect(withPracticeParticipations([teamParticipation], [])).toEqual([]);
            expect(withPracticeParticipations(undefined, [teamParticipation])).toEqual([teamParticipation]);
        });
    });
});
