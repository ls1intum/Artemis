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
    // we get utc from the server --> will be converted to the time zone of configuration
    examplePeriod.start = start;
    examplePeriod.end = end;
    examplePeriod.reason = reason;
    return examplePeriod;
};

export const generateExampleTutorialGroupFreePeriodDTO = ({
    id = 1,
    start = '2021-01-01T00:00:00',
    end = '2021-01-01T23:59:59',
    reason = 'Example Reason',
    tutorialGroupConfigurationId = 1,
}: Partial<TutorialGroupFreePeriodDTO> = {}): TutorialGroupFreePeriodDTO => {
    return {
        id,
        start,
        end,
        reason,
        tutorialGroupConfigurationId,
    };
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

export const tutorialGroupFreePeriodDTOToEntity = (dto: TutorialGroupFreePeriodDTO): TutorialGroupFreePeriod => {
    return {
        id: dto.id,
        start: dayjs(dto.start),
        end: dayjs(dto.end),
        reason: dto.reason,
    } as TutorialGroupFreePeriod;
};

export const formDataToTutorialGroupFreePeriodDTO = (formData: TutorialGroupFreePeriodFormData, tutorialGroupConfigurationId: number): TutorialGroupFreePeriodDTO => {
    if (!formData.startDate) {
        throw new Error('Start date is required');
    }
    const start = new Date(formData.startDate);
    start.setHours(formData.startTime?.getHours() ?? 0, formData.startTime?.getMinutes() ?? 0, 0, 0);

    let end: Date;

    if (formData.endDate && formData.endTime) {
        end = new Date(formData.endDate);
        end.setHours(formData.endTime.getHours(), formData.endTime.getMinutes(), 0, 0);
    } else {
        end = new Date(formData.startDate);
        end.setHours(23, 59, 0, 0);
    }

    return {
        start: start.toISOString(),
        end: end.toISOString(),
        reason: formData.reason,
        tutorialGroupConfigurationId,
    };
};
