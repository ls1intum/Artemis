import { BaseEntity } from 'app/shared/model/base-entity';
import { Course } from 'app/entities/course.model';
import { User } from 'app/core/user/user.model';
import { Competency } from 'app/entities/competency.model';

export class LearningPath implements BaseEntity {
    public id?: number;
    public masteredCompetencies?: number;
    public user?: User;
    public course?: Course;
    public competencies?: Competency[];

    constructor() {}
}
