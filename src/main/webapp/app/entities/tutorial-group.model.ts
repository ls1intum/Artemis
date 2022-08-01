import { BaseEntity } from 'app/shared/model/base-entity';
import { Course } from 'app/entities/course.model';
import { User } from 'app/core/user/user.model';

export class TutorialGroup implements BaseEntity {
    public id?: number;
    public title?: string;
    public course?: Course;
    public teachingAssistant?: User;
    public registeredStudents?: User[];

    constructor() {}
}
