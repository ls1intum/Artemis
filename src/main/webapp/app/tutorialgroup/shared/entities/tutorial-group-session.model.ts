import { BaseEntity } from 'app/shared/model/base-entity';
import dayjs, { Dayjs } from 'dayjs/esm';
import { TutorialGroup } from 'app/tutorialgroup/shared/entities/tutorial-group.model';
import { TutorialGroupSchedule } from 'app/tutorialgroup/shared/entities/tutorial-group-schedule.model';
import { TutorialGroupFreePeriod } from 'app/tutorialgroup/shared/entities/tutorial-group-free-day.model';

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

export class TutorialGroupSessionDTO {
    public id: number;
    public start: Dayjs;
    public end: Dayjs;
    public location: string;
    public isCancelled: boolean;
    public locationChanged: boolean;
    public timeChanged: boolean;
    public dateChanged: boolean;
    public attendance?: number;

    constructor(rawTutorialGroupSession: RawTutorialGroupSessionDTO) {
        this.id = rawTutorialGroupSession.id;
        this.start = dayjs(rawTutorialGroupSession.start);
        this.end = dayjs(rawTutorialGroupSession.end);
        this.location = rawTutorialGroupSession.location;
        this.isCancelled = rawTutorialGroupSession.isCancelled;
        this.locationChanged = rawTutorialGroupSession.locationChanged;
        this.timeChanged = rawTutorialGroupSession.timeChanged;
        this.dateChanged = rawTutorialGroupSession.dateChanged;
        this.attendance = rawTutorialGroupSession.attendanceCount;
    }
}

export interface RawTutorialGroupSessionDTO {
    id: number;
    start: string;
    end: string;
    location: string;
    isCancelled: boolean;
    locationChanged: boolean;
    timeChanged: boolean;
    dateChanged: boolean;
    attendanceCount?: number;
}

export interface CreateOrUpdateTutorialGroupSessionDTO {
    date: string;
    startTime: string;
    endTime: string;
    location: string;
    attendance?: number;
}
