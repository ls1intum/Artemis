import { describe, expect, it } from 'vitest';
import dayjs from 'dayjs/esm';
import { ProgrammingExercise, resetProgrammingForImport } from 'app/programming/shared/entities/programming-exercise.model';

describe('resetProgrammingForImport', () => {
    it('drops the build time window of the source, as it would not fit the reset dates', () => {
        const exercise = new ProgrammingExercise(undefined, undefined);
        exercise.buildConfig!.advancedSettings = { buildTimeWindow: { start: dayjs('2026-10-01T10:00:00Z'), end: dayjs('2026-10-01T12:00:00Z') } };

        resetProgrammingForImport(exercise);

        expect(exercise.buildConfig!.advancedSettings?.buildTimeWindow).toBeUndefined();
    });

    it('works for an exercise without advanced settings', () => {
        const exercise = new ProgrammingExercise(undefined, undefined);

        expect(() => resetProgrammingForImport(exercise)).not.toThrow();
        expect(exercise.buildConfig!.advancedSettings).toBeUndefined();
    });
});
