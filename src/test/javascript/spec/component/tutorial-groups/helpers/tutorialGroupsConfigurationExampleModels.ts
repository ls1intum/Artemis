import dayjs from 'dayjs/esm';
import { TutorialGroupsConfiguration } from 'app/entities/tutorial-group/tutorial-groups-configuration.model';
import { TutorialGroupsConfigurationFormData } from 'app/course/tutorial-groups/tutorial-groups-management/tutorial-groups-configuration/crud/tutorial-groups-configuration-form/tutorial-groups-configuration-form.component';

export const generateExampleTutorialGroupsConfiguration = () => {
    const exampleConfiguration = new TutorialGroupsConfiguration();
    exampleConfiguration.id = 1;
    exampleConfiguration.timeZone = 'Europe/Berlin';
    exampleConfiguration.tutorialPeriodStartInclusive = dayjs('2021-01-01');
    exampleConfiguration.tutorialPeriodEndInclusive = dayjs('2021-01-02');
    return exampleConfiguration;
};

export const tutorialsGroupsConfigurationToFormData = (entity: TutorialGroupsConfiguration): TutorialGroupsConfigurationFormData => {
    return {
        timeZone: entity.timeZone,
        period: [entity.tutorialPeriodStartInclusive!.toDate(), entity.tutorialPeriodEndInclusive!.toDate()],
    };
};
