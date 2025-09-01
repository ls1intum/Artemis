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

export enum TutorialGroupDetailSessionDTOStatus {
    ACTIVE = 'ACTIVE',
    CANCELLED = 'CANCELLED',
    RESCHEDULED = 'RESCHEDULED',
    RELOCATED = 'RELOCATED',
    RESCHEDULED_AND_RELOCATED = 'RESCHEDULED_AND_RELOCATED',
}

export class TutorialGroupDetailSessionDTO {
    constructor(
        public start: Dayjs,
        public end: Dayjs,
        public location: string,
        public status: TutorialGroupDetailSessionDTOStatus,
        public attendanceCount?: number,
    ) {}
}
