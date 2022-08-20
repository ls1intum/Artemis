import { BaseEntity } from 'app/shared/model/base-entity';
import { Course } from 'app/entities/course.model';
import { Exercise } from 'app/entities/exercise.model';
import { LectureUnit } from 'app/entities/lecture-unit/lectureUnit.model';

/**
 * The available learning goal types (based on Bloom's Taxonomy)
 */
export enum LearningGoalTaxonomy {
    REMEMBER = 'remember',
    UNDERSTAND = 'understand',
    APPLY = 'apply',
    ANALYZE = 'analyze',
    EVALUATE = 'evaluate',
    CREATE = 'create',
}

export class LearningGoal implements BaseEntity {
    public id?: number;
    public title?: string;
    public description?: string;
    public taxonomy?: LearningGoalTaxonomy;
    public course?: Course;
    public exercises?: Exercise[];
    public lectureUnits?: LectureUnit[];

    constructor() {}
}

export class LearningGoalRelation implements BaseEntity {
    public id?: number;
    public tailLearningGoal?: LearningGoal;
    public headLearningGoal?: LearningGoal;
    public type?: string;

    constructor() {}
}
