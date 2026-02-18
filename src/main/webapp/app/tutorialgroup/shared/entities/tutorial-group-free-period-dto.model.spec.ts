import { describe, expect, it, vi } from 'vitest';
import dayjs from 'dayjs/esm';
import * as dateUtils from 'app/shared/util/date.utils';

import { TutorialGroupFreePeriodDTO, fromTutorialGroupFreePeriodDTO, toTutorialGroupFreePeriodDTO } from 'app/tutorialgroup/shared/entities/tutorial-group-free-period-dto.model';

describe('TutorialGroupFreePeriodDTO', () => {
    afterEach(() => {
        vi.restoreAllMocks();
    });

    describe('toTutorialGroupFreePeriodDTO', () => {
        it('shouldReturnDtoWithConvertedDatesWhenEntityHasDates', () => {
            const convertSpy = vi.spyOn(dateUtils, 'convertDateFromClient').mockReturnValue('2024-01-10T10:00:00Z');

            const entity = {
                id: 1,
                start: dayjs.utc('2024-01-10T10:00:00Z'),
                end: dayjs.utc('2024-01-10T12:00:00Z'),
                reason: 'Holiday',
            };

            const dto = toTutorialGroupFreePeriodDTO(entity as any);

            expect(dto.id).toBe(1);
            expect(dto.start).toBe('2024-01-10T10:00:00Z');
            expect(dto.end).toBe('2024-01-10T10:00:00Z'); // mocked
            expect(dto.reason).toBe('Holiday');
            expect(convertSpy).toHaveBeenCalledTimes(2);
        });

        it('shouldReturnUndefinedDatesWhenEntityDatesAreUndefined', () => {
            const entity = {
                id: 2,
                reason: 'No dates',
            };

            const dto = toTutorialGroupFreePeriodDTO(entity as any);

            expect(dto.start).toBeUndefined();
            expect(dto.end).toBeUndefined();
        });
    });

    describe('fromTutorialGroupFreePeriodDTO', () => {
        it('shouldReturnEntityWithUtcDatesWhenDtoContainsDateStrings', () => {
            const dto: TutorialGroupFreePeriodDTO = {
                id: 3,
                start: '2024-01-10T10:00:00Z',
                end: '2024-01-10T12:00:00Z',
                reason: 'Exam',
            };

            const entity = fromTutorialGroupFreePeriodDTO(dto);

            expect(entity.id).toBe(3);
            expect(entity.start?.isSame(dayjs.utc('2024-01-10T10:00:00Z'))).toBe(true);
            expect(entity.end?.isSame(dayjs.utc('2024-01-10T12:00:00Z'))).toBe(true);
            expect(entity.reason).toBe('Exam');
        });

        it('shouldReturnUndefinedDatesWhenDtoDatesAreMissing', () => {
            const dto: TutorialGroupFreePeriodDTO = {
                id: 4,
                reason: 'Missing',
            };

            const entity = fromTutorialGroupFreePeriodDTO(dto);

            expect(entity.start).toBeUndefined();
            expect(entity.end).toBeUndefined();
        });
    });
});
