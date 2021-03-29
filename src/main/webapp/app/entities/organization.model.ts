import { BaseEntity } from 'app/shared/model/base-entity';
import { User } from 'app/core/user/user.model';
import { Course } from 'app/entities/course.model';

export class Organization implements BaseEntity {
    id?: number;
    name?: string;
    shortName?: string;
    url?: string;
    description?: string;
    logoUrl?: string;
    emailPattern?: string;
    users?: User[];
    courses?: Course[];
    numberOfUsers?: number;
    numberOfCourses?: number;
}
