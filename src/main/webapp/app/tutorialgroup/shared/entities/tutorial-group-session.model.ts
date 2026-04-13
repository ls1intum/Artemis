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

export class TutorialGroupDetailSessionDTO {
    start: Dayjs;
    end: Dayjs;
    location: string;
    isCancelled: boolean;
    locationChanged: boolean;
    timeChanged: boolean;
    dateChanged: boolean;
    attendanceCount?: number;

    constructor(rawDto: RawTutorialGroupDetailSessionDTO) {
        this.start = dayjs(rawDto.start);
        this.end = dayjs(rawDto.end);
        this.location = rawDto.location;
        this.isCancelled = rawDto.isCancelled;
        this.locationChanged = rawDto.locationChanged;
        this.timeChanged = rawDto.timeChanged;
        this.dateChanged = rawDto.dateChanged;
        this.attendanceCount = rawDto.attendanceCount;
    }
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

export class TutorialGroupSessionDTO {
    constructor(
        public id: number,
        public start: Dayjs,
        public end: Dayjs,
        public location: string,
        public isCancelled: boolean,
        public locationChanged: boolean,
        public timeChanged: boolean,
        public dateChanged: boolean,
        public attendance?: number,
    ) {}
}

export interface CreateOrUpdateTutorialGroupSessionDTO {
    date: string;
    startTime: string;
    endTime: string;
    location: string;
    attendance?: number;
}
