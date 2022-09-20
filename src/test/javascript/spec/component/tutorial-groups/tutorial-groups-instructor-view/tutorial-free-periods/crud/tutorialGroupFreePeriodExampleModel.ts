import dayjs from 'dayjs/esm';
import { TutorialGroupFreePeriod } from 'app/entities/tutorial-group/tutorial-group-free-day.model';
import { TutorialGroupFreePeriodFormData } from 'app/course/tutorial-groups/tutorial-groups-instructor-view/tutorial-free-periods/crud/tutorial-free-period-form/tutorial-group-free-period-form.component';
import { TutorialGroupFreePeriodDTO } from 'app/course/tutorial-groups/services/tutorial-group-free-period.service';

export const generateExampleTutorialGroupFreePeriod = () => {
    const examplePeriod = new TutorialGroupFreePeriod();
    examplePeriod.id = 1;
    // we get utc from the server --> will be converted to time zone of configuration
    examplePeriod.start = dayjs.utc('2021-01-01T00:00:00');
    examplePeriod.end = dayjs.utc('2021-01-01T23:59:59');
    examplePeriod.reason = 'Holiday';
    return examplePeriod;
};

export const tutorialGroupFreePeriodToTutorialGroupFreePeriodFormData = (entity: TutorialGroupFreePeriod, tz: string): TutorialGroupFreePeriodFormData => {
    return {
        date: entity.start!.tz(tz).toDate(),
        reason: entity.reason,
    };
};

export const formDataToTutorialGroupFreePeriodDTO = (formData: TutorialGroupFreePeriodFormData): TutorialGroupFreePeriodDTO => {
    return {
        date: formData.date,
        reason: formData.reason,
    };
};
