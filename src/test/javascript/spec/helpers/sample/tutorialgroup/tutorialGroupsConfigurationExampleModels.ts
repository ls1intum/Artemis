import dayjs from 'dayjs/esm';
import { TutorialGroupsConfiguration } from 'app/tutorialgroup/shared/entities/tutorial-groups-configuration.model';
import { TutorialGroupsConfigurationFormData } from 'app/tutorialgroup/manage/tutorial-groups-configuration/crud/tutorial-groups-configuration-form/tutorial-groups-configuration-form.component';
import { TutorialGroupConfiguration } from 'app/openapi/model/tutorial-group-configuration';

export const generateExampleTutorialGroupsConfiguration = ({
    id = 1,
    tutorialPeriodStartInclusive = dayjs('2021-01-01'),
    tutorialPeriodEndInclusive = dayjs('2021-01-02'),
    useTutorialGroupChannels = true,
    usePublicTutorialGroupChannels = true,
}: TutorialGroupsConfiguration) => {
    const exampleConfiguration = new TutorialGroupsConfiguration();
    exampleConfiguration.id = id;
    exampleConfiguration.tutorialPeriodStartInclusive = tutorialPeriodStartInclusive;
    exampleConfiguration.tutorialPeriodEndInclusive = tutorialPeriodEndInclusive;
    exampleConfiguration.useTutorialGroupChannels = useTutorialGroupChannels;
    exampleConfiguration.usePublicTutorialGroupChannels = usePublicTutorialGroupChannels;
    return exampleConfiguration;
};

export const generateExampleTutorialGroupsConfigurationDTO = ({
    id = 1,
    tutorialPeriodStartInclusive = '2021-01-01',
    tutorialPeriodEndInclusive = '2021-01-02',
    useTutorialGroupChannels = true,
    usePublicTutorialGroupChannels = true,
}: Partial<TutorialGroupConfiguration> = {}): TutorialGroupConfiguration => {
    return { id, tutorialPeriodStartInclusive, tutorialPeriodEndInclusive, useTutorialGroupChannels, usePublicTutorialGroupChannels };
};

export const tutorialsGroupsConfigurationDtoToFormData = (dto: TutorialGroupConfiguration): TutorialGroupsConfigurationFormData => {
    return {
        period: [
            dto.tutorialPeriodStartInclusive ? dayjs(dto.tutorialPeriodStartInclusive).toDate() : undefined,
            dto.tutorialPeriodEndInclusive ? dayjs(dto.tutorialPeriodEndInclusive).toDate() : undefined,
        ].filter((d): d is Date => !!d),
        useTutorialGroupChannels: dto.useTutorialGroupChannels,
        usePublicTutorialGroupChannels: dto.usePublicTutorialGroupChannels,
    };
};
