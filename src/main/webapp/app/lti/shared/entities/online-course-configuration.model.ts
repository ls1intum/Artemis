import { BaseEntity } from 'app/shared/model/base-entity';
import { Course } from 'app/core/shared/entities/course.model';
import { LtiPlatformConfiguration } from 'app/core/admin/lti-configuration/lti-configuration.model';

export class OnlineCourseConfiguration implements BaseEntity {
    public id?: number;
    public course?: Course;
    public userPrefix?: string;
    public requireExistingUser?: boolean;
    public ltiPlatformConfiguration?: LtiPlatformConfiguration;
}
