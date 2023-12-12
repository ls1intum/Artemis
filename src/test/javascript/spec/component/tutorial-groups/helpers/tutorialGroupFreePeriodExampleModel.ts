import dayjs from 'dayjs/esm';
import { TutorialGroupFreeDay } from 'app/entities/tutorial-group/tutorial-group-free-day.model';
import { TutorialGroupFreePeriodFormData } from 'app/course/tutorial-groups/tutorial-groups-management/tutorial-free-periods/crud/tutorial-free-period-form/tutorial-group-free-period-form.component';
import { TutorialGroupFreeDayDTO } from 'app/course/tutorial-groups/services/tutorial-group-free-day.service';

export const generateExampleTutorialGroupFreePeriod = ({
    id = 1,
    start = dayjs.utc('2021-01-01T00:00:00'),
    end = dayjs.utc('2021-01-01T23:59:59'),
    reason = 'Example Reason',
}: TutorialGroupFreeDay) => {
    const examplePeriod = new TutorialGroupFreeDay();
    examplePeriod.id = id;
    // we get utc from the server --> will be converted to time zone of configuration
    examplePeriod.start = start;
    examplePeriod.end = end;
    examplePeriod.reason = reason;
    return examplePeriod;
};

export const tutorialGroupFreePeriodToTutorialGroupFreePeriodFormData = (entity: TutorialGroupFreeDay, tz: string): TutorialGroupFreePeriodFormData => {
    return {
        date: entity.start!.tz(tz).toDate(),
        reason: entity.reason,
    };
};

export const formDataToTutorialGroupFreePeriodDTO = (formData: TutorialGroupFreePeriodFormData): TutorialGroupFreeDayDTO => {
    return {
        date: formData.date,
        reason: formData.reason,
    };
};
