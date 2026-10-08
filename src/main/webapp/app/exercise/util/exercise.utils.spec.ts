import { beforeEach, describe, expect, it, vi } from 'vitest';
import { Subject, of } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { SimpleChanges } from '@angular/core';
import dayjs from 'dayjs/esm';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import {
    EditType,
    SaveExerciseCommand,
    areManualResultsAllowed,
    countSubmissions,
    getExerciseDueDate,
    getPositiveAndCappedTotalScore,
    getTotalMaxPoints,
    hasExerciseChanged,
    hasExerciseDueDatePassed,
    isResumeExerciseAvailable,
    isStartExerciseAvailable,
    isStartPracticeAvailable,
    problemStatementHasChanged,
    validateStrictDateSequence,
    withPracticeParticipations,
} from 'app/exercise/util/exercise.utils';
import { QuizExercise } from 'app/quiz/shared/entities/quiz-exercise.model';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { InitializationState } from 'app/exercise/shared/entities/participation/participation.model';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { SubmissionType } from 'app/exercise/shared/entities/submission/submission.model';
import { ProgrammingSubmission } from 'app/programming/shared/entities/programming-submission.model';

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

    describe('SaveExerciseCommand', () => {
        const savedExercise = { id: 7, title: 'saved' } as Exercise;
        const response = new HttpResponse<Exercise>({ body: savedExercise });
        let exercise: Exercise;

        const createService = () => ({
            create: vi.fn().mockReturnValue(of(response)),
            import: vi.fn().mockReturnValue(of(response)),
            update: vi.fn().mockReturnValue(of(response)),
            reevaluateAndUpdate: vi.fn().mockReturnValue(of(response)),
        });

        const createWarning = () => ({ confirmed: new Subject<void>(), reEvaluated: new Subject<void>(), canceled: new Subject<void>(), deleteFeedback: () => true });

        const createCommand = (editType: EditType, hasOpenModals = false, warning?: ReturnType<typeof createWarning>) => {
            const service = createService();
            const modalService = { hasOpenModals: vi.fn().mockReturnValue(hasOpenModals) };
            const popupService = { checkExerciseBeforeUpdate: vi.fn().mockResolvedValue({ componentInstance: warning }) };
            const command = new SaveExerciseCommand(modalService as any, popupService as any, service, { id: 7 } as Exercise, editType);
            return { command, service, popupService };
        };

        // save() subscribed to the promise of the warning service first, so awaiting it afterwards lets the subscribers run before the test continues
        const flushPromises = async (popupService: { checkExerciseBeforeUpdate: ReturnType<typeof vi.fn> }) => {
            await popupService.checkExerciseBeforeUpdate.mock.results[0].value;
        };

        beforeEach(() => {
            exercise = { id: 7, title: '  padded title  ' } as Exercise;
        });

        it('should create the exercise with a trimmed title', () => {
            const { command, service } = createCommand(EditType.CREATE);
            let result: Exercise | undefined;

            command.save(exercise, false).subscribe((saved) => (result = saved));

            expect(service.create).toHaveBeenCalledOnce();
            expect(service.create.mock.calls[0][0].title).toBe('padded title');
            expect(result).toBe(savedExercise);
        });

        it('should import the exercise', () => {
            const { command, service } = createCommand(EditType.IMPORT);

            command.save(exercise, false).subscribe();

            expect(service.import).toHaveBeenCalledOnce();
            expect(service.create).not.toHaveBeenCalled();
        });

        it.each([
            { notificationText: 'Changed the deadline', expected: { notificationText: 'Changed the deadline' } },
            { notificationText: undefined, expected: {} },
        ])('should update the exercise with the request options $expected', ({ notificationText, expected }) => {
            const { command, service } = createCommand(EditType.UPDATE);

            command.save(exercise, false, notificationText).subscribe();

            expect(service.update).toHaveBeenCalledOnce();
            expect(service.update.mock.calls[0][1]).toEqual(expected);
            expect(service.reevaluateAndUpdate).not.toHaveBeenCalled();
        });

        it('should ignore the notification text when the exercise is created', () => {
            const { command, service } = createCommand(EditType.CREATE);

            command.save(exercise, false, 'ignored').subscribe();

            expect(service.create).toHaveBeenCalledOnce();
            expect(service.update).not.toHaveBeenCalled();
        });

        it('should wait for the confirmation of an open warning modal before it updates', async () => {
            const warning = createWarning();
            const { command, service, popupService } = createCommand(EditType.UPDATE, true, warning);
            let result: Exercise | undefined;

            command.save(exercise, true, 'notify').subscribe((saved) => (result = saved));
            await flushPromises(popupService);

            expect(popupService.checkExerciseBeforeUpdate).toHaveBeenCalledWith(exercise, { id: 7 }, true);
            expect(service.update).not.toHaveBeenCalled();

            warning.confirmed.next();

            expect(service.update).toHaveBeenCalledOnce();
            expect(service.update.mock.calls[0][1]).toEqual({ notificationText: 'notify' });
            expect(result).toBe(savedExercise);
        });

        it('should reevaluate and update when the warning modal asks for it', async () => {
            const warning = createWarning();
            const { command, service, popupService } = createCommand(EditType.UPDATE, true, warning);

            command.save(exercise, false).subscribe();
            await flushPromises(popupService);
            warning.reEvaluated.next();

            expect(service.reevaluateAndUpdate).toHaveBeenCalledOnce();
            expect(service.reevaluateAndUpdate.mock.calls[0][1]).toEqual({ deleteFeedback: true });
            expect(service.update).not.toHaveBeenCalled();
        });

        it('should not save when the warning modal is canceled', async () => {
            const warning = createWarning();
            const { command, service, popupService } = createCommand(EditType.UPDATE, true, warning);
            let completed = false;

            command.save(exercise, false).subscribe({ complete: () => (completed = true) });
            await flushPromises(popupService);
            warning.canceled.next();
            warning.confirmed.next();

            expect(completed).toBe(true);
            expect(service.update).not.toHaveBeenCalled();
            expect(service.reevaluateAndUpdate).not.toHaveBeenCalled();
        });
    });

    describe('hasExerciseChanged() and problemStatementHasChanged()', () => {
        const changes = (previousValue: Partial<Exercise> | undefined, currentValue: Partial<Exercise> | undefined) =>
            ({ exercise: { previousValue, currentValue } }) as unknown as SimpleChanges;

        it('should detect a change of the exercise id', () => {
            expect(hasExerciseChanged(changes({ id: 1 }, { id: 2 }))).toBe(true);
            expect(hasExerciseChanged(changes(undefined, { id: 2 }))).toBe(true);
            expect(hasExerciseChanged(changes({ id: 2 }, { id: 2 }))).toBe(false);
        });

        it('should not detect a change without exercise or current value', () => {
            expect(hasExerciseChanged({})).toBeFalsy();
            expect(hasExerciseChanged(changes({ id: 1 }, undefined))).toBeFalsy();
        });

        it('should detect a change of the problem statement', () => {
            expect(problemStatementHasChanged(changes({ problemStatement: 'a' }, { problemStatement: 'b' }))).toBe(true);
            expect(problemStatementHasChanged(changes(undefined, { problemStatement: 'b' }))).toBe(true);
            expect(problemStatementHasChanged(changes({ problemStatement: 'a' }, { problemStatement: 'a' }))).toBe(false);
        });

        it('should not detect a problem statement change without exercise or current value', () => {
            expect(problemStatementHasChanged({})).toBeFalsy();
            expect(problemStatementHasChanged(changes({ problemStatement: 'a' }, undefined))).toBeFalsy();
        });
    });

    describe('getPositiveAndCappedTotalScore() and getTotalMaxPoints()', () => {
        it.each([
            { totalScore: -3, maxPoints: 10, expected: 0 },
            { totalScore: 12.5, maxPoints: 10, expected: 10 },
            { totalScore: 3.14159, maxPoints: 10, expected: 3.14 },
        ])('should return $expected for the score $totalScore and the maximum $maxPoints', ({ totalScore, maxPoints, expected }) => {
            expect(getPositiveAndCappedTotalScore(totalScore, maxPoints)).toBe(expected);
        });

        it('should add the bonus points to the maximum points', () => {
            expect(getTotalMaxPoints({ maxPoints: 10, bonusPoints: 5 } as Exercise)).toBe(15);
            expect(getTotalMaxPoints({ maxPoints: 10 } as Exercise)).toBe(10);
            expect(getTotalMaxPoints(undefined)).toBe(0);
        });
    });

    describe('countSubmissions()', () => {
        it('should count the distinct commits of manual submissions only', () => {
            const submission = (type: SubmissionType, commitHash?: string) => ({ type, commitHash }) as ProgrammingSubmission;
            const participation = {
                submissions: [
                    submission(SubmissionType.MANUAL, 'a'),
                    submission(SubmissionType.MANUAL, 'a'),
                    submission(SubmissionType.MANUAL, 'b'),
                    submission(SubmissionType.MANUAL, undefined),
                    submission(SubmissionType.TEST, 'c'),
                ],
            } as StudentParticipation;

            expect(countSubmissions(participation)).toBe(2);
            expect(countSubmissions({} as StudentParticipation)).toBe(0);
            expect(countSubmissions(undefined)).toBe(0);
        });
    });
});
