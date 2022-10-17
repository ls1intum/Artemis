import dayjs from 'dayjs/esm';
import { TutorialGroupsConfiguration } from 'app/entities/tutorial-group/tutorial-groups-configuration.model';
import { TutorialGroupsConfigurationFormData } from 'app/course/tutorial-groups/tutorial-groups-management/tutorial-groups-configuration/crud/tutorial-groups-configuration-form/tutorial-groups-configuration-form.component';

export const generateExampleTutorialGroupsConfiguration = ({
    id = 1,
    timeZone = 'Europe/Berlin',
    tutorialPeriodStartInclusive = dayjs('2021-01-01'),
    tutorialPeriodEndInclusive = dayjs('2021-01-02'),
}: TutorialGroupsConfiguration) => {
    const exampleConfiguration = new TutorialGroupsConfiguration();
    exampleConfiguration.id = id;
    exampleConfiguration.timeZone = timeZone;
    exampleConfiguration.tutorialPeriodStartInclusive = tutorialPeriodStartInclusive;
    exampleConfiguration.tutorialPeriodEndInclusive = tutorialPeriodEndInclusive;
    return exampleConfiguration;
};

export const tutorialsGroupsConfigurationToFormData = (entity: TutorialGroupsConfiguration): TutorialGroupsConfigurationFormData => {
    return {
        timeZone: entity.timeZone,
        period: [entity.tutorialPeriodStartInclusive!.toDate(), entity.tutorialPeriodEndInclusive!.toDate()],
    };
};
