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
    competencyProgress: number;
    competencyConfidence: number;

    static shouldPromptForJol(competency: Competency, progress: CompetencyProgress | undefined, courseCompetencies: Competency[]): boolean {
        const currentDate = dayjs();
        const softDueDateMinusOneDay = competency.softDueDate?.subtract(1, 'day');
        const competencyProgress = progress?.progress ?? 0;

        // Condition 1: Current Date >= Competency Soft Due Date - 1 Days && Competency Progress >= 20%
        if (softDueDateMinusOneDay && currentDate.isAfter(softDueDateMinusOneDay) && competencyProgress >= 20) {
            return true;
        }

        // Filter previous competencies (those with soft due date in the past)
        const previousCompetencies = courseCompetencies.filter((c) => c.softDueDate && c.softDueDate.isBefore(currentDate));
        if (previousCompetencies.length === 0) {
            if (softDueDateMinusOneDay) {
                return false;
            } else {
                return competencyProgress >= 20;
            }
        }

        // Calculate the average progress of all previous competencies
        const totalPreviousProgress = previousCompetencies.reduce((sum, c) => {
            const progress = c.userProgress?.first()?.progress ?? 0;
            return sum + progress;
        }, 0);
        const avgPreviousProgress = totalPreviousProgress / previousCompetencies.length;

        // Condition 2: Competency Progress >= 0.8 * Avg. Progress of all previous competencies
        return competencyProgress >= 0.8 * avgPreviousProgress;
    }
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
