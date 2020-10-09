import { BaseEntity } from 'app/shared/model/base-entity';
import { Course } from 'app/entities/course.model';
import { Exercise } from 'app/entities/exercise.model';
import { Lecture } from 'app/entities/lecture.model';

export class LearningGoal implements BaseEntity {
    id?: number;
    title?: string;
    description?: string;
    course?: Course;
    exercises?: Exercise[];
    lectures?: Lecture[];

    constructor() {}
}
