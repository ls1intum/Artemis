import { TutorialGroupsConfiguration } from 'app/tutorialgroup/shared/entities/tutorial-groups-configuration.model';
import { fromTutorialGroupFreePeriodDTO } from 'app/tutorialgroup/shared/entities/tutorial-group-free-period-dto.model';
import { convertDateStringFromServer } from 'app/foundation/util/date.utils';
import { TutorialGroupConfiguration } from 'app/openapi/model/tutorial-group-configuration';

export function tutorialGroupsConfigurationEntityFromDto(dto: TutorialGroupConfiguration): TutorialGroupsConfiguration {
    const entity = new TutorialGroupsConfiguration();
    entity.id = dto.id;

    entity.tutorialPeriodStartInclusive = convertDateStringFromServer(dto.tutorialPeriodStartInclusive);
    entity.tutorialPeriodEndInclusive = convertDateStringFromServer(dto.tutorialPeriodEndInclusive);

    entity.useTutorialGroupChannels = dto.useTutorialGroupChannels;
    entity.usePublicTutorialGroupChannels = dto.usePublicTutorialGroupChannels;

    entity.tutorialGroupFreePeriods = (dto.tutorialGroupFreePeriods ?? []).map(fromTutorialGroupFreePeriodDTO);
    return entity;
}
