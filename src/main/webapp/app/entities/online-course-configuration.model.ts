import { BaseEntity } from 'app/shared/model/base-entity';
import { Course } from 'app/entities/course.model';

export class OnlineCourseConfiguration implements BaseEntity {
    public id?: number;
    public course?: Course;
    public userPrefix?: string;
    public requireExistingUser?: boolean;
}
