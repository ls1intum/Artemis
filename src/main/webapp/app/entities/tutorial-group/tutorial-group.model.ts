import { BaseEntity } from 'app/shared/model/base-entity';
import { Course, Language } from 'app/entities/course.model';
import { User } from 'app/core/user/user.model';
export class TutorialGroup implements BaseEntity {
    public id?: number;
    public title?: string;
    public course?: Course;
    public capacity?: number;
    public location?: string;
    public language?: Language;
    public additionalInformation?: string;
    public isOnline?: boolean;
    public teachingAssistant?: User;
    public registeredStudents?: User[];

    constructor() {}
}
