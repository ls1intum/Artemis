import dayjs from 'dayjs/esm';
import { TutorialGroupsConfiguration } from 'app/entities/tutorial-group/tutorial-groups-configuration.model';
import { TutorialGroupsConfigurationFormData } from 'app/course/tutorial-groups/tutorial-groups-management/tutorial-groups-configuration/crud/tutorial-groups-configuration-form/tutorial-groups-configuration-form.component';

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

export const tutorialsGroupsConfigurationToFormData = (entity: TutorialGroupsConfiguration): TutorialGroupsConfigurationFormData => {
    return {
        period: [entity.tutorialPeriodStartInclusive!.toDate(), entity.tutorialPeriodEndInclusive!.toDate()],
        useTutorialGroupChannels: entity.useTutorialGroupChannels,
        usePublicTutorialGroupChannels: entity.usePublicTutorialGroupChannels,
    };
};
