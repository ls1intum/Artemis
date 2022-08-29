import { BaseEntity } from 'app/shared/model/base-entity';
import { Course } from 'app/entities/course.model';
import dayjs from 'dayjs/esm';

export class TutorialGroupsConfiguration implements BaseEntity {
    public id?: number;
    public course?: Course;
    public timeZone: string;
    public tutorialPeriodStartInclusive?: dayjs.Dayjs;
    public tutorialPeriodEndInclusive?: dayjs.Dayjs;
}
