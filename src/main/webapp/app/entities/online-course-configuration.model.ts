import { BaseEntity } from 'app/shared/model/base-entity';
import { Course } from 'app/entities/course.model';

export class OnlineCourseConfiguration implements BaseEntity {
    public id?: number;
    public course?: Course;
    public ltiKey?: string;
    public ltiSecret?: string;
    public userPrefix?: string;
    public requireExistingUser?: boolean;
    public originalUrl?: string;
    public registrationId?: string;
    public clientId?: string;
    public authorizationUri?: string;
    public jwkSetUri?: string;
    public tokenUri?: string;
}
