import { describe, expect, it } from 'vitest';
import dayjs from 'dayjs/esm';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { toBuildConfigAdvancedSettingsDTO, toUpdateProgrammingExerciseDTO } from 'app/programming/manage/services/update-programming-exercise-dto.model';

describe('update programming exercise DTO', () => {
    const start = dayjs('2026-10-01T10:00:00Z');
    const end = dayjs('2026-10-01T12:00:00Z');

    describe('toBuildConfigAdvancedSettingsDTO', () => {
        it('converts both bounds to ISO strings', () => {
            expect(toBuildConfigAdvancedSettingsDTO({ buildTimeWindow: { start, end } })).toEqual({
                buildTimeWindow: { start: '2026-10-01T10:00:00.000Z', end: '2026-10-01T12:00:00.000Z' },
            });
        });

        it('keeps a window with only one bound', () => {
            expect(toBuildConfigAdvancedSettingsDTO({ buildTimeWindow: { start } })).toEqual({ buildTimeWindow: { start: '2026-10-01T10:00:00.000Z', end: undefined } });
            expect(toBuildConfigAdvancedSettingsDTO({ buildTimeWindow: { end } })).toEqual({ buildTimeWindow: { start: undefined, end: '2026-10-01T12:00:00.000Z' } });
        });

        it('omits the settings when no bound is set, so the server stores no window', () => {
            expect(toBuildConfigAdvancedSettingsDTO(undefined)).toBeUndefined();
            expect(toBuildConfigAdvancedSettingsDTO({})).toBeUndefined();
            expect(toBuildConfigAdvancedSettingsDTO({ buildTimeWindow: {} })).toBeUndefined();
            expect(toBuildConfigAdvancedSettingsDTO({ buildTimeWindow: { start: dayjs('not a date') } })).toBeUndefined();
        });
    });

    describe('toUpdateProgrammingExerciseDTO', () => {
        const exerciseWith = (buildTimeWindow?: { start?: dayjs.Dayjs; end?: dayjs.Dayjs }) => {
            const exercise = new ProgrammingExercise(undefined, undefined);
            exercise.id = 1;
            exercise.buildConfig!.allowBranching = true;
            exercise.buildConfig!.branchRegex = 'feature/.*';
            exercise.buildConfig!.advancedSettings = buildTimeWindow ? { buildTimeWindow } : undefined;
            return exercise;
        };

        it('sends the build time window nested in the build config', () => {
            const dto = toUpdateProgrammingExerciseDTO(exerciseWith({ start, end }));

            expect(dto.buildConfig?.advancedSettings).toEqual({ buildTimeWindow: { start: '2026-10-01T10:00:00.000Z', end: '2026-10-01T12:00:00.000Z' } });
        });

        it('keeps the branching settings flat and omits the settings without a window', () => {
            const dto = toUpdateProgrammingExerciseDTO(exerciseWith());

            expect(dto.buildConfig?.allowBranching).toBe(true);
            expect(dto.buildConfig?.branchRegex).toBe('feature/.*');
            expect(dto.buildConfig?.advancedSettings).toBeUndefined();
            expect(JSON.parse(JSON.stringify(dto)).buildConfig).not.toHaveProperty('advancedSettings');
        });
    });
});
