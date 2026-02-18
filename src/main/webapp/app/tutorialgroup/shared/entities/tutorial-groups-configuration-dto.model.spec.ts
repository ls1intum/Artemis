import { describe, expect, it, vi } from 'vitest';
import dayjs from 'dayjs/esm';
import * as dateUtils from 'app/shared/util/date.utils';

import {
    TutorialGroupConfigurationDTO,
    tutorialGroupConfigurationDtoFromEntity,
    tutorialGroupsConfigurationEntityFromDto,
} from 'app/tutorialgroup/shared/entities/tutorial-groups-configuration-dto.model';

import { TutorialGroupsConfiguration } from 'app/tutorialgroup/shared/entities/tutorial-groups-configuration.model';

describe('TutorialGroupConfigurationDTO mapping', () => {
    afterEach(() => {
        vi.restoreAllMocks();
    });

    describe('tutorialGroupConfigurationDtoFromEntity', () => {
        it('shouldReturnDtoWithConvertedDatesWhenEntityHasDates', () => {
            const convertSpy = vi.spyOn(dateUtils, 'convertDateFromClient').mockReturnValue('2024-01-01');

            const entity = new TutorialGroupsConfiguration();
            entity.id = 5;
            entity.tutorialPeriodStartInclusive = dayjs.utc('2024-01-01');
            entity.tutorialPeriodEndInclusive = dayjs.utc('2024-02-01');
            entity.useTutorialGroupChannels = true;
            entity.usePublicTutorialGroupChannels = false;
            entity.tutorialGroupFreePeriods = [];

            const dto = tutorialGroupConfigurationDtoFromEntity(entity);

            expect(dto.id).toBe(5);
            expect(dto.tutorialPeriodStartInclusive).toBe('2024-01-01');
            expect(dto.tutorialPeriodEndInclusive).toBe('2024-01-01'); // mocked
            expect(dto.useTutorialGroupChannels).toBeTrue();
            expect(dto.usePublicTutorialGroupChannels).toBeFalse();
            expect(dto.tutorialGroupFreePeriods).toHaveLength(0);
            expect(convertSpy).toHaveBeenCalledTimes(2);
        });

        it('shouldReturnEmptyFreePeriodsWhenEntityFreePeriodsUndefined', () => {
            const entity = new TutorialGroupsConfiguration();
            entity.tutorialGroupFreePeriods = undefined;

            const dto = tutorialGroupConfigurationDtoFromEntity(entity);

            expect(dto.tutorialGroupFreePeriods).toHaveLength(0);
        });

        it('shouldMapNestedFreePeriodsWhenEntityContainsFreePeriods', () => {
            const freePeriod = {
                id: 1,
                start: dayjs.utc('2024-01-10T10:00:00Z'),
                end: dayjs.utc('2024-01-10T12:00:00Z'),
                reason: 'Holiday',
            };

            const entity = new TutorialGroupsConfiguration();
            entity.tutorialGroupFreePeriods = [freePeriod as any];

            const dto = tutorialGroupConfigurationDtoFromEntity(entity);

            expect(dto.tutorialGroupFreePeriods).toHaveLength(1);
            expect(dto.tutorialGroupFreePeriods![0].reason).toBe('Holiday');
        });
    });

    describe('tutorialGroupsConfigurationEntityFromDto', () => {
        it('shouldReturnEntityWithConvertedDatesWhenDtoHasDateStrings', () => {
            const convertSpy = vi.spyOn(dateUtils, 'convertDateStringFromServer').mockReturnValue(dayjs.utc('2024-01-01'));

            const dto: TutorialGroupConfigurationDTO = {
                id: 8,
                tutorialPeriodStartInclusive: '2024-01-01',
                tutorialPeriodEndInclusive: '2024-02-01',
                useTutorialGroupChannels: true,
                usePublicTutorialGroupChannels: false,
                tutorialGroupFreePeriods: [],
            };

            const entity = tutorialGroupsConfigurationEntityFromDto(dto);

            expect(entity.id).toBe(8);
            expect(entity.tutorialPeriodStartInclusive?.isSame(dayjs.utc('2024-01-01'))).toBeTrue();
            expect(entity.tutorialPeriodEndInclusive?.isSame(dayjs.utc('2024-01-01'))).toBeTrue();
            expect(entity.useTutorialGroupChannels).toBeTrue();
            expect(entity.usePublicTutorialGroupChannels).toBeFalse();
            expect(entity.tutorialGroupFreePeriods).toHaveLength(0);
            expect(convertSpy).toHaveBeenCalledTimes(2);
        });

        it('shouldReturnEmptyFreePeriodsWhenDtoFreePeriodsUndefined', () => {
            const dto: TutorialGroupConfigurationDTO = {
                id: 9,
            };

            const entity = tutorialGroupsConfigurationEntityFromDto(dto);

            expect(entity.tutorialGroupFreePeriods).toHaveLength(0);
        });
    });
});
