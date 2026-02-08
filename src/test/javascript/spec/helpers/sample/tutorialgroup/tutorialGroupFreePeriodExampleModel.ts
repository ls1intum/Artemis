import dayjs from 'dayjs/esm';
import { TutorialGroupFreePeriod } from 'app/tutorialgroup/shared/entities/tutorial-group-free-day.model';
import { TutorialGroupFreePeriodFormData } from 'app/tutorialgroup/manage/tutorial-free-periods/crud/tutorial-free-period-form/tutorial-group-free-period-form.component';
import { TutorialGroupFreePeriodsManagementComponent } from 'app/tutorialgroup/manage/tutorial-free-periods/tutorial-free-periods-management/tutorial-group-free-periods-management.component';
import { TutorialGroupFreePeriodDTO } from 'app/tutorialgroup/shared/entities/tutorial-group-free-period-dto.model';

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
    const start = dayjs(formData.startDate);

    if (formData.endDate) {
        const end = dayjs(formData.endDate);
        return {
            start,
            end,
            reason: formData.reason,
        };
    }
    const end = dayjs(formData.startDate).hour(23).minute(59);
    return {
        start,
        end,
        reason: formData.reason,
    };
};
