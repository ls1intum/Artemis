import { BaseEntity } from 'app/shared/model/base-entity';
import { Course } from 'app/core/course/shared/entities/course.model';
import { LtiPlatformConfiguration } from 'app/lti/shared/entities/lti-configuration.model';
export class OnlineCourseConfiguration implements BaseEntity {
    public id?: number;
    public course?: Course;
    public userPrefix?: string;
    public requireExistingUser?: boolean;
    public ltiPlatformConfiguration?: LtiPlatformConfiguration;
}
