import dayjs from 'dayjs/esm';
import { TutorialGroupFreePeriod } from 'app/tutorialgroup/shared/entities/tutorial-group-free-day.model';
import { TutorialGroupFreePeriodFormData } from 'app/tutorialgroup/manage/tutorial-free-periods/crud/tutorial-free-period-form/tutorial-group-free-period-form.component';
import { TutorialGroupFreePeriodsManagementComponent } from 'app/tutorialgroup/manage/tutorial-free-periods/tutorial-free-periods-management/tutorial-group-free-periods-management.component';
import { TutorialGroupFreePeriodDTO } from 'app/tutorialgroup/shared/entities/tutorial-group-free-period-dto.model';

/**
 * Generates an example {@link TutorialGroupFreePeriod} entity.
 *
 * This helper is primarily used in unit tests to create
 * predictable free period entities.
 *
 * Dates are initialized in UTC to simulate server responses.
 *
 * @param id the free period id
 * @param start the start timestamp (UTC)
 * @param end the end timestamp (UTC)
 * @param reason the optional explanation for the free period
 * @returns a fully initialized TutorialGroupFreePeriod entity
 */
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

/**
 * Generates an example {@link TutorialGroupFreePeriodDTO}.
 *
 * Used in frontend tests where only transport-level
 * DTO objects are required.
 *
 * All date values are ISO 8601 strings.
 *
 * @param overrides optional properties to override defaults
 * @returns a fully initialized TutorialGroupFreePeriodDTO
 */
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

/**
 * Converts a {@link TutorialGroupFreePeriod} entity into
 * form data used by the free period dialog.
 *
 * @param entity the free period entity
 * @param tz the time zone to apply
 * @returns form data suitable for the free period form
 */
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

/**
 * Converts a {@link TutorialGroupFreePeriodDTO} into a
 * {@link TutorialGroupFreePeriod} entity.
 *
 * @param dto the free period DTO
 * @returns the corresponding entity representation
 */
export const tutorialGroupFreePeriodDTOToEntity = (dto: TutorialGroupFreePeriodDTO): TutorialGroupFreePeriod => {
    return {
        id: dto.id,
        start: dayjs(dto.start),
        end: dayjs(dto.end),
        reason: dto.reason,
    } as TutorialGroupFreePeriod;
};

/**
 * Converts form data into a {@link TutorialGroupFreePeriodDTO}
 * for backend submission.
 *
 * If no explicit end date/time is provided,
 * the period defaults to the end of the start day (23:59).
 *
 * @param formData the form input data
 * @param tutorialGroupConfigurationId the associated configuration id
 * @throws Error if required fields are missing
 * @returns a DTO ready for backend submission
 */
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
