import { BaseEntity } from 'app/foundation/model/base-entity';

export class Organization implements BaseEntity {
    id?: number;
    name?: string;
    shortName?: string;
    url?: string;
    description?: string;
    logoUrl?: string;
    emailPattern?: string;
    numberOfUsers?: number;
    numberOfCourses?: number;
}
