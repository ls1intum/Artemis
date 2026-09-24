import { describe, expect, it, vi } from 'vitest';
import dayjs from 'dayjs/esm';
import * as dateUtils from 'app/foundation/util/date.utils';

import { tutorialGroupsConfigurationEntityFromDto } from 'app/tutorialgroup/shared/entities/tutorial-groups-configuration-dto.model';
import { TutorialGroupConfiguration } from 'app/openapi/model/tutorial-group-configuration';

describe('TutorialGroupConfigurationDTO mapping', () => {
    afterEach(() => {
        vi.restoreAllMocks();
    });

    describe('tutorialGroupsConfigurationEntityFromDto', () => {
        it('shouldReturnEntityWithConvertedDatesWhenDtoHasDateStrings', () => {
            const convertSpy = vi.spyOn(dateUtils, 'convertDateStringFromServer').mockReturnValue(dayjs.utc('2024-01-01'));

            const dto: TutorialGroupConfiguration = {
                id: 8,
                tutorialPeriodStartInclusive: '2024-01-01',
                tutorialPeriodEndInclusive: '2024-02-01',
                useTutorialGroupChannels: true,
                usePublicTutorialGroupChannels: false,
                tutorialGroupFreePeriods: [],
            };

            const entity = tutorialGroupsConfigurationEntityFromDto(dto);

            expect(entity.id).toBe(8);
            expect(entity.tutorialPeriodStartInclusive?.isSame(dayjs.utc('2024-01-01'))).toBe(true);
            expect(entity.tutorialPeriodEndInclusive?.isSame(dayjs.utc('2024-01-01'))).toBe(true);
            expect(entity.useTutorialGroupChannels).toBe(true);
            expect(entity.usePublicTutorialGroupChannels).toBe(false);
            expect(entity.tutorialGroupFreePeriods).toHaveLength(0);
            expect(convertSpy).toHaveBeenCalledTimes(2);
        });

        it('shouldReturnEmptyFreePeriodsWhenDtoFreePeriodsUndefined', () => {
            const dto: TutorialGroupConfiguration = {
                id: 9,
                tutorialPeriodStartInclusive: '2024-01-01',
                tutorialPeriodEndInclusive: '2024-02-01',
                useTutorialGroupChannels: false,
                usePublicTutorialGroupChannels: false,
            };

            const entity = tutorialGroupsConfigurationEntityFromDto(dto);

            expect(entity.tutorialGroupFreePeriods).toHaveLength(0);
        });
    });
});
