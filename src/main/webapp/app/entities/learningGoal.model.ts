import { BaseEntity } from 'app/shared/model/base-entity';
import { Course } from 'app/entities/course.model';
import { Exercise } from 'app/entities/exercise.model';
import { LectureUnit } from 'app/entities/lecture-unit/lectureUnit.model';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { faBrain, faComments, faCubesStacked, faMagnifyingGlass, faPenFancy, faPlusMinus, faQuestion } from '@fortawesome/free-solid-svg-icons';

/**
 * The available competency types (based on Bloom's Taxonomy)
 */
export enum LearningGoalTaxonomy {
    REMEMBER = 'REMEMBER',
    UNDERSTAND = 'UNDERSTAND',
    APPLY = 'APPLY',
    ANALYZE = 'ANALYZE',
    EVALUATE = 'EVALUATE',
    CREATE = 'CREATE',
}

export enum LearningGoalRelationError {
    CIRCULAR = 'CIRCULAR',
    SELF = 'SELF',
    EXISTING = 'EXISTING',
    NONE = 'NONE',
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
    public courseProgress?: CourseLearningGoalProgress;

    constructor() {}
}

export class LearningGoalProgress {
    public progress?: number;
    public confidence?: number;

    constructor() {}
}

export class CourseLearningGoalProgress {
    competencyId?: number;
    numberOfStudents?: number;
    numberOfMasteredStudents?: number;
    averageStudentScore?: number;

    constructor() {}
}

export class LearningGoalRelation implements BaseEntity {
    public id?: number;
    public tailCompetency?: LearningGoal;
    public headCompetency?: LearningGoal;
    public type?: string;

    constructor() {}
}

export function getIcon(learningGoalTaxonomy?: LearningGoalTaxonomy): IconProp {
    if (!learningGoalTaxonomy) {
        return faQuestion as IconProp;
    }

    const icons = {
        [LearningGoalTaxonomy.REMEMBER]: faBrain,
        [LearningGoalTaxonomy.UNDERSTAND]: faComments,
        [LearningGoalTaxonomy.APPLY]: faPenFancy,
        [LearningGoalTaxonomy.ANALYZE]: faMagnifyingGlass,
        [LearningGoalTaxonomy.EVALUATE]: faPlusMinus,
        [LearningGoalTaxonomy.CREATE]: faCubesStacked,
    };

    return icons[learningGoalTaxonomy] as IconProp;
}

export function getIconTooltip(learningGoalTaxonomy?: LearningGoalTaxonomy): string {
    if (!learningGoalTaxonomy) {
        return '';
    }

    const tooltips = {
        [LearningGoalTaxonomy.REMEMBER]: 'artemisApp.learningGoal.taxonomies.remember',
        [LearningGoalTaxonomy.UNDERSTAND]: 'artemisApp.learningGoal.taxonomies.understand',
        [LearningGoalTaxonomy.APPLY]: 'artemisApp.learningGoal.taxonomies.apply',
        [LearningGoalTaxonomy.ANALYZE]: 'artemisApp.learningGoal.taxonomies.analyze',
        [LearningGoalTaxonomy.EVALUATE]: 'artemisApp.learningGoal.taxonomies.evaluate',
        [LearningGoalTaxonomy.CREATE]: 'artemisApp.learningGoal.taxonomies.create',
    };

    return tooltips[learningGoalTaxonomy];
}
