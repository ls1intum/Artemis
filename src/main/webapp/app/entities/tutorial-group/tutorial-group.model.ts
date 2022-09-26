import { BaseEntity } from 'app/shared/model/base-entity';
import { Course, Language } from 'app/entities/course.model';
import { User } from 'app/core/user/user.model';
import { TutorialGroupRegistration } from 'app/entities/tutorial-group/tutorial-group-registration.model';
export class TutorialGroup implements BaseEntity {
    public id?: number;
    public title?: string;
    public course?: Course;
    public capacity?: number;
    public campus?: string;
    public language?: Language;
    public additionalInformation?: string;
    public isOnline?: boolean;
    public teachingAssistant?: User;
    public registrations?: TutorialGroupRegistration[];

    constructor() {}
}
