import { BaseEntity } from 'app/shared/model/base-entity';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { TutorialGroupSession } from 'app/entities/tutorial-group/tutorial-group-session.model';
import dayjs from 'dayjs/esm';

export class TutorialGroupSchedule implements BaseEntity {
    public id?: number;
    public tutorialGroup?: TutorialGroup;
    public dayOfWeek?: number;
    public startTime?: string;
    public endTime?: string;
    public repetitionFrequency?: number;
    public validFromInclusive?: dayjs.Dayjs;
    public validToInclusive?: dayjs.Dayjs;
    public tutorialGroupSessions?: TutorialGroupSession[];
}
