import { BaseEntity } from 'app/shared/model/base-entity';
import { Course } from 'app/entities/course.model';
import { Exercise } from 'app/entities/exercise.model';
import { LectureUnit } from 'app/entities/lecture-unit/lectureUnit.model';

/**
 * The available learning goal types (based on Bloom's Taxonomy)
 */
export enum LearningGoalTaxonomy {
    REMEMBER = 'REMEMBER',
    UNDERSTAND = 'UNDERSTAND',
    APPLY = 'APPLY',
    ANALYZE = 'ANALYZE',
    EVALUATE = 'EVALUATE',
    CREATE = 'CREATE',
}

export class LearningGoal implements BaseEntity {
    public id?: number;
    public title?: string;
    public description?: string;
    public taxonomy?: LearningGoalTaxonomy;
    public masteryThreshold?: number;
    public course?: Course;
    public exercises?: Exercise[];
    public lectureUnits?: LectureUnit[];
    public userProgress?: LearningGoalProgress[];

    constructor() {}
}

export class LearningGoalProgress {
    public progress?: number;
    public confidence?: number;

    constructor() {}
}

export class LearningGoalRelation implements BaseEntity {
    public id?: number;
    public tailLearningGoal?: LearningGoal;
    public headLearningGoal?: LearningGoal;
    public type?: string;

    constructor() {}
}
