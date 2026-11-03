import { describe, expect, it, vi } from 'vitest';
import dayjs from 'dayjs/esm';

import { fromTutorialGroupFreePeriodDTO } from 'app/tutorialgroup/shared/entities/tutorial-group-free-period-dto.model';
import { TutorialGroupConfigurationFreePeriod } from 'app/openapi/model/tutorial-group-configuration-free-period';

describe('TutorialGroupFreePeriodDTO', () => {
    afterEach(() => {
        vi.restoreAllMocks();
    });

    describe('fromTutorialGroupFreePeriodDTO', () => {
        it('shouldReturnEntityWithUtcDatesWhenDtoContainsDateStrings', () => {
            const dto: TutorialGroupConfigurationFreePeriod = {
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
    });
});
