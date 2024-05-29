import dayjs from 'dayjs/esm';
import { BaseEntity } from 'app/shared/model/base-entity';
import { Course } from 'app/entities/course.model';
import { Exercise } from 'app/entities/exercise.model';
import { LectureUnit } from 'app/entities/lecture-unit/lectureUnit.model';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { faBrain, faComments, faCubesStacked, faMagnifyingGlass, faPenFancy, faPlusMinus, faQuestion } from '@fortawesome/free-solid-svg-icons';
import { StandardizedCompetency } from 'app/entities/competency/standardized-competency.model';

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

export enum CompetencyRelationType {
    RELATES = 'RELATES',
    ASSUMES = 'ASSUMES',
    EXTENDS = 'EXTENDS',
    MATCHES = 'MATCHES',
}

export enum CompetencyRelationError {
    CIRCULAR = 'CIRCULAR',
    SELF = 'SELF',
    EXISTING = 'EXISTING',
}

export enum CompetencyValidators {
    TITLE_MAX = 255,
    DESCRIPTION_MAX = 10000,
}

export const DEFAULT_MASTERY_THRESHOLD = 50;

export class Competency implements BaseEntity {
    public id?: number;
    public title?: string;
    public description?: string;
    public softDueDate?: dayjs.Dayjs;
    public taxonomy?: CompetencyTaxonomy;
    public masteryThreshold?: number;
    public optional?: boolean;
    public course?: Course;
    public exercises?: Exercise[];
    public lectureUnits?: LectureUnit[];
    public userProgress?: CompetencyProgress[];
    public courseProgress?: CourseCompetencyProgress;
    public linkedStandardizedCompetency?: StandardizedCompetency;

    constructor() {}
}

export class CompetencyJol {
    competencyId: number;
    jolValue: number;
    judgementTime: string;
    competencyProgress?: number;
    competencyConfidence?: number;
}

export interface CompetencyImportResponseDTO extends BaseEntity {
    title?: string;
    description?: string;
    softDueDate?: dayjs.Dayjs;
    taxonomy?: CompetencyTaxonomy;
    masteryThreshold?: number;
    optional?: boolean;
    courseId?: number;
    linkedStandardizedCompetencyId?: number;
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
    public type?: CompetencyRelationType;

    constructor() {}
}

export class CompetencyRelationDTO implements BaseEntity {
    id?: number;
    tailCompetencyId?: number;
    headCompetencyId?: number;
    relationType?: CompetencyRelationType;

    constructor() {}
}

/**
 * Converts a CompetencyRelationDTO to a CompetencyRelation
 * @param competencyRelationDTO
 */
export function dtoToCompetencyRelation(competencyRelationDTO: CompetencyRelationDTO) {
    const relation: CompetencyRelation = {
        id: competencyRelationDTO.id,
        tailCompetency: { id: competencyRelationDTO.tailCompetencyId },
        headCompetency: { id: competencyRelationDTO.headCompetencyId },
        type: competencyRelationDTO.relationType,
    };
    return relation;
}

export class CompetencyWithTailRelationDTO {
    competency?: Competency;
    tailRelations?: CompetencyRelationDTO[];

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

export function getProgress(competencyProgress: CompetencyProgress) {
    return Math.round(competencyProgress?.progress ?? 0);
}

export function getConfidence(competencyProgress: CompetencyProgress, masteryThreshold: number): number {
    return Math.min(Math.round(((competencyProgress?.confidence ?? 0) / (masteryThreshold ?? 100)) * 100), 100);
}

export function getMastery(competencyProgress: CompetencyProgress, masteryThreshold: number): number {
    const weight = 2 / 3;
    return Math.round((1 - weight) * getProgress(competencyProgress) + weight * getConfidence(competencyProgress, masteryThreshold));
}
