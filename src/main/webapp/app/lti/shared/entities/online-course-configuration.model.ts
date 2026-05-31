import { BaseEntity } from 'app/foundation/model/base-entity';
import { Course } from 'app/course/shared/entities/course.model';
import { LtiPlatformConfiguration } from 'app/lti/shared/entities/lti-configuration.model';
export class OnlineCourseConfiguration implements BaseEntity {
    public id?: number;
    public course?: Course;
    public userPrefix?: string;
    public requireExistingUser?: boolean;
    public ltiPlatformConfiguration?: LtiPlatformConfiguration;
}
