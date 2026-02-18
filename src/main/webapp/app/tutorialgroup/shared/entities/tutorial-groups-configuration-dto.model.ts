import { TutorialGroupsConfiguration } from 'app/tutorialgroup/shared/entities/tutorial-groups-configuration.model';
import { TutorialGroupFreePeriodDTO, fromTutorialGroupFreePeriodDTO, toTutorialGroupFreePeriodDTO } from 'app/tutorialgroup/shared/entities/tutorial-group-free-period-dto.model';
import { convertDateFromClient, convertDateStringFromServer } from 'app/shared/util/date.utils';

export interface TutorialGroupConfigurationDTO {
    id?: number;
    tutorialPeriodStartInclusive?: string;
    tutorialPeriodEndInclusive?: string;
    useTutorialGroupChannels?: boolean;
    usePublicTutorialGroupChannels?: boolean;
    tutorialGroupFreePeriods?: TutorialGroupFreePeriodDTO[];
}

export function tutorialGroupConfigurationDtoFromEntity(entity: TutorialGroupsConfiguration): TutorialGroupConfigurationDTO {
    return {
        id: entity.id,
        tutorialPeriodStartInclusive: convertDateFromClient(entity.tutorialPeriodStartInclusive),
        tutorialPeriodEndInclusive: convertDateFromClient(entity.tutorialPeriodEndInclusive),
        useTutorialGroupChannels: entity.useTutorialGroupChannels,
        usePublicTutorialGroupChannels: entity.usePublicTutorialGroupChannels,
        tutorialGroupFreePeriods: (entity.tutorialGroupFreePeriods ?? []).map(toTutorialGroupFreePeriodDTO),
    };
}

export function tutorialGroupsConfigurationEntityFromDto(dto: TutorialGroupConfigurationDTO): TutorialGroupsConfiguration {
    const entity = new TutorialGroupsConfiguration();
    entity.id = dto.id;

    entity.tutorialPeriodStartInclusive = convertDateStringFromServer(dto.tutorialPeriodStartInclusive);
    entity.tutorialPeriodEndInclusive = convertDateStringFromServer(dto.tutorialPeriodEndInclusive);

    entity.useTutorialGroupChannels = dto.useTutorialGroupChannels;
    entity.usePublicTutorialGroupChannels = dto.usePublicTutorialGroupChannels;

    entity.tutorialGroupFreePeriods = (dto.tutorialGroupFreePeriods ?? []).map(fromTutorialGroupFreePeriodDTO);
    return entity;
}
