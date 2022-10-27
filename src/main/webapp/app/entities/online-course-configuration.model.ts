import { BaseEntity } from 'app/shared/model/base-entity';
import { Course } from 'app/entities/course.model';

export class OnlineCourseConfiguration implements BaseEntity {
    public id?: number;
    public course?: Course;
    public ltiKey?: string;
    public ltiSecret?: string;
    public userPrefix?: string;
    public originalUrl?: string;
}
