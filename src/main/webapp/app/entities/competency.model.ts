import { BaseEntity } from 'app/shared/model/base-entity';
import { Course } from 'app/entities/course.model';
import { Exercise } from 'app/entities/exercise.model';
import { LectureUnit } from 'app/entities/lecture-unit/lectureUnit.model';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { faBrain, faComments, faCubesStacked, faMagnifyingGlass, faPenFancy, faPlusMinus, faQuestion } from '@fortawesome/free-solid-svg-icons';

/**
 * The available competency types (based on Bloom's Taxonomy)
 */
export enum CompetencyTaxonomy {
    REMEMBER = 'REMEMBER',
    UNDERSTAND = 'UNDERSTAND',
    APPLY = 'APPLY',
    ANALYZE = 'ANALYZE',
    EVALUATE = 'EVALUATE',
    CREATE = 'CREATE',
}

export enum CompetencyRelationError {
    CIRCULAR = 'CIRCULAR',
    SELF = 'SELF',
    EXISTING = 'EXISTING',
    NONE = 'NONE',
}

export class Competency implements BaseEntity {
    public id?: number;
    public title?: string;
    public description?: string;
    public taxonomy?: CompetencyTaxonomy;
    public masteryThreshold?: number;
    public course?: Course;
    public exercises?: Exercise[];
    public lectureUnits?: LectureUnit[];
    public userProgress?: CompetencyProgress[];
    public courseProgress?: CourseCompetencyProgress;

    constructor() {}
}

export class CompetencyProgress {
    public progress?: number;
    public confidence?: number;

    constructor() {}
}

export class CourseCompetencyProgress {
    competencyId?: number;
    numberOfStudents?: number;
    numberOfMasteredStudents?: number;
    averageStudentScore?: number;

    constructor() {}
}

export class CompetencyRelation implements BaseEntity {
    public id?: number;
    public tailCompetency?: Competency;
    public headCompetency?: Competency;
    public type?: string;

    constructor() {}
}

export function getIcon(competencyTaxonomy?: CompetencyTaxonomy): IconProp {
    if (!competencyTaxonomy) {
        return faQuestion as IconProp;
    }

    const icons = {
        [CompetencyTaxonomy.REMEMBER]: faBrain,
        [CompetencyTaxonomy.UNDERSTAND]: faComments,
        [CompetencyTaxonomy.APPLY]: faPenFancy,
        [CompetencyTaxonomy.ANALYZE]: faMagnifyingGlass,
        [CompetencyTaxonomy.EVALUATE]: faPlusMinus,
        [CompetencyTaxonomy.CREATE]: faCubesStacked,
    };

    return icons[competencyTaxonomy] as IconProp;
}

export function getIconTooltip(competencyTaxonomy?: CompetencyTaxonomy): string {
    if (!competencyTaxonomy) {
        return '';
    }

    const tooltips = {
        [CompetencyTaxonomy.REMEMBER]: 'artemisApp.competency.taxonomies.remember',
        [CompetencyTaxonomy.UNDERSTAND]: 'artemisApp.competency.taxonomies.understand',
        [CompetencyTaxonomy.APPLY]: 'artemisApp.competency.taxonomies.apply',
        [CompetencyTaxonomy.ANALYZE]: 'artemisApp.competency.taxonomies.analyze',
        [CompetencyTaxonomy.EVALUATE]: 'artemisApp.competency.taxonomies.evaluate',
        [CompetencyTaxonomy.CREATE]: 'artemisApp.competency.taxonomies.create',
    };

    return tooltips[competencyTaxonomy];
}
