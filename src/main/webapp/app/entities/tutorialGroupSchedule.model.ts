import { BaseEntity } from 'app/shared/model/base-entity';
import dayjs from 'dayjs/esm';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { TutorialGroupSession } from 'app/entities/TutorialGroupSession.model';

export class TutorialGroupSchedule implements BaseEntity {
    public id?: number;
    public tutorialGroup?: TutorialGroup;
    public dayOfWeek?: number;
    public startTime?: string;
    public endTime?: string;
    public timeZone: string;
    public repetitionFrequency?: number;
    public validFromInclusive?: dayjs.Dayjs;
    public validToInclusive?: dayjs.Dayjs;
    public tutorialGroupSessions?: TutorialGroupSession[];
}
