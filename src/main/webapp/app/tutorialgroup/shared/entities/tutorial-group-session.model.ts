import { BaseEntity } from 'app/shared/model/base-entity';
import { Dayjs } from 'dayjs/esm';
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
