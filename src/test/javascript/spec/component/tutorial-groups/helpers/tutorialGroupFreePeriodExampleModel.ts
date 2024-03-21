import dayjs from 'dayjs/esm';
import { TutorialGroupFreePeriod } from 'app/entities/tutorial-group/tutorial-group-free-day.model';
import { TutorialGroupFreePeriodFormData } from 'app/course/tutorial-groups/tutorial-groups-management/tutorial-free-periods/crud/tutorial-free-period-form/tutorial-group-free-period-form.component';
import { TutorialGroupFreePeriodDTO } from 'app/course/tutorial-groups/services/tutorial-group-free-period.service';
import { TutorialGroupFreePeriodsManagementComponent } from 'app/course/tutorial-groups/tutorial-groups-management/tutorial-free-periods/tutorial-free-periods-management/tutorial-group-free-periods-management.component';

export const generateExampleTutorialGroupFreePeriod = ({
    id = 1,
    start = dayjs.utc('2021-01-01T00:00:00'),
    end = dayjs.utc('2021-01-01T23:59:59'),
    reason = 'Example Reason',
}: TutorialGroupFreePeriod) => {
    const examplePeriod = new TutorialGroupFreePeriod();
    examplePeriod.id = id;
    // we get utc from the server --> will be converted to time zone of configuration
    examplePeriod.start = start;
    examplePeriod.end = end;
    examplePeriod.reason = reason;
    return examplePeriod;
};

export const tutorialGroupFreePeriodToTutorialGroupFreePeriodFormData = (entity: TutorialGroupFreePeriod, tz: string): TutorialGroupFreePeriodFormData => {
    if (TutorialGroupFreePeriodsManagementComponent.isFreeDay(entity)) {
        return {
            startDate: entity.start!.tz(tz).toDate(),
            endDate: undefined,
            startTime: undefined,
            endTime: undefined,
            reason: entity.reason,
        };
    } else if (TutorialGroupFreePeriodsManagementComponent.isFreePeriod(entity)) {
        return {
            startDate: entity.start!.tz(tz).toDate(),
            endDate: entity.end!.tz(tz).toDate(),
            startTime: undefined,
            endTime: undefined,
            reason: entity.reason,
        };
    } else {
        return {
            startDate: entity.start!.tz(tz).toDate(),
            endDate: undefined,
            startTime: entity.start!.tz(tz).toDate(),
            endTime: entity.end!.tz(tz).toDate(),
            reason: entity.reason,
        };
    }
};

export const formDataToTutorialGroupFreePeriodDTO = (formData: TutorialGroupFreePeriodFormData): TutorialGroupFreePeriodDTO => {
    if (formData.endDate) {
        return {
            startDate: formData.startDate,
            endDate: formData.endDate,
            reason: formData.reason,
        };
    } else {
        const res = {
            startDate: formData.startDate,
            endDate: formData.startDate,
            reason: formData.reason,
        };
        res.endDate!.setHours(23, 59);
        return res;
    }
};
