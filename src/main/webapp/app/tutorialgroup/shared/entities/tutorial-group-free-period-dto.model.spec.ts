import { describe, expect, it, vi } from 'vitest';
import dayjs from 'dayjs/esm';
import * as dateUtils from 'app/shared/util/date.utils';

import {
    TutorialGroupFreePeriodDTO,
    entityToTutorialGroupFreePeriodDTO,
    fromTutorialGroupFreePeriodDTO,
} from 'app/tutorialgroup/shared/entities/tutorial-group-free-period-dto.model';
import { TutorialGroupsConfiguration } from 'app/tutorialgroup/shared/entities/tutorial-groups-configuration.model';

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
                tutorialGroupsConfiguration: { id: 1 } as TutorialGroupsConfiguration,
            };

            const dto = entityToTutorialGroupFreePeriodDTO(entity)!;

            expect(dto.id).toBe(1);
            expect(dto.start).toBe('2024-01-10T10:00:00Z');
            expect(dto.end).toBe('2024-01-10T10:00:00Z');
            expect(dto.reason).toBe('Holiday');
            expect(dto.tutorialGroupConfigurationId).toBe(1);
            expect(convertSpy).toHaveBeenCalledTimes(2);
        });
    });

    describe('fromTutorialGroupFreePeriodDTO', () => {
        it('shouldReturnEntityWithUtcDatesWhenDtoContainsDateStrings', () => {
            const dto: TutorialGroupFreePeriodDTO = {
                id: 3,
                start: '2024-01-10T10:00:00Z',
                end: '2024-01-10T12:00:00Z',
                reason: 'Exam',
                tutorialGroupConfigurationId: 1,
            };

            const entity = fromTutorialGroupFreePeriodDTO(dto, { id: 1 });

            expect(entity.id).toBe(3);
            expect(entity.start?.isSame(dayjs.utc('2024-01-10T10:00:00Z'))).toBe(true);
            expect(entity.end?.isSame(dayjs.utc('2024-01-10T12:00:00Z'))).toBe(true);
            expect(entity.reason).toBe('Exam');
        });
    });
});
