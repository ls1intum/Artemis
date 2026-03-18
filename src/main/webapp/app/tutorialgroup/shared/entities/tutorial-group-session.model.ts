import { BaseEntity } from 'app/shared/model/base-entity';
import { Dayjs } from 'dayjs/esm';
import { TutorialGroup } from 'app/tutorialgroup/shared/entities/tutorial-group.model';
import { TutorialGroupSchedule } from 'app/tutorialgroup/shared/entities/tutorial-group-schedule.model';
import { TutorialGroupFreePeriod } from 'app/tutorialgroup/shared/entities/tutorial-group-free-day.model';
import { TutorialGroupFreePeriodDTO, entityToTutorialGroupFreePeriodDTO } from 'app/tutorialgroup/shared/entities/tutorial-group-free-period-dto.model';
import { TutorialGroupScheduleDTO, entityToTutorialGroupScheduleDTO } from 'app/tutorialgroup/shared/entities/tutorial-group-schedule-dto.model';
import { convertDateFromClient } from 'app/shared/util/date.utils';

export enum TutorialGroupSessionStatus {
    ACTIVE = 'ACTIVE',
    CANCELLED = 'CANCELLED',
}

export class TutorialGroupSession implements BaseEntity {
    public id?: number;
    public tutorialGroupSchedule?: TutorialGroupSchedule;
    public tutorialGroup?: TutorialGroup;
    public start?: Dayjs;
    public end?: Dayjs;
    public status?: TutorialGroupSessionStatus;
    public statusExplanation?: string;
    public location?: string;
    public tutorialGroupFreePeriod?: TutorialGroupFreePeriod;
    public attendanceCount?: number;
}

export class TutorialGroupDetailSessionDTO {
    constructor(
        public start: Dayjs,
        public end: Dayjs,
        public location: string,
        public isCancelled: boolean,
        public locationChanged: boolean,
        public timeChanged: boolean,
        public dateChanged: boolean,
        public attendanceCount?: number,
    ) {}
}

export interface RawTutorialGroupDetailSessionDTO {
    start: string;
    end: string;
    location: string;
    isCancelled: boolean;
    locationChanged: boolean;
    timeChanged: boolean;
    dateChanged: boolean;
    attendanceCount?: number;
}

/**
 * Data Transfer Object representing a tutorial group session
 *
 * All date values are serialized as ISO 8601 strings.
 * Nested entities are converted to their respective DTOs.
 *
 */
export interface TutorialGroupSessionDTO {
    id: number;
    start: string;
    end: string;
    status?: TutorialGroupSessionStatus;
    statusExplanation?: string;
    location: string;
    attendanceCount?: number;
    schedule?: TutorialGroupScheduleDTO;
    freePeriod?: TutorialGroupFreePeriodDTO;
}

/**
 * DTO used when creating a new tutorial group session.
 *
 * The date and time are transmitted separately to allow
 * flexible backend reconstruction of the full timestamp.
 */
export interface TutorialGroupSessionRequestDTO {
    date: string;
    startTime: string;
    endTime: string;
    location: string;
}

/**
 * Converts a {@link TutorialGroupSession} entity into a
 * {@link TutorialGroupSessionDTO}.
 *
 * @param entity the session entity to convert
 * @returns the corresponding TutorialGroupSessionDTO
 */
export const entityToTutorialGroupSessionDTO = (entity: TutorialGroupSession): TutorialGroupSessionDTO => {
    return {
        id: entity.id!,
        start: convertDateFromClient(entity.start)!,
        end: convertDateFromClient(entity.end)!,
        status: entity.status,
        statusExplanation: entity.statusExplanation,
        location: entity.location!,
        attendanceCount: entity.attendanceCount,
        schedule: entity.tutorialGroupSchedule ? entityToTutorialGroupScheduleDTO(entity.tutorialGroupSchedule) : undefined,
        freePeriod: entity.tutorialGroupFreePeriod ? entityToTutorialGroupFreePeriodDTO(entity.tutorialGroupFreePeriod) : undefined,
    };
};
