import { BaseEntity } from 'app/shared/model/base-entity';
import dayjs from 'dayjs/esm';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { TutorialGroupSchedule } from 'app/entities/tutorial-group/tutorial-group-schedule.model';

export enum TutorialGroupSessionStatus {
    ACTIVE = 'ACTIVE',
    CANCELLED = 'CANCELLED',
}

export class TutorialGroupSession implements BaseEntity {
    public id?: number;
    public tutorialGroupSchedule?: TutorialGroupSchedule;
    public tutorialGroup?: TutorialGroup;
    public start?: dayjs.Dayjs;
    public end?: dayjs.Dayjs;
    public status?: TutorialGroupSessionStatus;
    public statusExplanation?: string;
    public location?: string;
}
