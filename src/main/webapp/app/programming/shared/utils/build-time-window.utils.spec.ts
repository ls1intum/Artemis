import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import dayjs from 'dayjs/esm';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ParticipationType } from 'app/exercise/shared/entities/participation/participation.model';
import { ProgrammingExerciseStudentParticipation } from 'app/exercise/shared/entities/participation/programming-exercise-student-participation.model';
import { TemplateProgrammingExerciseParticipation } from 'app/exercise/shared/entities/participation/template-programming-exercise-participation.model';
import { BuildTimeWindow } from 'app/programming/shared/entities/programming-exercise-build-advanced-settings';
import {
    BuildTimeWindowError,
    BuildWindowPhase,
    convertBuildConfigDatesFromServer,
    getBuildTimeWindow,
    getBuildTimeWindowError,
    getBuildTimeWindowFor,
    getBuildWindowPhase,
    injectBuildWindowPhase,
    isBuildWindowEnforcedFor,
    isPushOutsideBuildWindow,
    normalizeBuildTimeWindow,
} from 'app/programming/shared/utils/build-time-window.utils';

describe('build time window utils', () => {
    const start = dayjs('2026-10-01T10:00:00Z');
    const end = dayjs('2026-10-01T12:00:00Z');

    const exerciseWith = (buildTimeWindow?: BuildTimeWindow): ProgrammingExercise => {
        const exercise = new ProgrammingExercise(undefined, undefined);
        exercise.buildConfig!.advancedSettings = buildTimeWindow ? { buildTimeWindow } : undefined;
        return exercise;
    };

    const studentParticipation = (testRun?: boolean): ProgrammingExerciseStudentParticipation => {
        const participation = new ProgrammingExerciseStudentParticipation();
        participation.testRun = testRun;
        return participation;
    };

    describe('normalizeBuildTimeWindow', () => {
        it('returns undefined without a window or without a valid bound', () => {
            expect(normalizeBuildTimeWindow(undefined)).toBeUndefined();
            expect(normalizeBuildTimeWindow({})).toBeUndefined();
            expect(normalizeBuildTimeWindow({ start: dayjs('not a date') })).toBeUndefined();
        });

        it('converts date strings of a server response to dayjs objects', () => {
            const window = normalizeBuildTimeWindow({ start: '2026-10-01T10:00:00Z' as unknown as dayjs.Dayjs });

            expect(dayjs.isDayjs(window?.start)).toBe(true);
            expect(window?.start?.isSame(start)).toBe(true);
            expect(window?.end).toBeUndefined();
        });
    });

    describe('getBuildTimeWindow', () => {
        it('reads the window of a programming exercise', () => {
            expect(getBuildTimeWindow(exerciseWith({ start, end }))).toEqual({ start, end });
        });

        it('returns undefined for exercises without a window and for other exercise types', () => {
            expect(getBuildTimeWindow(undefined)).toBeUndefined();
            expect(getBuildTimeWindow(exerciseWith())).toBeUndefined();

            const textExercise = exerciseWith({ start, end });
            textExercise.type = ExerciseType.TEXT;
            expect(getBuildTimeWindow(textExercise)).toBeUndefined();
        });
    });

    describe('getBuildTimeWindowFor', () => {
        const dueDate = end.add(1, 'day');

        const exerciseWithDueDate = (buildTimeWindow?: BuildTimeWindow): ProgrammingExercise => {
            const exercise = exerciseWith(buildTimeWindow);
            exercise.dueDate = dueDate;
            return exercise;
        };

        const participationWithExtension = (individualDueDate?: dayjs.Dayjs): ProgrammingExerciseStudentParticipation => {
            const participation = studentParticipation();
            participation.individualDueDate = individualDueDate;
            return participation;
        };

        it('moves only the end of the window by the individual due date extension', () => {
            const window = getBuildTimeWindowFor(exerciseWithDueDate({ start, end }), participationWithExtension(dueDate.add(5, 'hour')));

            expect(window?.start?.isSame(start)).toBe(true);
            expect(window?.end?.isSame(end.add(5, 'hour'))).toBe(true);
        });

        it('keeps the window of the exercise without an extension', () => {
            const exercise = exerciseWithDueDate({ start, end });

            expect(getBuildTimeWindowFor(exercise, undefined)).toEqual({ start, end });
            expect(getBuildTimeWindowFor(exercise, participationWithExtension())).toEqual({ start, end });
            expect(getBuildTimeWindowFor(exercise, participationWithExtension(dueDate))).toEqual({ start, end });
            // an individual due date before the due date never shortens the window
            expect(getBuildTimeWindowFor(exercise, participationWithExtension(dueDate.subtract(1, 'hour')))).toEqual({ start, end });
            expect(getBuildTimeWindowFor(exerciseWith({ start, end }), participationWithExtension(dueDate.add(1, 'hour')))).toEqual({ start, end });
        });

        it('keeps an open end and returns nothing without a window', () => {
            expect(getBuildTimeWindowFor(exerciseWithDueDate({ start }), participationWithExtension(dueDate.add(1, 'hour')))).toEqual({ start, end: undefined });
            expect(getBuildTimeWindowFor(exerciseWithDueDate(), participationWithExtension(dueDate.add(1, 'hour')))).toBeUndefined();
        });
    });

    describe('convertBuildConfigDatesFromServer', () => {
        it('converts the window dates in place', () => {
            const exercise = exerciseWith({ start: '2026-10-01T10:00:00Z' as unknown as dayjs.Dayjs, end: '2026-10-01T12:00:00Z' as unknown as dayjs.Dayjs });

            convertBuildConfigDatesFromServer(exercise.buildConfig);

            const window = exercise.buildConfig!.advancedSettings!.buildTimeWindow!;
            expect(dayjs.isDayjs(window.start)).toBe(true);
            expect(window.end?.isSame(end)).toBe(true);
        });

        it('ignores a build config without advanced settings', () => {
            expect(() => convertBuildConfigDatesFromServer(undefined)).not.toThrow();
            expect(() => convertBuildConfigDatesFromServer(exerciseWith().buildConfig)).not.toThrow();
        });
    });

    describe('getBuildWindowPhase', () => {
        it('is NONE without a window', () => {
            expect(getBuildWindowPhase(undefined, start)).toBe(BuildWindowPhase.NONE);
        });

        it('treats both bounds as inclusive', () => {
            expect(getBuildWindowPhase({ start, end }, start.subtract(1, 'millisecond'))).toBe(BuildWindowPhase.BEFORE);
            expect(getBuildWindowPhase({ start, end }, start)).toBe(BuildWindowPhase.DURING);
            expect(getBuildWindowPhase({ start, end }, end)).toBe(BuildWindowPhase.DURING);
            expect(getBuildWindowPhase({ start, end }, end.add(1, 'millisecond'))).toBe(BuildWindowPhase.AFTER);
        });

        it('handles a window with only a start', () => {
            expect(getBuildWindowPhase({ start }, start.subtract(1, 'day'))).toBe(BuildWindowPhase.BEFORE);
            expect(getBuildWindowPhase({ start }, start.add(100, 'year'))).toBe(BuildWindowPhase.DURING);
        });

        it('handles a window with only an end', () => {
            expect(getBuildWindowPhase({ end }, end.subtract(100, 'year'))).toBe(BuildWindowPhase.DURING);
            expect(getBuildWindowPhase({ end }, end.add(1, 'day'))).toBe(BuildWindowPhase.AFTER);
        });
    });

    describe('isBuildWindowEnforcedFor', () => {
        it('applies to the graded student repository of non-staff users', () => {
            expect(isBuildWindowEnforcedFor(exerciseWith({ start }), studentParticipation())).toBe(true);
            expect(isBuildWindowEnforcedFor(exerciseWith({ start }), studentParticipation(false))).toBe(true);
        });

        it.each([
            ['editors and instructors', () => Object.assign(exerciseWith({ start }), { isAtLeastEditor: true }), studentParticipation()],
            ['practice mode', () => exerciseWith({ start }), studentParticipation(true)],
            ['template participations', () => exerciseWith({ start }), new TemplateProgrammingExerciseParticipation()],
            ['a missing participation', () => exerciseWith({ start }), undefined],
            ['a missing exercise', () => undefined, studentParticipation()],
        ])('does not apply to %s', (_, exercise, participation) => {
            expect(isBuildWindowEnforcedFor(exercise(), participation)).toBe(false);
        });

        it('relies on the participation type', () => {
            const participation = studentParticipation();
            participation.type = ParticipationType.STUDENT;

            expect(isBuildWindowEnforcedFor(exerciseWith({ start }), participation)).toBe(false);
        });
    });

    describe('isPushOutsideBuildWindow', () => {
        const exercise = exerciseWith({ start, end });

        it('is true before and after the window', () => {
            expect(isPushOutsideBuildWindow(exercise, studentParticipation(), start.subtract(1, 'minute'))).toBe(true);
            expect(isPushOutsideBuildWindow(exercise, studentParticipation(), end.add(1, 'minute'))).toBe(true);
        });

        it('is false inside the window, without a window and for staff', () => {
            expect(isPushOutsideBuildWindow(exercise, studentParticipation(), start)).toBe(false);
            expect(isPushOutsideBuildWindow(exerciseWith(), studentParticipation(), end.add(1, 'year'))).toBe(false);
            expect(isPushOutsideBuildWindow(Object.assign(exerciseWith({ start, end }), { isAtLeastEditor: true }), studentParticipation(), end.add(1, 'year'))).toBe(false);
        });

        it('does not count a push as outside while the individual due date extension keeps the window open', () => {
            const exerciseWithDueDate = exerciseWith({ start, end });
            exerciseWithDueDate.dueDate = end;
            const participation = studentParticipation();
            participation.individualDueDate = end.add(3, 'hour');

            expect(isPushOutsideBuildWindow(exerciseWithDueDate, participation, end.add(2, 'hour'))).toBe(false);
            expect(isPushOutsideBuildWindow(exerciseWithDueDate, participation, end.add(4, 'hour'))).toBe(true);
            expect(isPushOutsideBuildWindow(exerciseWithDueDate, studentParticipation(), end.add(2, 'hour'))).toBe(true);
        });

        it('uses the current time by default', () => {
            vi.useFakeTimers();
            try {
                vi.setSystemTime(end.add(1, 'hour').toDate());
                expect(isPushOutsideBuildWindow(exercise, studentParticipation())).toBe(true);
                vi.setSystemTime(start.add(1, 'hour').toDate());
                expect(isPushOutsideBuildWindow(exercise, studentParticipation())).toBe(false);
            } finally {
                vi.useRealTimers();
            }
        });
    });

    describe('getBuildTimeWindowError', () => {
        const exerciseWithDates = (buildTimeWindow: BuildTimeWindow | undefined, dates: { release?: dayjs.Dayjs; start?: dayjs.Dayjs; due?: dayjs.Dayjs }) => {
            const exercise = exerciseWith(buildTimeWindow);
            exercise.releaseDate = dates.release;
            exercise.startDate = dates.start;
            exercise.dueDate = dates.due;
            return exercise;
        };

        it('accepts no window and a window inside the dates', () => {
            expect(getBuildTimeWindowError(exerciseWithDates(undefined, { due: end }))).toBeUndefined();
            expect(getBuildTimeWindowError(exerciseWithDates({ start, end }, { release: start.subtract(1, 'day'), due: end.add(1, 'day') }))).toBeUndefined();
        });

        it('accepts a window that touches the exercise dates', () => {
            expect(getBuildTimeWindowError(exerciseWithDates({ start, end }, { start, due: end }))).toBeUndefined();
        });

        it('accepts any window when the exercise has no dates', () => {
            expect(getBuildTimeWindowError(exerciseWithDates({ start, end }, {}))).toBeUndefined();
        });

        it('rejects an end that is not after the start', () => {
            expect(getBuildTimeWindowError(exerciseWithDates({ start: end, end: start }, {}))).toBe(BuildTimeWindowError.END_NOT_AFTER_START);
            expect(getBuildTimeWindowError(exerciseWithDates({ start, end: start }, {}))).toBe(BuildTimeWindowError.END_NOT_AFTER_START);
        });

        it('rejects a bound before the start date, falling back to the release date', () => {
            expect(getBuildTimeWindowError(exerciseWithDates({ start, end }, { start: start.add(1, 'minute') }))).toBe(BuildTimeWindowError.OUTSIDE_EXERCISE_DATES);
            expect(getBuildTimeWindowError(exerciseWithDates({ start, end }, { release: start.add(1, 'minute') }))).toBe(BuildTimeWindowError.OUTSIDE_EXERCISE_DATES);
            // the start date wins over the release date
            expect(getBuildTimeWindowError(exerciseWithDates({ start, end }, { release: start.add(1, 'minute'), start: start.subtract(1, 'minute') }))).toBeUndefined();
        });

        it('rejects a bound after the due date, also when only one bound is set', () => {
            expect(getBuildTimeWindowError(exerciseWithDates({ start, end }, { due: end.subtract(1, 'minute') }))).toBe(BuildTimeWindowError.OUTSIDE_EXERCISE_DATES);
            expect(getBuildTimeWindowError(exerciseWithDates({ end }, { due: end.subtract(1, 'minute') }))).toBe(BuildTimeWindowError.OUTSIDE_EXERCISE_DATES);
            expect(getBuildTimeWindowError(exerciseWithDates({ start }, { due: start.subtract(1, 'minute') }))).toBe(BuildTimeWindowError.OUTSIDE_EXERCISE_DATES);
        });
    });

    describe('injectBuildWindowPhase', () => {
        beforeEach(() => {
            vi.useFakeTimers();
        });

        afterEach(() => {
            vi.useRealTimers();
        });

        it('follows the clock across the start and the end of the window', () => {
            vi.setSystemTime(start.subtract(1, 'minute').toDate());
            const window = signal<BuildTimeWindow | undefined>({ start, end });
            const phase = TestBed.runInInjectionContext(() => injectBuildWindowPhase(window));
            TestBed.tick();

            expect(phase()).toBe(BuildWindowPhase.BEFORE);

            vi.advanceTimersByTime(60 * 1000);
            expect(phase()).toBe(BuildWindowPhase.DURING);

            vi.advanceTimersByTime(2 * 60 * 60 * 1000 + 1);
            expect(phase()).toBe(BuildWindowPhase.AFTER);
        });

        it('stays open at the end of the window until the next millisecond', () => {
            vi.setSystemTime(end.subtract(1, 'second').toDate());
            const phase = TestBed.runInInjectionContext(() => injectBuildWindowPhase(signal<BuildTimeWindow | undefined>({ start, end })));
            TestBed.tick();

            vi.advanceTimersByTime(1000);
            expect(phase()).toBe(BuildWindowPhase.DURING);

            vi.advanceTimersByTime(1);
            expect(phase()).toBe(BuildWindowPhase.AFTER);
        });

        it('reacts to a changed window and to no window', () => {
            vi.setSystemTime(start.add(1, 'minute').toDate());
            const window = signal<BuildTimeWindow | undefined>(undefined);
            const phase = TestBed.runInInjectionContext(() => injectBuildWindowPhase(window));
            TestBed.tick();
            expect(phase()).toBe(BuildWindowPhase.NONE);

            window.set({ end: start.add(2, 'minute') });
            TestBed.tick();
            expect(phase()).toBe(BuildWindowPhase.DURING);

            vi.advanceTimersByTime(2 * 60 * 1000);
            expect(phase()).toBe(BuildWindowPhase.AFTER);
        });

        it('does not leave a timer behind when its owner is destroyed', () => {
            vi.setSystemTime(start.subtract(1, 'minute').toDate());
            TestBed.runInInjectionContext(() => injectBuildWindowPhase(signal<BuildTimeWindow | undefined>({ start, end })));
            TestBed.tick();
            expect(vi.getTimerCount()).toBe(1);

            TestBed.resetTestingModule();

            expect(vi.getTimerCount()).toBe(0);
        });
    });
});
