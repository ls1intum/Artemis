import { BaseEntity } from 'app/shared/model/base-entity';
import { Course } from 'app/entities/course.model';
import { User, UserNameAndLoginDTO } from 'app/core/user/user.model';
import { Competency } from 'app/entities/competency.model';

export class LearningPath implements BaseEntity {
    public id?: number;
    public progress?: number;
    public user?: User;
    public course?: Course;
    public competencies?: Competency[];

    constructor() {}
}

export class LearningPathPageableSearchDTO {
    public id?: number;
    public user?: UserNameAndLoginDTO;
    public progress?: number;
}
