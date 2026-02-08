import dayjs from 'dayjs/esm';
import { TutorialGroupsConfiguration } from 'app/tutorialgroup/shared/entities/tutorial-groups-configuration.model';

export class TutorialGroupConfigurationDTO {
    public id?: number;
    public tutorialPeriodStartInclusive?: dayjs.Dayjs;
    public tutorialPeriodEndInclusive?: dayjs.Dayjs;
    public useTutorialGroupChannels?: boolean;
    public usePublicTutorialGroupChannels?: boolean;
}

export function tutorialGroupConfigurationDtoFromEntity(tutorialGroupsConfiguration: TutorialGroupsConfiguration): TutorialGroupConfigurationDTO {
    return {
        id: tutorialGroupsConfiguration.id,
        tutorialPeriodStartInclusive: tutorialGroupsConfiguration.tutorialPeriodStartInclusive,
        tutorialPeriodEndInclusive: tutorialGroupsConfiguration.tutorialPeriodEndInclusive,
        useTutorialGroupChannels: tutorialGroupsConfiguration.useTutorialGroupChannels,
        usePublicTutorialGroupChannels: tutorialGroupsConfiguration.usePublicTutorialGroupChannels,
    };
}
