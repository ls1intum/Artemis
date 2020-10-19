import { BaseEntity } from 'app/shared/model/base-entity';
import { Course } from 'app/entities/course.model';
import { Exercise } from 'app/entities/exercise.model';
import { LectureUnit } from 'app/entities/lecture-unit/lectureUnit.model';

export class LearningGoal implements BaseEntity {
    public id?: number;
    public title?: String;
    public description?: String;
    public course?: Course;
    public exercises?: Exercise[];
    public lectureModules?: LectureUnit[];

    constructor() {}
}
